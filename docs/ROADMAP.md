# Belay — Roadmap (post-v1 backlog)

Items intentionally kept out of v1 to protect launch scope. Nothing here blocks the initial Play Store release described in [PRD.md](PRD.md).

## Backlog

- **Photo-proof check-ins** — optional photo attached to a check-in, visible only to the witness. Named explicitly as a "remaining follow-on" in the original design.
- **Streak-recovery flow beyond grace days** — a designed moment for "you broke a streak, here's what happens now" distinct from the grace-day mechanic (which is already in v1 per [PRD §6.2](PRD.md#6-v1-scope-beyond-the-original-design-proposed)).
- **Deep-link pairing** — pair via a shareable link/QR in addition to the 4-character code, via Android App Links.
- **Multiple witnesses per challenge** — currently one witness per challenge by design; revisit only if user feedback asks for it (changes the data model and the "one person sees a miss" privacy principle, so treat as a deliberate product decision, not a default expansion).
- **Multiple concurrent challenges per challenger** — v1 is one active challenge at a time.
- **iOS** — no plan; would be a from-scratch build given the native Kotlin/Compose decision in [TECH_STACK.md](TECH_STACK.md).
- **Tone as a full user-facing setting** — Warm/Competitive/Dry copy variants exist in the design; confirm in v1 whether this ships as a setting or a fixed choice (see [PRD §10](PRD.md#10-open-questions)); if fixed for v1, making it a real setting is the natural follow-on.
- **Widgets / home-screen glanceability** — the progress ring is a natural Android widget candidate.
- **Full CD pipeline** to a Play Store release track (v1 ships via manual/scripted upload per [TECH_STACK.md §8](TECH_STACK.md#8-cicd)).
