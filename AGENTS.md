# 1x1 Android App

## Overview

This is a Kotlin Android app built with Jetpack Compose for children to practice arithmetic. The app uses a fixed light/gray visual theme, supports English, German (CH/DE/AT), Spanish, French, Italian, and Dutch, and offers:

- Addition (`+`)
- Subtraction (`-`)
- Multiplication (`×`)
- Division (`÷`)
- Mixed multiplication/division (`×÷`)
- Wildcard table selection (`?`) for multiplication, division, and mixed modes

## Main structure

- `app/src/main/java/com/danielhoop/timestables/MainActivity.kt` contains the Compose screens, dialogs, setup forms, parental settings UI, language application, hints, timer/stopwatch UI, and navigation.
- `PracticeViewModel.kt` owns screen state, configuration loading/saving, calculation progress, timer/stopwatch activity, scoring, retries, and validation.
- `PracticeEngine.kt` contains pure calculation generation, review-round generation, hint generation, and arithmetic models. Keep this file free of Android APIs so it remains unit-testable.
- `ConfigurationDatabase.kt` contains the SQLite schema, persistence methods, and migrations.
- `PracticeEngineTest.kt` contains JVM unit tests for generation, hints, mixed/wildcard sampling, addition, and subtraction.
- `app/src/main/res/values/strings.xml` is the default resource set. Keep every supported locale file in `values-*/strings.xml` in sync when adding user-facing strings; supported locales are declared in `res/xml/locales_config.xml`.

## Persistence

SQLite database name: `times_tables.db`.

The database stores practice configurations, parental number availability, hint and display-sign preferences, scores, timer/stopwatch settings and history, the parental PIN, language, erroneous-result repetition count, addition settings, and subtraction settings. The current schema version is defined in `ConfigurationDatabase.kt`; any schema change must include an incremental `onUpgrade` migration.

Parental settings are PIN-gated and allow changing the PIN. Number availability defaults to all numbers enabled. Unset addition/subtraction restrictions mean no restriction. The erroneous-result repetition setting is an integer from 0 through 5 and defaults to 3; load it into memory at startup before using it in review-round logic. A value of 0 removes the deliberate error-number preference; such numbers may still reappear by chance.

## Calculation behavior

- Multiplication/division/mixed table practice uses first-half and review-half rounds.
- Wrong answers open the correction flow and require the correct answer before proceeding.
- Wildcard mode samples up to 10 unique calculations from allowed base numbers and safely handles an empty or over-restricted pool. In its review half, prefer calculations not used in the first half; only calculations tied to errors may be reused.
- Addition and subtraction use 20 unique samples where enough combinations exist, with the same correction/retry behavior.
- Auto-enter is controlled per applicable setup configuration.
- Hints are generated in `PracticeEngine`. Multiplication and division hints can be disabled independently; the highest-digits hint is controlled separately per arithmetic mode.
- Multiplication and division display signs are educator-configurable and must remain valid for their respective operations.
- Timers and stopwatches count active practice time, persist their progress, and share the same result history.

## Development commands

Use the configured JDK at `C:\Program Files\Android\Android Studio 1\jbr` when needed:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio 1\jbr'
.\gradlew.bat :app:testDebugUnitTest --no-configuration-cache
.\gradlew.bat :app:compileDebugKotlin --no-configuration-cache
```

Run the unit tests after changes to calculation generation, persistence, validation, or UI state. Do not remove existing user changes in the working tree.
