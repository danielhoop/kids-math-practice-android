# Times Tables

A small, offline Android app for children to practice multiplication and exact
division. It is written in Kotlin with Jetpack Compose.

## Practice flow

1. Choose multiplication (`·`) or division (`:`).
2. Choose a table from 1 through 12 and the highest practice number. For
   multiplication, you can also choose whether the number positions may swap.
   “Without 1 and 10” is enabled by default. It excludes those second numbers
   and recycles other allowed numbers to keep both rounds the requested length.
   Recycled numbers are arranged so the same number never appears twice in a row.
   The optional “Auto enter” setting submits an answer as soon as the expected
   number of digits has been typed.
3. Complete two rounds. The first includes every number from 1 through the
   configured maximum once. The second repeats weak numbers three times where
   space permits and fills the rest with other numbers.
4. Earn up to three trophies based on the total number of mistakes.

The four setup options are saved in a local SQLite database when a round starts.
Multiplication and division keep separate settings, which are restored the next
time their table-selection screen opens. After the results screen, the app returns
directly to table selection.

Division questions always have an integer answer and keep the chosen table
number as the divisor. For example, choosing table 3 can produce `21 : 3`.

## Build

Open the project in Android Studio, or run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
