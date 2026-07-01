# Dose

Local-first Android app to track medications and supplements: daily reminders
and a full adherence history (taken, skipped, and doses that were due but
missed). No backend, no account.

Reference project (architecture inspiration): [unitto](https://github.com/sadellie/unitto).

## Architecture decisions

| Area | Decision | Why |
|------|----------|-----|
| Platform | 100% native Android, Kotlin + Jetpack Compose (no KMP) | No second target; KMP ceremony not worth it |
| Modules | Single `:app`, layered packages | Split later only if it hurts |
| DI | Hilt | Google-standard; `@HiltWorker` integration |
| Navigation | Navigation 3 (stable 1.0.0) | Current direction; clean deep links from notifications |
| minSdk / target | 31 / 36 | Guaranteed Material You; `java.time` native (no desugaring) |
| Toolchain | Stable (AGP 9.1.1, Gradle 9.3.1, compileSdk 37, Compose BOM 2026.06, Kotlin 2.2.x, Room 2.8) | Solid over bleeding-edge alphas |
| UI | Material 3 Expressive, dynamic color, edge-to-edge | Feel like a Pixel default app |
| Persistence | Room + DataStore Preferences | Google-standard local storage |
| Reminders | AlarmManager exact **+** WorkManager | Exact alarm fires the dose; worker re-nags/sweeps missed doses |
| Schedules | daily times · weekly · every N days · cyclic (on/off) · PRN | Full clinical coverage |
| Doses | Materialized occurrences (rolling window, immutable past) | Fast Today/History; editing never rewrites the past |
| Notifications | Rich actions (Taken / Snooze / Skip) + deep link | Handle a dose without opening the app |
| Localization | English base + `values-es` | Google-standard; Spanish on Spanish devices |

**v1 scope:** core (meds + schedules + reminders + history) + JSON backup/export
+ Glance home-screen widget.
**v2 (deferred):** inventory + refill reminders, multiple profiles.

## Package layout (`com.victorfalcon.dose`)

```
domain/        models (Medication, Schedule, DoseOccurrence) + OccurrenceGenerator
data/          Room (entities, DAOs, DB), DataStore, repositories        [TODO]
ui/            theme/ (done) + screens (Today, History, MedEditor, Settings) + nav  [TODO]
reminder/      AlarmScheduler, AlarmReceiver, BootReceiver, NotificationHelper,
               missed-dose WorkManager worker                            [TODO]
widget/        Glance app widget                                         [TODO]
```

The reminder engine keys alarms to `DoseOccurrence` rows: schedule an exact
alarm per upcoming occurrence, the receiver posts the rich notification, and a
periodic worker flips still-PENDING past occurrences to `MISSED` and re-nags.

`OccurrenceGenerator` is pure/deterministic and unit-tested — it's the risky
part (weekly / interval / cyclic date math), so keep it that way.

## Build

No Gradle wrapper jar is committed. Either open the project in Android Studio
(it provisions the wrapper), or run once:

```
gradle wrapper --gradle-version 9.3.1
./gradlew :app:testDebugUnitTest   # runs OccurrenceGeneratorTest
./gradlew :app:assembleDebug
```

Versions in `gradle/libs.versions.toml` are a known-good stable baseline; let
Android Studio's upgrade assistant bump patches if it flags them.

## Next step

Wire the data layer (Room + repository) and the Today screen, then the reminder
engine. See the `[TODO]` packages above.
