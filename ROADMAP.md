# Dose — Roadmap to v1

Phased plan from the current skeleton to the agreed v1. Order is dependency-driven;
each phase is shippable and leaves something verifiable. Decisions come from the
initial design (see README).

**v1 scope:** core (meds + schedules + reminders + history/adherence) + JSON
backup/export + Glance widget. **Out (v2):** inventory/refill, multiple profiles.

## Overview

| Phase | Theme | Status |
|------|-------|--------|
| 0 | Foundation: toolchain, domain model, nav skeleton | ✅ Done |
| 1 | Persistence layer (Room + DataStore + repositories) | ✅ Done |
| 2 | Create / edit a medication | ✅ Done |
| 3 | Today screen (mark taken/skip) | ✅ Done |
| 4 | Reminder engine (alarms + notifications + workers) | ▢ |
| 5 | History & adherence | ▢ |
| 6 | Medication management & occurrence upkeep | ▢ |
| 7 | Settings | ▢ |
| 8 | Backup / export (JSON) | ▢ |
| 9 | Glance home-screen widget | ▢ |
| 10 | Release polish | ▢ |

---

## Phase 0 — Foundation ✅

Toolchain green (AGP 9.1.1 / compileSdk 37 / Hilt / Nav3 / Room / Compose M3).
Domain models (`Medication`, `Schedule`, `DoseOccurrence`) + `OccurrenceGenerator`
(unit-tested). Bottom-nav skeleton with Today/History placeholders. Builds + tests pass.

## Phase 1 — Persistence layer ✅

**Goal:** everything can read/write from a local DB.
- Room entities for `Medication`, `Schedule`, `DoseOccurrence` (+ `@TypeConverter`s for
  `java.time`, enums, `List<LocalTime>`, `Set<DayOfWeek>`).
- DAOs (queries: today's occurrences, range for history, meds list).
- `DoseDatabase` + Hilt module providing DB/DAOs.
- `MedicationRepository` (interface in domain, Room-backed impl) exposing `Flow`s.
- `DataStore` for settings (theme, dynamic-color toggle, default snooze, first-run).
- `OccurrenceMaterializer`: uses `OccurrenceGenerator` to persist a rolling window
  (~60 days) of occurrences, idempotently (no duplicates on re-run).
- **Verify:** DAO test (in-memory Room) round-trips a med+schedule and materialized occurrences.

## Phase 2 — Create / edit a medication ✅

**Goal:** the FAB adds a real medication with any schedule type.
- `MedEditor` screen (route via Nav3): name, dosage, notes, start date, schedule-type
  picker (daily times / weekly / every N days / cyclic on-off / as-needed), and the
  per-type inputs (times list, weekday chips, interval, cycle lengths).
- FAB on Today → MedEditor; reuse the screen for edit.
- Save → persist `Medication` + `Schedule`, then materialize occurrences.
- Form validation (name required, ≥1 time for scheduled types, sane cycle/interval).
- **Verify:** create each schedule type; confirm rows + generated occurrences.

## Phase 3 — Today screen ✅

**Goal:** the daily core loop works end to end (without notifications yet).
- `TodayViewModel` (Hilt) exposing today's occurrences grouped by time + status.
- Mark **taken** / **skip** / **undo**; record actual `takenAt`.
- "Log now" for PRN meds (creates an ad-hoc taken record).
- Empty state ("no meds yet → add one").
- **Verify:** add med → appears on Today at its times → mark taken → status persists.

## Phase 4 — Reminder engine

**Goal:** the app reminds you on time and you can act from the notification. *(The heart.)*
- Notification channel(s) (high importance for reminders).
- `AlarmScheduler`: exact `setExactAndAllowWhileIdle` per upcoming occurrence
  (`USE_EXACT_ALARM`); schedule the next window.
- `AlarmReceiver`: post the rich notification — actions **Taken / Snooze / Skip** + tap
  deep-links to the dose.
- Action `BroadcastReceiver`: apply Taken/Skip (update occurrence, no UI), Snooze
  (reschedule alarm by the default snooze).
- `BootReceiver` (`RECEIVE_BOOT_COMPLETED`): reschedule pending alarms after reboot.
- WorkManager (`@HiltWorker`): periodic sweep — flip past `PENDING` → `MISSED`, re-nag
  doses still unhandled after a grace period.
- Runtime `POST_NOTIFICATIONS` request (API 33+) and exact-alarm permission handling.
- **Verify:** set a dose ~1 min out → notification fires → each action works → reboot reschedules.

## Phase 5 — History & adherence

**Goal:** the History tab answers "what did I take, skip, or miss?"
- History screen: day-by-day (calendar or list) with per-dose status.
- Adherence summary (e.g. % taken over 7/30 days).
- Per-medication history detail.
- **Verify:** past occurrences render with correct taken/skipped/missed.

## Phase 6 — Medication management & occurrence upkeep

**Goal:** editing never corrupts the past; the window keeps rolling.
- Edit a med/schedule → regenerate only **future** `PENDING` occurrences; past stays frozen.
- Archive (soft-delete `active=false`): stop future occurrences, keep history.
- Maintenance worker: keep materializing N days ahead and pruning as time passes.
- **Verify:** change a schedule → future updates, past untouched; archive hides from Today, keeps History.

## Phase 7 — Settings

**Goal:** the top-bar Settings destination.
- Theme (light/dark/system), dynamic-color toggle, default snooze duration.
- Shortcuts to system notification settings / exact-alarm permission.
- **Verify:** toggles persist (DataStore) and take effect.

## Phase 8 — Backup / export (JSON)

**Goal:** local-first safety net (no account = data-loss risk).
- Export whole DB → JSON file via SAF (document picker), `kotlinx.serialization`.
- Import from JSON (replace or merge), re-materialize occurrences.
- **Verify:** export → clear data → import → everything restored.

## Phase 9 — Glance home-screen widget

**Goal:** today's doses on the home screen.
- Glance widget listing today's remaining doses + quick "taken".
- Refresh on data change.
- **Verify:** add widget → shows today → tap marks taken → app reflects it.

## Phase 10 — Release polish

**Goal:** feels like a Pixel default app, ready to install.
- Adaptive + themed (Material You) app icon; splash screen (`core-splashscreen`).
- Predictive back; loading/empty/error states; accessibility (content descriptions, touch targets).
- Enable R8/minify + real ProGuard rules for release.
- Complete `values-es` translations; swap in `MaterialExpressiveTheme` if public by then.
- **Verify:** signed-ish release build; full walkthrough of every flow.

---

## v1 Definition of Done

- Add meds with all schedule types; reminders fire on time with working notification actions.
- Today shows the day's doses and lets you act; History shows accurate taken/skipped/missed + adherence.
- Reminders survive reboot; missed doses are detected.
- Settings, JSON backup/export, and a home-screen widget all work.
- Dynamic color, edge-to-edge, English + Spanish. Release build with R8 passes.
