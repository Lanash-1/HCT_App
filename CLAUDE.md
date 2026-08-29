# Belay — Project Guide

Android habit-accountability app. Challenger commits to daily habits; a single witness sees every check-in/miss and can cheer or nudge (1/day). See [docs/PRD.md](docs/PRD.md) for full product spec.

## Docs to read before touching code

- [docs/PRD.md](docs/PRD.md) — product requirements, screens, v1 scope
- [docs/TECH_STACK.md](docs/TECH_STACK.md) — architecture, backend, sync model, testing approach
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — Catalyst Data Store schema
- [docs/ROADMAP.md](docs/ROADMAP.md) — explicitly out-of-scope-for-v1 items; don't build these unless asked
- [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md) — draft policy; keep in sync with DATA_MODEL.md if the data model changes
- Design source of truth: [`app-logo-and-names/project/Habit Challenge.dc.html`](app-logo-and-names/project/Habit%20Challenge.dc.html) — pixel/interaction spec. Recreate the visual output in Compose; don't copy the prototype's HTML/JS structure.

## Stack summary

- Kotlin + Jetpack Compose, Material 3, minSdk 26
- MVVM + Repository, Hilt DI, Coroutines/Flow, Compose Navigation
- Single module (`:app`) — don't split into multi-module speculatively
- Backend: Zoho Catalyst (Auth, Data Store, Functions, Cache, Cron, FCM push), **separate dev and prod Catalyst projects**
- Auth: email + password via Catalyst Authentication
- Sync: FCM push + refresh-on-open, no polling loop
- Offline: Room mirrors relevant Catalyst data; check-ins queue via WorkManager
- Crash reporting: Firebase Crashlytics, wired in from the first app skeleton
- Pairing: 4-character code + shareable deep link (Android App Links)
- Cheer/nudge: witness-typed free-text messages, not preset tone copy
- Secrets: local gitignored config per environment (`google-services.json`, `secrets.properties`), with a checked-in `.example` template — never commit real credentials
- Git: simple feature branches off `development`, CI (lint + tests) is the merge gate, no formal review required while solo

Full detail and rationale in [docs/TECH_STACK.md](docs/TECH_STACK.md) — don't relitigate these decisions without a concrete reason; if one comes up, update the doc, don't just diverge in code.

## Workflow: TDD is mandatory, not optional

This project follows test-driven development for all business logic — client-side (ViewModels, Repositories, streak/grace-day math, pairing/role logic) and backend (Catalyst Functions).

1. Write a failing test first, for the specific behavior you're about to build.
2. Write the minimum code to make it pass.
3. Refactor with the test green.
4. Only then move to the next behavior.

Don't write implementation code speculatively "ahead of" a test. Streak and grace-day calculation especially should be driven by a test table of concrete scenarios (day N, grace remaining, miss/check-in → expected new state) written before the implementation.

## Conventions

- No speculative abstraction — build what the current screen/feature needs, not a generalized system for hypothetical future ones (matches the "single module for v1" call — same underlying reasoning).
- Derived/scored fields (streaks, perfect-day counts, grace remaining) are computed server-side in Catalyst Functions, never computed client-side and pushed up — keeps scoring single-sourced across devices. See [docs/DATA_MODEL.md](docs/DATA_MODEL.md#notes-for-implementation).
- Centralize user-facing copy rather than inlining strings in Composables — v1 is English-only, but this keeps later localization tractable (see [docs/PRD.md §7](docs/PRD.md#7-non-functional-requirements)).
- A miss/incomplete day is only ever shown to the challenger and their one witness. Never write code that aggregates or surfaces this more broadly (no feeds, no cross-witness visibility) — this is a stated product/privacy principle, not just a UI choice.

## Commands

No build yet — this repo currently contains design assets and docs only. Once the Android project is scaffolded, record the actual build/test/lint commands here (e.g. `./gradlew test`, `./gradlew lint`) so future sessions don't have to rediscover them.
