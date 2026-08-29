# Belay — Tech Stack & Architecture

**Status:** Decided (pre-development). Revisit only with explicit reason — see [Open questions](#open-questions).

## 1. Platform

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3 (matches the design doc's own label: "Android · Material 3")
- **Min SDK:** 26 (Android 8.0) — covers the large majority of active devices while keeping access to modern notification/scheduling APIs.
- **Target/compile SDK:** latest stable at build time.
- **Distribution:** Google Play Store, public listing.

## 2. App architecture

**Single module (`:app`) for v1.** No multi-module split at the start — a 7-screen v1 doesn't justify the ceremony. Split into feature/core modules later only when build times or contributor count actually make it worth doing; don't pre-modularize speculatively.

**MVVM + Repository**, standard modern Android shape:

```
UI (Compose screens)
  ↓ observes
ViewModel (per screen/feature, StateFlow-based UI state)
  ↓ calls
Repository (one per domain: Challenges, Habits, People/Pairing, Recap, Notifications)
  ↓ reads/writes
  ├── Local: Room (offline cache + queue)
  └── Remote: a per-domain wrapper interface (see "Provider-agnostic wrappers" below) backed by Firebase
```

- **Dependency injection:** Hilt.
- **Async:** Kotlin Coroutines + Flow throughout; no callbacks/RxJava.
- **Navigation:** Compose Navigation, single-Activity.

### Provider-agnostic wrappers

Every backend touchpoint (auth, Firestore, push, Cloud Functions calls) sits behind an interface that ViewModels and UI depend on — never a concrete Firebase type. E.g. `AuthRepository`/`FirebaseAuthRepositoryImpl`, a per-domain `*RemoteDataSource`/`Firestore*Impl`, `PushNotificationService`/`FcmPushNotificationService`. Nothing outside an `Impl` class or a Hilt `di/*Module.kt` imports `com.google.firebase.*` directly. This is a deliberate choice (not speculative — it's already paid off once, when auth had to move off Catalyst mid-build without touching a single ViewModel) so a future provider swap stays contained to the `Impl` classes.

## 3. Backend: Firebase

**Revised during implementation** — v1 originally targeted Zoho Catalyst (chosen for being inside the Zoho ecosystem). Catalyst's Android auth SDK turned out not to support a custom-styled login form (only a hosted Zoho page), and its signup object has no password field at all — pointing at an invite-and-confirm flow, not immediate self-service signup (see §10). Rather than keep reverse-engineering undocumented SDK internals, the backend moved to the Firebase suite entirely: one vendor, one console, and Firebase was already integrated for Crashlytics. See git history for the removed Catalyst integration if it's ever useful for reference.

### Environments

**Separate dev and production Firebase projects from the start.** The app build (via build variants/flavors — `dev` / `prod`) points at the matching Firebase project per environment (via each flavor's `google-services.json`), so active development never touches production data once it exists.

- **Dev Firebase project:** `belay-883c4`. Cloud Functions live in [`backend/`](../backend) (Node.js, Firebase Functions).
- **Prod Firebase project:** not yet created — create before first release build.

### Services in use

| Firebase service | Role in Belay |
|---|---|
| **Authentication** | Email + password sign-up/sign-in. See §10. |
| **Firestore** | Primary data: users, challenges, habits, check-ins, pairing/witness relationships, recap snapshots. See [DATA_MODEL.md](DATA_MODEL.md) — collections mirror the tables described there. |
| **Cloud Functions** | Server-side business logic: streak calculation, grace-day accounting, weekly recap generation (scheduled), cheer/nudge write (callable function, using `context.auth` for identity). Keeps scoring/streak logic off-device and single-sourced. |
| **Cloud Scheduler** (via scheduled Cloud Functions) | Daily day-rollover evaluation, weekly recap generation (Sunday). |
| **Cloud Messaging (FCM)** | User-facing push notifications — see §4, §6. |
| **Crashlytics** | Crash reporting — see §6a. |

### Real-time sync

Unlike the originally-considered Catalyst platform, Firestore has native realtime listeners (`addSnapshotListener`) — no need for a websocket workaround or push-triggered refetch to get live challenger⇄witness state while the app is open. See §4.

## 4. Sync model: challenger ⇄ witness live state

**Decision: Firestore realtime listeners while the app is open, FCM for notification-tray alerts when it isn't.** Revised from the original Catalyst-era "FCM push + refresh-on-open" plan now that the backend (Firestore) actually supports realtime listeners (§3).

Flow for a challenger check-in:
1. Challenger's app writes the check-in to Firestore (optimistic local update first — see §5).
2. A Firestore-triggered/scheduled Cloud Function recalculates streak/day-complete state and writes the derived fields back to Firestore.
3. The witness's app has an active `addSnapshotListener` on the challenge/habit documents it's watching — the UI updates live, no push round-trip needed, while the app is foregrounded (or briefly backgrounded, per Firestore's listener behavior).
4. If the witness's app isn't running, an FCM push still delivers a system notification (e.g. "Arun finished the day") — the listener picks up the actual state once the app opens.
5. Same pattern in reverse for Cheer/Nudge → the challenger sees it live via a listener, or via FCM if the app isn't open.

Notes:
- Every screen also does a plain refetch on resume (`onStart`) as a safety net, so even a dropped listener/missed push never leaves the UI stale for more than one foreground.
- One FCM channel per event type (check-in update, cheer, nudge, recap-ready) so the client can route each to the right in-app toast/banner shown in the design (e.g. the slide-up cheer card, the nudge toast).

## 5. Offline handling

- Room database mirrors the subset of Firestore data relevant to the signed-in user (their own challenge + the challenges they witness).
- Check-ins and cheer/nudge actions write to Room immediately (optimistic UI, matches the design's instant toggle animation) and enqueue a sync job (WorkManager) that pushes to Firestore when connectivity returns.
- Sync conflicts are unlikely in this data model (each habit's daily check-in is owned by exactly one challenger, each cheer/nudge by exactly one witness) — last-write-wins per field is sufficient; no CRDT/merge logic needed.

## 6. Notifications

- **FCM** for user-facing notifications (daily reminder, "your witness cheered you", "you were nudged", weekly recap ready) and as a fallback for sync events when the app isn't open (§4).
- Per-habit reminder times (PRD §6.1) scheduled via `AlarmManager`/`WorkManager` locally, not server-pushed — they're a local scheduling concern, not cross-device sync.
- Runtime notification permission requested contextually (PRD §6.5), not on cold start.

## 6a. Crash & error reporting

**Firebase Crashlytics.** A Firebase project is already required for FCM (§4, §6), so adding Crashlytics is a small addition rather than new infrastructure. Wired in from the first app skeleton alongside CI (§8), not bolted on right before launch — a crash in early TDD-built code is still worth catching in later manual/device testing.

## 7. Testing — Test-Driven Development

**Decision: TDD for all business logic**, on both client and backend. Red → green → refactor is the default workflow for this project, not an afterthought layered on once features "work." Practically:

- **Write the failing test first** for any unit of logic before writing the implementation — ViewModels, Repositories, streak/grace-day calculation, pairing/role logic, and Cloud Functions.
- **Streak and grace-day math gets the most rigorous TDD treatment.** It's pure logic with unambiguous right/wrong answers (e.g. "day 14, 1 grace day left, habit missed → streak becomes X, grace becomes Y") — write the test table first, then the implementation that satisfies it.
- **Compose UI tests** follow the same discipline for the core interaction loop (check off a habit → ring/streak update, cheer/nudge → toast) and the two most state-dependent screens (Today, Witness detail) — write the test asserting the expected UI state transition, then build the Composable/state to pass it.
- **Cloud Functions** are unit-tested (Jest) the same way, independent of the Android client, since their logic (streak calc, grace-day accounting, weekly recap generation) is shared across every device. Verified against the Firebase Local Emulator Suite before real deployment.
- No blanket coverage-percentage target is enforced — TDD's discipline (nothing gets written without a preceding failing test) is the quality gate, not a coverage number after the fact.

## 8. CI/CD

**CI is set up alongside the first app skeleton**, not deferred — this is the natural complement to TDD: a red→green cycle only has teeth if the same checks run automatically on every push.

- GitHub Actions (or equivalent) running lint + unit tests on every PR, from the first meaningful commit onward.
- Release builds signed and uploaded to Play Console via a manual or scripted step for v1 — full CD to a Play track is a nice-to-have, not a launch blocker.

## 9. Naming & identifiers

- **App name:** Belay
- **Package ID:** `com.codigitech.belay` — confirmed. Play Console app entry not yet created; create it under this package ID when ready to publish (package ID is effectively permanent once published, so don't change it casually after that point).

## 9a. App icon

**Text/monogram placeholder for v1 development.** No exported logo assets exist yet — the design bundle is an interactive HTML mockup, not production art. Build a basic Material adaptive icon using a "B" monogram in the brand green (`#1F3D2B`, per the design's palette) so development isn't blocked on branding work. Treat this as swappable: replace with real logo assets whenever they're ready, independent of app development — doesn't require an app code change beyond the icon resource itself.

## 9b. Secrets & environment config

**Local, gitignored config files with a checked-in template**, standard Android approach:
- `google-services.json` (per environment — dev/prod, per §Environments above) — gitignored, never committed.
- `local.properties` for local SDK path config — gitignored (standard Android, not project-specific).
- A checked-in `*.example` / `*.template` version of each (with placeholder values) documents the required keys for anyone (including a future you) setting up the project fresh.
- CI (§8) injects real dev-environment secrets via GitHub Actions secrets, not committed files.

## 9c. Git workflow

**Simple feature-branch workflow.** Branch off `development` (or `main`) per feature/fix, merge when ready. No formal PR-review gate required while solo — CI (lint + unit tests, §8) is the actual quality gate on each branch, consistent with the TDD approach: a branch shouldn't merge with red tests.

## 10. Auth

**Email + password via Firebase Authentication.** No phone/OTP or social sign-in providers to configure. `createUserWithEmailAndPassword`/`signInWithEmailAndPassword` are direct, immediate calls that support a fully custom-styled in-app form (unlike the originally-considered Zoho Catalyst, whose SDK only offered a hosted login page and an invite-based signup flow — see §3). The `users` collection's `user_id` (DATA_MODEL.md) is a Firebase UID. Wrapped behind `AuthRepository` per the provider-agnostic-wrapper principle in §2.

## 11. Pairing

**Code + deep link, both in v1.** The 4-character pairing code (e.g. `7K42`) from the design remains the primary UI. Additionally, a shareable deep link (Android App Links) lets a challenger send a direct link that opens Belay straight to the "enter/confirm pairing" state pre-filled with their code — reduces friction versus typing the code manually. Requires:
- An `assetlinks.json` hosted at the package's associated domain (`codigitech.com` or subdomain, once confirmed) for App Links verification.
- A pairing deep-link route (e.g. `https://belay.codigitech.com/pair/{code}` or a custom scheme fallback) handled by Compose Navigation's deep-link support.

## 12. Cheer / nudge messages

**User-typed, not preset copy.** The design's Warm/Competitive/Dry tone variants are dropped — instead, when a witness cheers or nudges, they type their own short message (with a sensible character limit, e.g. 140 chars). This replaces `challenges.tone` and the tone-keyed copy table referenced in the design's prototype logic. See [DATA_MODEL.md](DATA_MODEL.md) for the resulting `interactions.message` field, which is now witness-authored input rather than server-resolved copy.

A default placeholder/prompt (e.g. "Say something...") is shown in the compose field; consider a minimal quick-pick of a couple of short defaults ("Nice!", "Don't forget!") as optional shortcuts, not as a tone system — confirm with product owner if that's wanted or if free text only is preferred.

## Open questions

- **`firebase login` + Blaze plan needed before real Cloud Functions deployment.** Functions are written and tested against the Firebase Local Emulator Suite, but deploying them for real needs an authenticated `firebase-tools` session (interactive browser OAuth) and the `belay-883c4` project to be on the Blaze (pay-as-you-go) plan — Cloud Functions don't deploy on the free Spark plan.
- Create the prod Firebase project before first release build.
- Confirm final Play Console developer account name once the app entry is created there.
- Confirm the domain to use for App Links verification (§11) — needed before deep-link pairing can ship.
- Decide whether cheer/nudge gets optional quick-pick shortcuts alongside free text (§12), or free text only.
- Real logo/icon assets (§9a) — swap in whenever ready; not a blocker.

Everything else in this doc is settled. Remaining decisions (store listing content, launch date, etc.) are deliberately deferred to when they're actually needed, not resolved speculatively — see [PRD.md](PRD.md) and [ROADMAP.md](ROADMAP.md).
