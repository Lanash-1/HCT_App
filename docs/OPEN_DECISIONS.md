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

