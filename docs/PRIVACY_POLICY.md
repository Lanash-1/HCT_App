# Belay — Privacy Policy (draft)

**Status:** Draft for review — not legal advice. Have this checked before publishing/hosting it or submitting to Play Console. Update the bracketed placeholders before use.

**Last updated:** [date]

## Who this covers

This policy describes how Belay ("the app," "we," "our") handles information for anyone who creates an account, whether as a challenger or a witness.

## What we collect

| Data | Why | Where it lives |
|---|---|---|
| Email address and password (hashed) | Account creation and sign-in | Zoho Catalyst Authentication |
| Display name | Shown to your witness / the people you witness | Zoho Catalyst Data Store |
| Habit names, details, and daily check-in status | Core app function — this is what you and your witness track | Zoho Catalyst Data Store, cached locally on your device |
| Pairing code / pairing relationships | Connects a challenger with their witness | Zoho Catalyst Data Store |
| Cheer/nudge messages you send | Delivered to the person you're witnessing | Zoho Catalyst Data Store |
| Device push token (FCM) | Delivers notifications (sync updates, reminders, cheer/nudge) | Firebase Cloud Messaging, referenced from Catalyst |
| Crash and error diagnostics | Fixing bugs | Firebase Crashlytics |

We do not collect location data, contacts, photos (v1 has no photo-proof feature — see [ROADMAP.md](ROADMAP.md)), or browsing history.

## Who sees your data

- **Your habit check-ins and misses are visible only to you and your one witness.** They are never shown to any other user, never aggregated, and never made public. This is a core design principle of the app, not just a policy statement — see [PRD.md §4](PRD.md#4-product-principles-from-the-designs-stated-assumptions).
- Cheer/nudge messages you send are visible only to the person you sent them to.
- We do not sell, rent, or share your data with third parties for advertising or marketing purposes.

## Third-party services

Belay is built on:
- **Zoho Catalyst** — backend hosting, authentication, and data storage.
- **Firebase Cloud Messaging** — push notification delivery (used by Catalyst for this purpose).
- **Firebase Crashlytics** — crash and error diagnostics.

Each of these processes data on our behalf under their own respective privacy/data-processing terms; we don't share your Belay data with them for any purpose beyond providing the app's functionality.

## Data retention & deletion

- Your data is retained for as long as your account is active.
- You can delete your account and associated data in-app, from **Profile → [Settings → Delete account]** (matches the in-app deletion path required by Play policy — see [PRD.md §6.3](PRD.md#6-v1-scope-beyond-the-original-design-proposed)). This removes your account, your challenges/habits/check-ins, and your pairing relationships.
- If you are someone's witness, your identity is also removed from their challenge record when you delete your account.
- [ Specify retention period for backups/logs after deletion, e.g. "up to 30 days," once confirmed. ]

## Children's privacy

Belay is not directed at children under 13 [confirm/adjust age threshold per your target market and Play Console content rating]. We do not knowingly collect data from children under this age.

## Security

Data in transit is encrypted (HTTPS/TLS). Passwords are hashed by Zoho Catalyst Authentication, never stored or visible to us in plain text.

## Changes to this policy

We'll update the "Last updated" date above when this policy changes, and notify users in-app of material changes.

## Contact

[ Contact email/address to be added before publishing — required by Play Console. ]

---

### Notes for whoever finalizes this (not part of the published policy)

- This draft is scoped to what's actually in [DATA_MODEL.md](DATA_MODEL.md) and [PRD.md](PRD.md) — update it if the data model changes.
- Before submission: fill in the bracketed placeholders, host this (or a converted version) at a public URL, and use its content to answer the Play Console **Data Safety** questionnaire consistently — the two must not contradict each other.
- Get an actual legal review before publishing; this draft is a starting point, not a substitute for one.
