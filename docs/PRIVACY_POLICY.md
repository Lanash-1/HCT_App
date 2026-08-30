# Belay — Privacy Policy (draft)

**Status:** Draft for review — not legal advice. Have this checked before publishing/hosting it or submitting to Play Console. Update the bracketed placeholders before use.

**Last updated:** 2026-08-29

## Who this covers

This policy describes how Belay ("the app," "we," "our") handles information for anyone who creates an account, whether as a challenger or a witness.

## What we collect

| Data | Why | Where it lives |
|---|---|---|
| Email address and password (hashed) | Account creation and sign-in | Firebase Authentication |
| Display name | Shown to your witness / the people you witness | Firebase Firestore |
| Habit names, details, and daily check-in status | Core app function — this is what you and your witness track | Firebase Firestore, cached locally on your device |
| Pairing code / pairing relationships | Connects a challenger with their witness | Firebase Firestore |
| Cheer/nudge messages you send | Delivered to the person you're witnessing | Firebase Firestore |
| Device push token (FCM) | Delivers notifications (sync updates, reminders, cheer/nudge) | Firebase Firestore (`users/{id}/private/push`), sent to Firebase Cloud Messaging |
| The date you last opened the app | Lets a challenger see whether their witness is still looking in | Firebase Firestore |
| Crash and error diagnostics | Fixing bugs | Firebase Crashlytics |

We do not collect location data, contacts, photos (v1 has no photo-proof feature — see [ROADMAP.md](ROADMAP.md)), or browsing history.

## Who sees your data

- **Your habit check-ins and misses are visible only to you and your one witness.** They are never shown to any other user, never aggregated, and never made public. This is a core design principle of the app, not just a policy statement — see [PRD.md §4](PRD.md#4-product-principles-from-the-designs-stated-assumptions).
- Cheer/nudge messages you send are visible only to the person you sent them to.
- If you are someone's witness, they can see the date you last opened Belay — so they know whether anyone is actually watching. It is a date only, never a record of what you did in the app. It is stored on your profile alongside your display name, which is readable by other signed-in Belay users who already have your account identifier — in practice, the people you have paired with.
- Your push tokens are stored where only your own account can read them; nobody you are paired with can see what devices you use.
- We do not sell, rent, or share your data with third parties for advertising or marketing purposes.

## Third-party services

Belay is built on:
- **Firebase** (Authentication, Firestore, Cloud Functions) — sign-in, data storage, and server-side business logic.
- **Firebase Cloud Messaging** — push notification delivery.
- **Firebase Crashlytics** — crash and error diagnostics.

Each of these processes data on our behalf under their own respective privacy/data-processing terms; we don't share your Belay data with them for any purpose beyond providing the app's functionality.

## Data retention & deletion

- Your data is retained for as long as your account is active.
- You can delete your account and associated data in-app, from **Profile → Account → Delete account** (matches the in-app deletion path required by Play policy — see [PRD.md §6.3](PRD.md#6-v1-scope-beyond-the-original-design-proposed)). This removes your account, your challenges/habits/check-ins, and your pairing relationships.
- If you are someone's witness, your identity is also removed from their challenge record when you delete your account.
- Deletion from our database (Firestore) and your sign-in account (Firebase Authentication) happens immediately when you confirm the deletion — there's no separate backup or soft-delete window on our side. Crash/error diagnostics (Firebase Crashlytics) aren't linked to your deleted account, but persist for whatever retention period Firebase's own policies set for that service, outside our control.

## Children's privacy

Belay is not directed at children under 13. We do not knowingly collect data from children under this age. [ Confirm this threshold matches the Play Console content rating and any additional regional requirements (e.g. GDPR-K/COPPA) before publishing. ]

## Security

Data in transit is encrypted (HTTPS/TLS). Passwords are hashed by Firebase Authentication, never stored or visible to us in plain text.

## Changes to this policy

We'll update the "Last updated" date above when this policy changes, and notify users in-app of material changes.

## Contact

lanash.db@zohocorp.com [ Confirm this is the address you want public before publishing — Play Console requires a working contact for the listing; many developers use a dedicated support address rather than a personal one. ]

---

### Notes for whoever finalizes this (not part of the published policy)

- This draft is scoped to what's actually in [DATA_MODEL.md](DATA_MODEL.md) and [PRD.md](PRD.md) — update it if the data model changes.
- Before submission: fill in the bracketed placeholders, host this (or a converted version) at a public URL, and use its content to answer the Play Console **Data Safety** questionnaire consistently — the two must not contradict each other.
- Get an actual legal review before publishing; this draft is a starting point, not a substitute for one.
