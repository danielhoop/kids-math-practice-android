# Times Tables

A small, offline Android app for children to practice multiplication and exact
division. It is written in Kotlin with Jetpack Compose.

## Practice flow

1. Choose multiplication (`×`) or division (`÷`).
2. Choose a table from 1 through 12 and the highest practice number. For
   multiplication, you can also choose whether the number positions may swap.
   “Without 1 and 10” is enabled by default. It excludes those second numbers.
   The developer setting `forceSetLength` controls whether other allowed numbers
   are recycled to preserve the configured length. It currently defaults to false,
   so error-free halves contain each allowed number exactly once. A review half
   with errors still expands to the configured length for targeted repetition.
   The first question of the review half also differs from the final question of
   the initial half.
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

The last score for each operator and table number is stored in SQLite as well.
The table buttons show the same one-to-three trophy rating used on the results
screen.

An optional timer can be armed from the operator screen. Its saved duration
defaults to 30 minutes. It only consumes time after a digit is entered in an
answer field. A submitted numeric answer renews the maximum 30-second activity
window so timing continues into the next calculation. Individual digits do not
keep extending an already-active window. The developer-facing
`ACTIVE_INPUT_TIMEOUT_SECONDS` constant controls the idle timeout.
Completed timers are stored with their finish time and configured duration. The
timer setup dialog provides a newest-first history for parents, including the
localized abbreviated weekday and the correct/total calculation score for every
entry. History derives one, two, or three cups at 90%, 95%, or 98% accuracy.

Division questions always have an integer answer and keep the chosen table
number as the divisor. For example, choosing table 3 can produce `21 ÷ 3`.

## Build

Open the project in Android Studio, or run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
