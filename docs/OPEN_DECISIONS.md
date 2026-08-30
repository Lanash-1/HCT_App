# Open decisions — needs your input

Things I hit while implementing the PRD that need a human decision or an
external account/asset I can't create. Each says what I did in the meantime, so
nothing is blocked — but each needs your action before public release.

Status legend: **BLOCKS RELEASE** / **works, needs confirming** / **nice to have**

## 1. App Links domain for pairing invites — **BLOCKS RELEASE (for the link path)**

`PairingDeepLink.kt` and `AndroidManifest.xml` currently hardcode
`https://belay.codigitech.com/pair/{code}`. TECH_STACK.md §11 lists the domain as unconfirmed.

**What I did:** built the whole link path against that provisional host, plus a
`belay://pair/{code}` custom-scheme fallback so invites still work on devices where App Links
verification hasn't completed.

**What I need from you:**
- Confirm the domain (or name a different one).
- Host `.well-known/assetlinks.json` on it — template and steps in
  [docs/well-known/README.md](well-known/README.md). This needs the **release** signing
  certificate's SHA-256, which I can't generate.

Changing the domain later is a two-line edit (the constant in `PairingDeepLink.kt` and the
`android:host` in the manifest), but any invite link already sent out stops working.

## 2. Profile documents are readable by any signed-in user — **works, worth hardening**

`backend/firestore.rules` allows `read` on `/users/{userId}` for anyone signed in. That's there so
a witness can resolve a challenger's display name (and vice versa) without a server round-trip.

**What it means in practice:** a signed-in user who already knows another account's Firebase UID
could read that profile's `display_name`, `pair_code` and `last_seen_at`. UIDs aren't discoverable
in the app — you get one by pairing — so the practical exposure is the people you've paired with.
The privacy policy is worded to match this rather than overclaiming.

Habit data, check-ins, misses and cheer/nudge messages are *not* affected: those are already scoped
to challenge membership, and device push tokens live in an owner-only subdocument.

**What I'd suggest:** before launch, narrow it to "self, or a user you share a challenge with".
Firestore rules can't run queries, so this needs the paired user ids denormalised onto the profile
(e.g. a `visible_to` array maintained by the pairing Cloud Function) — a real change, not a rules
tweak, which is why I haven't made the call unilaterally.

