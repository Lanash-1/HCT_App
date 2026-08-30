# Belay — Play Console Data Safety disclosure (draft)

**Status:** Draft for review — not legal advice. This maps [PRIVACY_POLICY.md](PRIVACY_POLICY.md) and [DATA_MODEL.md](DATA_MODEL.md) onto Play Console's **Data Safety** questionnaire categories, so whoever fills in that form (a manual step in Play Console, not something a repo file can submit for you) has one accurate, consistent source to copy from. The two documents must not contradict each other — if either changes, update both.

## Does the app collect or share any of the following user data types?

| Play category | Collected? | Shared with third parties? | Which field(s) | Purpose (per Play's options) | Optional or required |
|---|---|---|---|---|---|
| Personal info → Email address | Yes | No | `users` account email (Firebase Auth) | Account management | Required |
| Personal info → Name | Yes | No | `users.display_name` | App functionality (shown to your witness / the people you witness) | Required |
| Messages → In-app messages | Yes | No | `interactions.message` (cheer/nudge text) | App functionality | Required (to use cheer/nudge) |
| App activity → Other user-generated content | Yes | No | `habits.name`/`detail`, `check_ins.done` | App functionality (this is the core habit-tracking data) | Required |
| App info and performance → Crash logs | Yes | No | Firebase Crashlytics reports | Analytics (crash diagnostics only) | Optional from the user's perspective, but not separately toggleable in v1 — see note below |
| App info and performance → Diagnostics | Yes | No | Firebase Crashlytics device/performance data | Analytics | Same as above |
| Device or other IDs | Yes | No | FCM push token (`users/{id}/private/push`) | App functionality (delivering notifications) | Required (to receive push notifications) |
| App activity → App interactions | Yes | No | `users.last_seen_at` (date of last app open) | App functionality (shows a challenger whether their witness is still checking in) | Required |

Every other Play category (location, financial info, health/fitness, photos/videos, contacts, web browsing history, search history, etc.) — **not collected.**

## Standard declarations

- **Is all of the user data collected by your app encrypted in transit?** Yes (HTTPS/TLS, via Firebase SDKs).
- **Do you provide a way for users to request that their data be deleted?** Yes — in-app, Profile → Account → Delete account (PRD §6.3). This is a real deletion path (a Cloud Function cascades the delete through Firestore and removes the Firebase Auth account), not just a deactivation.
- **Data collection is required for the app to function**, per the "Required" column above — there's no anonymous/guest mode in v1 (see [PRD.md §8](PRD.md#8-out-of-scope-for-v1)).

## Notes for whoever fills in the actual Play Console form

- Crashlytics' crash/diagnostic collection isn't currently behind a separate user-facing consent toggle in the app (it's part of the base Firebase wiring — see [TECH_STACK.md](TECH_STACK.md)). If Play Console's questionnaire treats "optional" as requiring an in-app toggle, mark this data type as collected without a separate opt-out, rather than "optional," unless a toggle is added before submission.
- The "in-app messages" category (cheer/nudge) is scoped to the one witness relationship, never a broader messaging feature (no DMs, no groups) — see [PRD.md §4](PRD.md#4-product-principles-from-the-designs-stated-assumptions) if the questionnaire's follow-up questions ask about scope.
- Get an actual legal/compliance review of both this document and [PRIVACY_POLICY.md](PRIVACY_POLICY.md) before submitting — this is a starting point, not a substitute for one.
