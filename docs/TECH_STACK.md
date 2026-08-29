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
  └── Remote: Catalyst SDK / REST client
```

- **Dependency injection:** Hilt.
- **Async:** Kotlin Coroutines + Flow throughout; no callbacks/RxJava.
- **Navigation:** Compose Navigation, single-Activity.

## 3. Backend: Zoho Catalyst

Chosen because you're already inside the Zoho ecosystem and Catalyst gives a managed full-stack platform without standing up your own servers.

### Environments

**Separate dev and production Catalyst projects from the start.** A Catalyst project has already been created (record its project ID/org below once at hand — placeholder until confirmed). A second project for production is created before first Play Store submission. The app build (via build variants/flavors — `dev` / `prod`) points at the matching Catalyst project + Firebase project per environment, so active development never touches production data once it exists.

- **Dev Catalyst project:** *(record project ID here once available)*
- **Prod Catalyst project:** not yet created — create before first release build.

### Services in use

| Catalyst service | Role in Belay |
|---|---|
| **Authentication** | User accounts — email/phone sign-in, session/token management. Avoids building auth from scratch. |
| **Data Store (NoSQL)** | Primary data: users, challenges, habits, check-ins, pairing/witness relationships, recap snapshots. See [DATA_MODEL.md](DATA_MODEL.md). |
| **Functions** | Server-side business logic: streak calculation, grace-day accounting, weekly recap generation (Sunday cron), cheer/nudge write + fan-out. Keeps scoring/streak logic off-device and single-sourced. |
| **Cache** | Short-lived state that doesn't need Data Store durability (e.g. "last seen" / presence-ish signals, idempotency keys for check-in retries). |
| **Cron** | Scheduled jobs: weekly recap generation, grace-day resets on challenge rollover, stale-pairing cleanup. |
| **Push Notifications (via FCM)** | Delivers the events that drive sync — see §4. |

### Why not a persistent-connection backend

Catalyst's platform is REST + serverless Functions + an event bus ("Signals") + FCM-based push — it does **not** offer a Firestore-style realtime listener or native websocket hosting. Standing up a custom websocket server was considered and rejected for v1: it would mean running and operating an always-on process *outside* Catalyst's managed/serverless model, which reintroduces exactly the ops burden Catalyst was chosen to avoid, for a two-person-per-challenge app where near-real-time (not truly instant) sync is acceptable. Revisit only if push-driven sync (§4) proves too slow in practice.

## 4. Sync model: challenger ⇄ witness live state

**Decision: FCM push + refresh-on-open**, no polling loop.

Flow for a challenger check-in:
1. Challenger's app writes the check-in to Catalyst Data Store (optimistic local update first — see §5).
2. A Catalyst Function (triggered on that write, or called directly by the client) recalculates streak/day-complete state and fires a **silent/data FCM push** to the witness's registered device(s).
3. Witness app receives the push (foreground: update state directly; background: refresh on next open, or show a system notification if the app defines one for this event — e.g. "Arun finished the day").
4. Same pattern in reverse for Cheer/Nudge → pushed to the challenger.

Notes:
- This is "near-instant in practice, not true realtime" — acceptable per §3.
- Every screen also does a plain refetch on resume (`onStart`), so a missed/delayed push never leaves the UI stale for more than one foreground.
- One push channel per event type (check-in update, cheer, nudge, recap-ready) so the client can route each to the right in-app toast/banner shown in the design (e.g. the slide-up cheer card, the nudge toast).

## 5. Offline handling

- Room database mirrors the subset of Catalyst Data Store relevant to the signed-in user (their own challenge + the challenges they witness).
- Check-ins and cheer/nudge actions write to Room immediately (optimistic UI, matches the design's instant toggle animation) and enqueue a sync job (WorkManager) that pushes to Catalyst when connectivity returns.
- Sync conflicts are unlikely in this data model (each habit's daily check-in is owned by exactly one challenger, each cheer/nudge by exactly one witness) — last-write-wins per field is sufficient; no CRDT/merge logic needed.

## 6. Notifications

- **FCM** for both data-sync push (§4) and user-facing notifications (daily reminder, "your witness cheered you", "you were nudged", weekly recap ready).
- Per-habit reminder times (PRD §6.1) scheduled via `AlarmManager`/`WorkManager` locally, not server-pushed — they're a local scheduling concern, not cross-device sync.
- Runtime notification permission requested contextually (PRD §6.5), not on cold start.

## 6a. Crash & error reporting

**Firebase Crashlytics.** A Firebase project is already required for FCM (§4, §6), so adding Crashlytics is a small addition rather than new infrastructure. Wired in from the first app skeleton alongside CI (§8), not bolted on right before launch — a crash in early TDD-built code is still worth catching in later manual/device testing.

## 7. Testing — Test-Driven Development

**Decision: TDD for all business logic**, on both client and backend. Red → green → refactor is the default workflow for this project, not an afterthought layered on once features "work." Practically:

- **Write the failing test first** for any unit of logic before writing the implementation — ViewModels, Repositories, streak/grace-day calculation, pairing/role logic, and Catalyst Functions.
- **Streak and grace-day math gets the most rigorous TDD treatment.** It's pure logic with unambiguous right/wrong answers (e.g. "day 14, 1 grace day left, habit missed → streak becomes X, grace becomes Y") — write the test table first, then the implementation that satisfies it.
- **Compose UI tests** follow the same discipline for the core interaction loop (check off a habit → ring/streak update, cheer/nudge → toast) and the two most state-dependent screens (Today, Witness detail) — write the test asserting the expected UI state transition, then build the Composable/state to pass it.
- **Catalyst Functions** are unit-tested the same way, independent of the Android client, since their logic (streak calc, grace-day accounting, weekly recap generation) is shared across every device.
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
- `local.properties` or a `secrets.properties` file for Catalyst API keys/project IDs per environment — gitignored.
- A checked-in `*.example` / `*.template` version of each (with placeholder values) documents the required keys for anyone (including a future you) setting up the project fresh.
- CI (§8) injects real dev-environment secrets via GitHub Actions secrets, not committed files.

## 9c. Git workflow

**Simple feature-branch workflow.** Branch off `development` (or `main`) per feature/fix, merge when ready. No formal PR-review gate required while solo — CI (lint + unit tests, §8) is the actual quality gate on each branch, consistent with the TDD approach: a branch shouldn't merge with red tests.

## 10. Auth

**Email + password** via Catalyst Authentication for v1. No phone/OTP or social sign-in providers to configure — keeps the onboarding flow to a standard email/password form ahead of the existing role-pick screen in the design.

## 11. Pairing

**Code + deep link, both in v1.** The 4-character pairing code (e.g. `7K42`) from the design remains the primary UI. Additionally, a shareable deep link (Android App Links) lets a challenger send a direct link that opens Belay straight to the "enter/confirm pairing" state pre-filled with their code — reduces friction versus typing the code manually. Requires:
- An `assetlinks.json` hosted at the package's associated domain (`codigitech.com` or subdomain, once confirmed) for App Links verification.
- A pairing deep-link route (e.g. `https://belay.codigitech.com/pair/{code}` or a custom scheme fallback) handled by Compose Navigation's deep-link support.

## 12. Cheer / nudge messages

**User-typed, not preset copy.** The design's Warm/Competitive/Dry tone variants are dropped — instead, when a witness cheers or nudges, they type their own short message (with a sensible character limit, e.g. 140 chars). This replaces `challenges.tone` and the tone-keyed copy table referenced in the design's prototype logic. See [DATA_MODEL.md](DATA_MODEL.md) for the resulting `interactions.message` field, which is now witness-authored input rather than server-resolved copy.

A default placeholder/prompt (e.g. "Say something...") is shown in the compose field; consider a minimal quick-pick of a couple of short defaults ("Nice!", "Don't forget!") as optional shortcuts, not as a tone system — confirm with product owner if that's wanted or if free text only is preferred.

## Open questions

- Record the dev Catalyst project ID/org (§Environments) once at hand; create the prod Catalyst project before first release build.
- Confirm final Play Console developer account name once the app entry is created there.
- Confirm the domain to use for App Links verification (§11) — needed before deep-link pairing can ship.
- Decide whether cheer/nudge gets optional quick-pick shortcuts alongside free text (§12), or free text only.
- Real logo/icon assets (§9a) — swap in whenever ready; not a blocker.

Everything else in this doc is settled. Remaining decisions (store listing content, launch date, etc.) are deliberately deferred to when they're actually needed, not resolved speculatively — see [PRD.md](PRD.md) and [ROADMAP.md](ROADMAP.md).
