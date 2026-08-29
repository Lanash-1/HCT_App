# Belay — Project Guide

Android habit-accountability app. Challenger commits to daily habits; a single witness sees every check-in/miss and can cheer or nudge (1/day). See [docs/PRD.md](docs/PRD.md) for full product spec.

## Docs to read before touching code

- [docs/PRD.md](docs/PRD.md) — product requirements, screens, v1 scope
- [docs/TECH_STACK.md](docs/TECH_STACK.md) — architecture, backend, sync model, testing approach
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — Firestore schema
- [docs/ROADMAP.md](docs/ROADMAP.md) — explicitly out-of-scope-for-v1 items; don't build these unless asked
- [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md) — draft policy; keep in sync with DATA_MODEL.md if the data model changes
- Design source of truth: [`app-logo-and-names/project/Habit Challenge.dc.html`](app-logo-and-names/project/Habit%20Challenge.dc.html) — pixel/interaction spec. Recreate the visual output in Compose; don't copy the prototype's HTML/JS structure.

## Stack summary

- Kotlin + Jetpack Compose, Material 3, minSdk 26
- MVVM + Repository, Hilt DI, Coroutines/Flow, Compose Navigation
- Single module (`:app`) — don't split into multi-module speculatively
- Backend: Firebase (Auth, Firestore, Cloud Functions, FCM push, Crashlytics), **separate dev and prod Firebase projects**. Every touchpoint sits behind a swappable wrapper interface (`AuthRepository`, per-domain `*RemoteDataSource`, `PushNotificationService`) — no `com.google.firebase.*` import outside an `Impl` class or `di/*Module.kt`
- Auth: email + password via Firebase Authentication
- Sync: Firestore realtime listeners while the app is open, FCM for notification-tray alerts when it isn't
- Offline: Room mirrors relevant Firestore data; check-ins queue via WorkManager
- Crash reporting: Firebase Crashlytics, wired in from the first app skeleton
- Pairing: 4-character code + shareable deep link (Android App Links)
- Cheer/nudge: witness-typed free-text messages, not preset tone copy
- Secrets: local gitignored config per environment (`google-services.json`), with a checked-in `.example` template — never commit real credentials
- Git: simple feature branches off `development`, CI (lint + tests) is the merge gate, no formal review required while solo

Full detail and rationale in [docs/TECH_STACK.md](docs/TECH_STACK.md) — don't relitigate these decisions without a concrete reason; if one comes up, update the doc, don't just diverge in code.

## Workflow: TDD is mandatory, not optional

This project follows test-driven development for all business logic — client-side (ViewModels, Repositories, streak/grace-day math, pairing/role logic) and backend (Cloud Functions).

1. Write a failing test first, for the specific behavior you're about to build.
2. Write the minimum code to make it pass.
3. Refactor with the test green.
4. Only then move to the next behavior.

Don't write implementation code speculatively "ahead of" a test. Streak and grace-day calculation especially should be driven by a test table of concrete scenarios (day N, grace remaining, miss/check-in → expected new state) written before the implementation.

## Conventions

- No speculative abstraction — build what the current screen/feature needs, not a generalized system for hypothetical future ones (matches the "single module for v1" call — same underlying reasoning).
- Derived/scored fields (streaks, perfect-day counts, grace remaining) are computed server-side in Cloud Functions, never computed client-side and pushed up — keeps scoring single-sourced across devices. See [docs/DATA_MODEL.md](docs/DATA_MODEL.md#notes-for-implementation).
- Centralize user-facing copy rather than inlining strings in Composables — v1 is English-only, but this keeps later localization tractable (see [docs/PRD.md §7](docs/PRD.md#7-non-functional-requirements)).
- A miss/incomplete day is only ever shown to the challenger and their one witness. Never write code that aggregates or surfaces this more broadly (no feeds, no cross-witness visibility) — this is a stated product/privacy principle, not just a UI choice.

## Commands

Two product flavors (`dev`/`prod`, see [docs/TECH_STACK.md §Environments](docs/TECH_STACK.md#environments)) × two build types (`debug`/`release`). Use the `dev` flavor for local work.

- Build: `./gradlew :app:assembleDevDebug`
- Unit tests: `./gradlew testDevDebugUnitTest`
- Lint: `./gradlew lintDevDebug`
- Instrumented tests (needs a connected device/emulator): `./gradlew connectedDevDebugAndroidTest`
- CI (`.github/workflows/android-ci.yml`) runs lint + unit tests on every push/PR to `main`/`development`.

Firebase (Auth, Firestore, Cloud Functions, Crashlytics, FCM) is wired into the build but only activates once a real `google-services.json` is dropped in per environment (`app/src/dev/`, `app/src/prod/` — see the `.example` templates and [docs/TECH_STACK.md §9b](docs/TECH_STACK.md#9b-secrets--environment-config)); until then the plugin is skipped so the build still works.

Backend Cloud Functions live in [`backend/`](backend) (Node.js, Jest — see [docs/TECH_STACK.md §3](docs/TECH_STACK.md#3-backend-firebase)):

- Unit tests: `npm test` (from `backend/`)
- Local emulator (Firestore + Functions, no login needed): `npm run emulators` (from `backend/`)
- Deploy (needs `firebase login` + Blaze plan — see TECH_STACK.md open questions): `npm run deploy` (from `backend/`)
- CI (`.github/workflows/backend-ci.yml`) runs `backend/functions`' Jest suite on any push/PR touching `backend/`.
