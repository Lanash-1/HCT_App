# Belay — Product Requirements Document

**Status:** Draft v1 — pre-development
**Owner:** lanash.db@zohocorp.com
**Last updated:** 2026-08-29

## 1. What this is

Belay is an Android habit-accountability app built on one idea: **the asymmetry is the product.**

A **challenger** commits to a short stack of daily habits (max 5). A **witness** — a friend who is *not* competing — sees every check-in and every miss, and is the only person who can nudge. There is no leaderboard, no feed, no public streaks. One person does the work; one person watches. Role is a mode on a profile, not a separate account — the same person can run their own challenge and witness someone else's at the same time.

Source design: [`app-logo-and-names/project/Habit Challenge.dc.html`](../app-logo-and-names/project/Habit%20Challenge.dc.html) (Claude Design handoff bundle). This PRD treats that file as the visual/interaction spec of record; where this document adds scope beyond it, it's called out explicitly under [§6](#6-v1-scope-beyond-the-original-design-proposed).

## 2. Problem & core bet

Solo habit trackers fail quietly — nobody notices when you stop. Public accountability apps (streaks, leaderboards) create performance anxiety and shame spirals when you miss. Belay's bet: **a single, chosen witness** gives real accountability (someone will notice) without the social cost of a public miss (only one person ever sees it, and their only tools are "cheer" or one nudge a day).

## 3. Roles

| | Challenger | Witness |
|---|---|---|
| Commits to habits | Yes, up to 5 | No |
| Has a streak / score | Yes | No — explicitly "nothing to lose" |
| Sees | Their own stack only | The challenger's day, live |
| Can act | Check habits off | Cheer (unlimited-ish) or Nudge (1/day) |
| Switching | A mode toggle in Profile, not a new account | Same |

A user can be a challenger on their own challenge and a witness on someone else's simultaneously. Profile → Mode switches what the current session/tab shows; it never deletes or hides the other role's data.

## 4. Product principles (from the design's stated assumptions)

1. A challenge holds several habits, capped at **5**, so the day stays winnable.
2. A day counts as complete only when **every** habit is checked. Partial days are visible, not hidden.
3. Streaks belong to each **habit**; perfect days belong to the **challenge**.
4. Role is a mode, not an account.
5. A miss is visible to **exactly one person** — the witness — never a feed.
6. Grace days are decided **at challenge creation**, not negotiated after a miss.

## 5. Screens (from the design — all in v1)

1. **Onboarding & role pick** — choose challenger or witness, or pair with a code (`7K42`-style).
2. **Create a challenge** — add up to 5 habits (name + detail), pick duration (7/21/30/66 days), pick a witness, set grace days.
3. **Today** — the daily stack: progress ring, per-habit check-off with streak, witness-live-status pill, cheer/nudge inline responses, perfect-days/grace-left/days-to-go summary.
4. **Weekly recap** — shareable card: check-ins this week, perfect days, per-habit 7-day grid, "witnessed by X" line, share/save actions. Auto-generated weekly (design says "sent... automatically every Sunday").
5. **Profile** — identity, mode switch (challenger/witness), appearance (light/dark), stats (habits, best streak, people watched), people list, settings (daily reminder time, "let X nudge me" toggle, grace days left).
6. **Watching (witness mode home)** — list of people you witness, each with live per-habit dot status and Cheer/Nudge buttons.
7. **Witness detail** — one challenger's day in full: status card (headline changes tone when complete vs. incomplete), per-habit live dots, challenge progress bar, cheer/nudge, activity log.

## 6. V1 scope beyond the original design (proposed)

The design's own "Next steps" note names dark theme as done and flags photo-proof, per-habit reminder times, and a streak-broken recovery moment as future follow-ons. Per your direction to fold in judgment-based improvements, this PRD pulls some of those into v1 and adds a few more driven by the fact that this ships to a public Play Store audience (which the original design, a personal-use prototype, didn't need to account for). Each is called out so you can cut any of them without touching the core spec.

| # | Addition | Why it's in v1, not backlog |
|---|---|---|
| 6.1 | **Per-habit reminder times** (not just one global daily reminder) | The design already shows per-habit times (`6:42 am`, `9:05 pm`) as if they're set — without this feature those numbers have nowhere to come from. |
| 6.2 | **Streak-broken / grace-exhausted recovery screen** | Today-screen and Profile both surface streaks and grace count prominently; hitting zero grace with a miss needs a designed moment, not a silent streak reset. |
| 6.3 | **Account deletion + data export, in-app** | Google Play policy requires an in-app path to delete account and associated data for any app with account creation. Non-negotiable for public release. |
| 6.4 | **Privacy policy + Play Data Safety disclosure** | Same reason — required for listing, and this app handles another person's behavioral data (the witness relationship), which needs explicit disclosure. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) and [PLAY_DATA_SAFETY.md](PLAY_DATA_SAFETY.md). |
| 6.5 | **Notification permission flow (Android 13+)** | Runtime `POST_NOTIFICATIONS` prompt, timed appropriately (after the user sees why — e.g. right after picking a witness), not fired blind on first launch. |
| 6.6 | **Offline-tolerant check-ins** | Habits get checked off in the morning; assume flaky connectivity. Check-ins queue locally (Room) and sync to Firestore when back online, rather than failing silently. |
| 6.7 | **Empty / edge states** — no witness yet, witness hasn't opened app in N days, all grace used, challenge ended | Design's interactive prototype only shows the "happy path" state per screen; production needs these explicitly designed, not improvised at build time. |
| 6.8 | **Basic crash/error reporting** | Needed pre-launch to catch issues in a public release; not user-facing but a build requirement. |
| 6.9 | *(Deferred, not v1)* Photo-proof check-ins | Explicitly named "remaining follow-on" in the design; kept out of v1 to avoid scope creep on the launch build. Tracked in [ROADMAP.md](ROADMAP.md). |
| 6.10 | **User-typed cheer/nudge messages** (replaces the design's preset Warm/Competitive/Dry tone copy) | Decided over preset tone variants — a witness types their own short message rather than picking a tone that resolves to canned copy. See [TECH_STACK.md §12](TECH_STACK.md#12-cheer--nudge-messages). |
| 6.11 | **Pairing via code + deep link** (design shows code-only) | Reduces first-pairing friction; code stays primary, deep link is additive. See [TECH_STACK.md §11](TECH_STACK.md#11-pairing). |

Sections 6.1–6.8 are considered **in scope for v1** under the "Full design + improvements" decision. 6.9 and other backlog ideas live in [ROADMAP.md](ROADMAP.md).

## 7. Non-functional requirements

- **Sync latency:** challenger check-in should reach the witness's device within a few seconds under normal connectivity (push-driven, not polling — see [TECH_STACK.md](TECH_STACK.md)).
- **Offline tolerance:** the Today screen must be checkable with no network; sync resumes on reconnect.
- **Privacy:** a miss/incomplete day is visible only to the challenger and their one witness — never aggregated, never shown to other witnesses, never public.
- **Accessibility:** Material 3 dynamic color/contrast support; all interactive elements meet minimum touch target size; screen-reader labels on icon-only controls (the ring, the check dots).
- **Localization:** English only for v1; copy is centralized (not hardcoded in Composables) to make later localization tractable.

## 8. Out of scope for v1

- iOS / any non-Android platform.
- Public/social features of any kind (feeds, leaderboards, discovery).
- More than one witness per challenge.
- More than 5 habits per challenge, or more than one active challenge per challenger at a time.
- In-app payments / premium tier.
- Photo-proof check-ins (see §6.9, backlog).

## 9. Success signals (informal, personal/small-audience launch)

Since this starts as a personal/friends project going public on Play Store rather than a funded product, "success" is qualitative for v1:
- A challenger and witness can complete a real 7+ day challenge together without the app breaking sync or losing data.
- Witnesses actually use cheer/nudge (i.e., the asymmetry is felt, not just displayed).
- No Play Store policy rejection on first submission.

## 10. Open questions

Everything needed to start development is decided. These remain open but are deliberately deferred to when they're actually needed (per your direction — decide at time of use, not speculatively):

- Package ID confirmed as `com.codigitech.belay` ([TECH_STACK.md §9](TECH_STACK.md#9-naming--identifiers)); Play Console app entry itself not yet created — do that when ready to publish.
- ~~Domain for Android App Links verification~~ — confirmed as `belay.codigitech.com` ([TECH_STACK.md §11](TECH_STACK.md#11-pairing)); hosting `assetlinks.json` there is still an open task, tracked in [OPEN_DECISIONS.md](OPEN_DECISIONS.md#1-app-links-domain-for-pairing-invites--blocks-release-for-the-link-path--domain-confirmed-hosting-still-open).
- ~~Whether cheer/nudge gets optional quick-pick message shortcuts~~ — decided: free text only ([TECH_STACK.md §12](TECH_STACK.md#12-cheer--nudge-messages)).
- Prod Firebase project creation — dev project exists; prod is created before first release build ([TECH_STACK.md §Environments](TECH_STACK.md#environments)).
- Real logo/icon assets — using a text-monogram placeholder for now ([TECH_STACK.md §9a](TECH_STACK.md#9a-app-icon)).
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) is drafted but needs the bracketed placeholders filled in, a legal review, and hosting before Play Store submission.
