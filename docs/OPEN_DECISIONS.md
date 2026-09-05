# Open decisions — needs your input

Things I hit while implementing the PRD that need a human decision or an
external account/asset I can't create. Each says what I did in the meantime, so
nothing is blocked — but each needs your action before public release.

Status legend: **BLOCKS RELEASE** / **works, needs confirming** / **nice to have**

## 1. App Links domain for pairing invites — **BLOCKS RELEASE (for the link path) — domain confirmed, hosting still open**

`PairingDeepLink.kt` and `AndroidManifest.xml` hardcode `https://belay.codigitech.com/pair/{code}`.
**Decided (2026-09-05): keep this domain.**

**What I did:** built the whole link path against that host, plus a `belay://pair/{code}`
custom-scheme fallback so invites still work on devices where App Links verification hasn't
completed.

**Still needed from you (this is the part I genuinely can't do):**
- Host `.well-known/assetlinks.json` on `belay.codigitech.com` — template and steps in
  [docs/well-known/README.md](well-known/README.md). This needs the **release** signing
  certificate's SHA-256, which only you can generate (it comes from your release keystore).

If the domain ever needs to change, it's a two-line edit (the constant in `PairingDeepLink.kt` and
the `android:host` in the manifest), but any invite link already sent out stops working.

## 2. Profile documents are readable by any signed-in user — **decided: leave as-is for v1**

`backend/firestore.rules` allows `read` on `/users/{userId}` for anyone signed in. That's there so
a witness can resolve a challenger's display name (and vice versa) without a server round-trip.

**What it means in practice:** a signed-in user who already knows another account's Firebase UID
could read that profile's `display_name`, `pair_code` and `last_seen_at`. UIDs aren't discoverable
in the app — you get one by pairing — so the practical exposure is the people you've paired with.
The privacy policy is worded to match this rather than overclaiming.

Habit data, check-ins, misses and cheer/nudge messages are *not* affected: those are already scoped
to challenge membership, and device push tokens live in an owner-only subdocument.

**Decided (2026-09-05):** leave as-is for v1, revisit before public launch rather than doing it now.
If it's picked back up, the fix is narrowing the rule to "self, or a user you share a challenge
with" — Firestore rules can't run queries, so that needs the paired user ids denormalised onto the
profile (e.g. a `visible_to` array maintained by the pairing Cloud Function), which is a real
backend change, not a rules tweak.

## 3. Cheer/nudge quick-pick shortcuts, or free text only? — **decided: free text only**

TECH_STACK.md §12 left this open: a witness types their own short message (≤140 chars). The
question was whether to also offer a couple of one-tap defaults ("Nice!", "Don't forget!").

**Decided (2026-09-05): free text only**, as originally implemented. No code change needed —
the tone-preset system the PRD explicitly dropped (§6.10) stays dropped.

## 4. Firebase projects, Blaze plan, and Cloud Functions deployment — **BLOCKS RELEASE**

None of the backend runs anywhere yet. Everything in `backend/functions` is written and unit-tested,
but it has never been deployed, because deploying needs things only you can do:

- `firebase login` (interactive browser OAuth).
- The **Blaze** (pay-as-you-go) plan — Cloud Functions don't deploy on Spark.
- A **prod** Firebase project (dev exists; prod is created before the first release build).
- A real `google-services.json` in `app/src/dev/` and `app/src/prod/` (templates are checked in as
  `.example`). Until one exists the Firebase Gradle plugins are skipped entirely — so the app
  builds, but Auth, Firestore, Crashlytics and push are all inert.

**Worth knowing:** the app now depends on functions being deployed for things a user would notice —
push notifications, the weekly recap, and account deletion (PRD §6.3, a Play policy requirement).
Deploy before you submit, not after.

New since the last docs pass, so remember to deploy these too: `onCheckInWritten` (a Firestore
trigger, not a scheduled function) and the updated `firestore.rules`.

## 5. Play Console entry, listing, and legal review — **BLOCKS RELEASE**

Not startable from here:

- Play Console app entry doesn't exist yet (package `com.codigitech.belay` is confirmed).
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) needs a **legal review** and a public URL before
  submission; the Data Safety form must be answered to match
  [PLAY_DATA_SAFETY.md](PLAY_DATA_SAFETY.md) exactly.
- Confirm the Play Console developer account name.
- Real logo/icon assets — a text-monogram placeholder is in place and is not a blocker, but it is
  what users would see today.

## 6. Room migrations before the first release — **BLOCKS RELEASE**

`DatabaseModule.kt` still uses `fallbackToDestructiveMigration`, which wipes local data on any
schema change. That's correct now (no shipped users) and I kept it while changing the schema this
round — `users.last_seen_at` and a nullable `challenges.witness_user_id` took the database to
version 4.

Once anything is in users' hands, this has to become real `Migration` objects, or an app update
silently deletes people's local check-in queue.

## 7. Things I decided on my own

Flagging these because they're judgment calls, not spec:

- **Witness inactivity threshold: 3 days.** `WITNESS_AWAY_THRESHOLD_DAYS` in
  `domain/challenge/ChallengeEdgeStates.kt`. Long enough not to nag over one quiet evening, short
  enough to matter on a 7-day challenge. One-line change.
- **A challenge that loses its witness adopts the challenger's next paired contact automatically**,
  rather than asking. With one witness per challenge there's nothing to choose between, and the
  alternative was a dead end (see PRD §6.7 work).
- **Check-in sync failures are not sent to Crashlytics**, unlike other swallowed remote failures —
  they queue in WorkManager and retry, so reporting them would mean a report every time someone
  checks a habit off on the train.
- **Push notification copy** ("Arun finished the day", "Meera cheered you on") is mine —
  `backend/functions/lib/buildPushMessage.js` and `data/notification/PushChannels.kt`. Worth a read
  since it's the app's voice in someone's notification tray.
