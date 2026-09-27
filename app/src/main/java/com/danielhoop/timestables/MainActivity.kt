package com.danielhoop.timestables

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DefaultGray = Color(0xFFD4D4D4)
private val MildGreen = Color(0xFFB8E6C0)
private val MildRed = Color(0xFFF2B8B5)
private val Purple = Color(0xFF6352C7)
private val ConfigurationLabelFontSize = 18.sp
private const val AUTO_ENTER_VISIBILITY_MILLIS = 80L

class MainActivity : AppCompatActivity() {
    private val practiceModel: PracticeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Purple,
                    background = DefaultGray,
                    surface = DefaultGray,
                ),
            ) {
                TimesTablesApp(practiceModel)
            }
        }
    }

    override fun onStop() {
        practiceModel.pauseTimerForBackground()
        super.onStop()
    }
}

@Composable
fun TimesTablesApp(model: PracticeViewModel = viewModel()) {
    LaunchedEffect(model.selectedLanguageTag) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(model.selectedLanguageTag),
        )
    }
    BackHandler(enabled = model.screen != AppScreen.OPERATOR) { model.goBack() }

    when (model.screen) {
        AppScreen.OPERATOR -> OperatorScreen(model)
        AppScreen.SETUP -> SetupScreen(model)
        AppScreen.PRACTICE -> PracticeScreen(model)
        AppScreen.RESULTS -> ResultsScreen(model.errorCount, model::returnToSetup)
        AppScreen.SETTINGS -> SettingsScreen(model)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OperatorScreen(model: PracticeViewModel) {
    val context = LocalContext.current
    var showTimerDialog by remember { mutableStateOf(false) }
    var showStopwatchDialog by remember { mutableStateOf(false) }
    var showReplaceClockConfirmation by remember { mutableStateOf(false) }
    var replacementIsStopwatch by remember { mutableStateOf(false) }
    var showTimerHistoryDialog by remember { mutableStateOf(false) }
    var showSettingsPinDialog by remember { mutableStateOf(false) }
    var pinText by remember(showSettingsPinDialog) { mutableStateOf("") }
    var repeatPinText by remember(showSettingsPinDialog) { mutableStateOf("") }
    var pinError by remember(showSettingsPinDialog) { mutableStateOf<String?>(null) }
    val pinFocusRequester = remember { FocusRequester() }
    var minutesText by remember(showTimerDialog, model.timerMinutes) {
        mutableStateOf(model.timerMinutes.toString())
    }
    var timerError by remember(showTimerDialog) { mutableStateOf<String?>(null) }

    GrayPage {
        Text(stringResource(R.string.operator_prompt), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(42.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OperatorButton("+") { model.chooseOperator(MathOperator.ADDITION) }
                OperatorButton("-") { model.chooseOperator(MathOperator.SUBTRACTION) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OperatorButton("×") { model.chooseOperator(MathOperator.MULTIPLY) }
                OperatorButton("÷") { model.chooseOperator(MathOperator.DIVIDE) }
            }
            Row {
                OperatorButton("×÷") { model.chooseOperator(MathOperator.MIXED) }
            }
        }
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showTimerDialog = true }) {
                Text(stringResource(R.string.timer_button), fontSize = 21.sp)
            }
            Button(onClick = {
                showStopwatchDialog = true
            }) {
                Text(stringResource(R.string.stopwatch_button), fontSize = 21.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showSettingsPinDialog = true },
            enabled = model.settingsPinLoaded,
        ) {
            Text(stringResource(R.string.settings_button), fontSize = 21.sp)
        }
    }

    if (showSettingsPinDialog) {
        val creatingPin = model.settingsPin == null
        AlertDialog(
            onDismissRequest = { showSettingsPinDialog = false },
            containerColor = DefaultGray,
            title = { Text(stringResource(if (creatingPin) R.string.create_settings_pin else R.string.settings_pin)) },
            text = {
                LaunchedEffect(Unit) {
                    delay(100)
                    pinFocusRequester.requestFocus()
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit)) {
                                pinText = value
                                if (!creatingPin && value.isNotEmpty() && value == model.settingsPin) {
                                    showSettingsPinDialog = false
                                    model.openSettings()
                                }
                            }
                            pinError = null
                        },
                        label = { Text(stringResource(if (creatingPin) R.string.pin else R.string.enter_pin)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.focusRequester(pinFocusRequester),
                        singleLine = true,
                    )
                    if (creatingPin) {
                        OutlinedTextField(
                            value = repeatPinText,
                            onValueChange = { value ->
                                if (value.all(Char::isDigit)) repeatPinText = value
                                pinError = null
                            },
                            label = { Text(stringResource(R.string.repeat_pin)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    }
                    pinError?.let { Text(it, color = Color(0xFF9B2226)) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when {
                        pinText.isEmpty() -> pinError = context.getString(R.string.enter_pin_error)
                        creatingPin && pinText != repeatPinText -> pinError = context.getString(R.string.pins_do_not_match)
                        !creatingPin && pinText != model.settingsPin -> pinError = context.getString(R.string.incorrect_pin)
                        else -> {
                            if (creatingPin) model.saveSettingsPin(pinText)
                            showSettingsPinDialog = false
                            model.openSettings()
                        }
                    }
                }) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = { TextButton(onClick = { showSettingsPinDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            containerColor = DefaultGray,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.practice_timer), fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            showTimerDialog = false
                            showTimerHistoryDialog = true
                            model.loadTimerHistory()
                        },
                    ) {
                        Text(stringResource(R.string.history))
                    }
                }
            },
            text = {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) minutesText = value
                        timerError = null
                    },
                    label = { Text(stringResource(R.string.minutes)) },
                    supportingText = { timerError?.let { Text(it) } },
                    isError = timerError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minutes = minutesText.toIntOrNull()
                        if (minutes == null || minutes !in 1..1440) {
                            timerError = context.getString(R.string.timer_minutes_error)
                        } else if (model.isPracticeClockActive) {
                            replacementIsStopwatch = false
                            showReplaceClockConfirmation = true
                        } else {
                            model.configureTimer(minutes)
                            showTimerDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.start_timer))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showStopwatchDialog) {
        AlertDialog(
            onDismissRequest = { showStopwatchDialog = false },
            containerColor = DefaultGray,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.stopwatch_button), fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            showStopwatchDialog = false
                            showTimerHistoryDialog = true
                            model.loadTimerHistory()
                        },
                    ) {
                        Text(stringResource(R.string.history))
                    }
                }
            },
            text = { Text(formatTimer(model.elapsedStopwatchSeconds)) },
            confirmButton = {
                Button(onClick = {
                    if (model.isPracticeClockActive) {
                        replacementIsStopwatch = true
                        showReplaceClockConfirmation = true
                    } else {
                        model.configureStopwatch()
                        showStopwatchDialog = false
                    }
                }) {
                    Text(stringResource(R.string.start_new_stopwatch))
                }
            },
            dismissButton = {
                if (model.stopwatchIsArmed) {
                    TextButton(onClick = {
                        model.stopStopwatch()
                        showStopwatchDialog = false
                    }) {
                        Text(stringResource(R.string.stop_stopwatch))
                    }
                } else {
                    TextButton(onClick = { showStopwatchDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }

    if (showReplaceClockConfirmation) {
        AlertDialog(
            onDismissRequest = { showReplaceClockConfirmation = false },
            containerColor = DefaultGray,
            title = { Text(stringResource(R.string.active_clock_title)) },
            text = { Text(stringResource(R.string.start_new_question)) },
            confirmButton = {
                Button(onClick = {
                    showReplaceClockConfirmation = false
                    showTimerDialog = false
                    showStopwatchDialog = false
                }) {
                    Text(stringResource(R.string.keep_current))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (replacementIsStopwatch) {
                        model.configureStopwatch()
                    } else {
                        val minutes = minutesText.toIntOrNull() ?: return@TextButton
                        model.configureTimer(minutes)
                    }
                    showReplaceClockConfirmation = false
                    showTimerDialog = false
                    showStopwatchDialog = false
                }) {
                    Text(stringResource(if (replacementIsStopwatch) R.string.start_new_stopwatch else R.string.start_new_timer))
                }
            },
        )
    }

    if (showTimerHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showTimerHistoryDialog = false },
            containerColor = DefaultGray,
            title = { Text(stringResource(R.string.timer_history), fontWeight = FontWeight.Bold) },
            text = {
                when {
                    model.isTimerHistoryLoading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    model.timerHistory.isEmpty() -> Text(stringResource(R.string.no_finished_timers))
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(model.timerHistory, key = { it.id }) { entry ->
                            val cups = historyCups(entry)
                            Text(
                                text = "${formatHistoryDate(entry.finishedAtMillis)}, " +
                                    "${formatHistoryDuration(entry)} " +
                                    "(${entry.correctCalculations}/${entry.numberOfCalculations}" +
                                    if (cups > 0) " ${"🏆".repeat(cups)})" else ")",
                                fontSize = 17.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(onClick = { showTimerHistoryDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            },
        )
    }
}

@Composable
private fun OperatorButton(symbol: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(96.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(symbol, fontSize = if (symbol == "×÷") 42.sp else 54.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupScreen(model: PracticeViewModel) {
    if (model.operator == MathOperator.ADDITION || model.operator == MathOperator.SUBTRACTION) {
        if (model.operator == MathOperator.ADDITION) AdditionSetupScreen(model) else SubtractionSetupScreen(model)
        return
    }
    Scaffold(
        containerColor = DefaultGray,
        topBar = { BackBar(model::goBack) },
    ) { padding ->
        if (model.isConfigurationLoading) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when (model.operator) {
                    MathOperator.ADDITION -> stringResource(R.string.addition)
                    MathOperator.SUBTRACTION -> stringResource(R.string.subtraction)
                    MathOperator.MULTIPLY -> stringResource(R.string.multiplication)
                    MathOperator.DIVIDE -> stringResource(R.string.division)
                    MathOperator.MIXED -> stringResource(R.string.mixed_setup_title)
                },
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                (1..12).forEach { number ->
                    Button(
                        onClick = { model.startPractice(number) },
                        enabled = model.numberIsAvailable(model.operator, number),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(7.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color(0xFF9E9E9E),
                            disabledContentColor = Color(0xFF616161),
                        ),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                number.toString(),
                                modifier = Modifier.align(Alignment.Center),
                                fontSize = 23.sp,
                            )
                            val trophyCount = trophyCountForErrors(
                                model.scoresByFirstNumber[number],
                            )
                            if (trophyCount > 0) {
                                Text(
                                    text = "🏆".repeat(trophyCount),
                                    modifier = Modifier.align(Alignment.TopEnd),
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = model::startWildcardPractice,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(7.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            "?",
                            modifier = Modifier.align(Alignment.Center),
                            fontSize = 23.sp,
                        )
                        val trophyCount = trophyCountForErrors(model.wildcardScore())
                        if (trophyCount > 0) {
                            Text(
                                text = "🏆".repeat(trophyCount),
                                modifier = Modifier.align(Alignment.TopEnd),
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = model.highestNumberText,
                onValueChange = model::updateHighestNumber,
                label = { Text(stringResource(R.string.highest_number)) },
                supportingText = { model.highestNumberError?.let { Text(it) } },
                isError = model.highestNumberError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.ordered_numbers),
                    fontSize = ConfigurationLabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = model.orderedNumbers,
                    onCheckedChange = model::updateOrderedNumbers,
                )
            }
            if (model.operator != MathOperator.DIVIDE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "3 × 6 ↔ 6 × 3",
                            fontSize = ConfigurationLabelFontSize,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(stringResource(R.string.swap_positions), fontSize = 14.sp)
                    }
                    Switch(
                        checked = model.randomFirstSecond,
                        onCheckedChange = model::updateRandomFirstSecond,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.without_one_and_ten),
                    fontSize = ConfigurationLabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = model.withoutOneAndTen,
                    onCheckedChange = model::updateWithoutOneAndTen,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.auto_enter),
                    fontSize = ConfigurationLabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = model.autoEnter,
                    onCheckedChange = model::updateAutoEnter,
                )
            }
        }
    }
}

@Composable
private fun AdditionSetupScreen(model: PracticeViewModel) {
    Scaffold(
        containerColor = DefaultGray,
        topBar = { BackBar(model::goBack) },
    ) { padding ->
        if (model.isConfigurationLoading) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.addition), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = model.highestInputText,
                onValueChange = model::updateHighestInput,
                label = { Text(stringResource(R.string.highest_input)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = model.highestResultText,
                onValueChange = model::updateHighestResult,
                label = { Text(stringResource(R.string.highest_result)) },
                supportingText = { model.highestNumberError?.let { Text(it) } },
                isError = model.highestNumberError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.auto_enter), fontSize = ConfigurationLabelFontSize, fontWeight = FontWeight.SemiBold)
                Switch(checked = model.autoEnter, onCheckedChange = model::updateAutoEnter)
            }
            Button(
                onClick = model::startAdditionPractice,
                modifier = Modifier.height(56.dp),
            ) {
                Text(stringResource(R.string.start), fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun SubtractionSetupScreen(model: PracticeViewModel) {
    Scaffold(
        containerColor = DefaultGray,
        topBar = { BackBar(model::goBack) },
    ) { padding ->
        if (model.isConfigurationLoading) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.subtraction), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = model.subtractionHighestInputText,
                onValueChange = model::updateSubtractionHighestInput,
                label = { Text(stringResource(R.string.highest_input)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = model.subtractionLowestResultText,
                onValueChange = model::updateSubtractionLowestResult,
                label = { Text(stringResource(R.string.lowest_result)) },
                supportingText = { model.highestNumberError?.let { Text(it) } },
                isError = model.highestNumberError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.auto_enter), fontSize = ConfigurationLabelFontSize, fontWeight = FontWeight.SemiBold)
                Switch(checked = model.autoEnter, onCheckedChange = model::updateAutoEnter)
            }
            Button(
                onClick = model::startSubtractionPractice,
                modifier = Modifier.height(56.dp),
            ) {
                Text(stringResource(R.string.start), fontSize = 19.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(model: PracticeViewModel) {
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val selectedLanguage = stringResource(
        when (model.selectedLanguageTag) {
            "de-CH" -> R.string.language_german_ch
            "de-DE" -> R.string.language_german_de
            "de-AT" -> R.string.language_german_at
            "es-ES" -> R.string.language_spanish
            "fr-FR" -> R.string.language_french
            "it-IT" -> R.string.language_italian
            "nl-NL" -> R.string.language_dutch
            else -> R.string.language_english_us
        },
    )
    Scaffold(
        containerColor = DefaultGray,
        topBar = { BackBar(model::goBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.educator_settings), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            ExposedDropdownMenuBox(
                expanded = languageMenuExpanded,
                onExpandedChange = { languageMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.language)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(languageMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false },
                ) {
                    listOf("en-US", "de-CH", "de-DE", "de-AT", "es-ES", "fr-FR", "it-IT", "nl-NL").forEach { languageTag ->
                        val label = stringResource(
                            when (languageTag) {
                                "de-CH" -> R.string.language_german_ch
                                "de-DE" -> R.string.language_german_de
                                "de-AT" -> R.string.language_german_at
                                "es-ES" -> R.string.language_spanish
                                "fr-FR" -> R.string.language_french
                                "it-IT" -> R.string.language_italian
                                "nl-NL" -> R.string.language_dutch
                                else -> R.string.language_english_us
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                languageMenuExpanded = false
                                model.updateLanguage(languageTag)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.addition), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = model.minimumResultText,
                onValueChange = model::updateMinimumResult,
                label = { Text(stringResource(R.string.minimum_result)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsHintsSwitch(
                label = R.string.highest_digits_hint_allowed,
                enabled = model.additionHighestDigitsHintAllowed,
                onEnabledChange = { model.updateHighestDigitsHintAllowed(MathOperator.ADDITION, it) },
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.subtraction), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = model.subtractionMinimumInputText,
                onValueChange = model::updateSubtractionMinimumInput,
                label = { Text(stringResource(R.string.minimum_input)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = model.subtractionMaximumResultText,
                onValueChange = model::updateSubtractionMaximumResult,
                label = { Text(stringResource(R.string.maximum_result)) },
                supportingText = { model.subtractionSettingsError?.let { Text(it) } },
                isError = model.subtractionSettingsError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsHintsSwitch(
                label = R.string.highest_digits_hint_allowed,
                enabled = model.subtractionHighestDigitsHintAllowed,
                onEnabledChange = { model.updateHighestDigitsHintAllowed(MathOperator.SUBTRACTION, it) },
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.multiplication), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            SettingsHintsSwitch(
                enabled = model.multiplicationShowHints,
                onEnabledChange = { model.updateShowHints(MathOperator.MULTIPLY, it) },
            )
            (1..12).forEach { number ->
                SettingsNumberRow(
                    number = number,
                    enabled = number in model.multiplicationNumbers,
                    onEnabledChange = { model.updateNumberAvailability(MathOperator.MULTIPLY, number, it) },
                )
            }
            SettingsHintsSwitch(
                label = R.string.highest_digits_hint_allowed,
                enabled = model.multiplicationHighestDigitsHintAllowed,
                onEnabledChange = { model.updateHighestDigitsHintAllowed(MathOperator.MULTIPLY, it) },
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.division), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            SettingsHintsSwitch(
                enabled = model.divisionShowHints,
                onEnabledChange = { model.updateShowHints(MathOperator.DIVIDE, it) },
            )
            (1..12).forEach { number ->
                SettingsNumberRow(
                    number = number,
                    enabled = number in model.divisionNumbers,
                    onEnabledChange = { model.updateNumberAvailability(MathOperator.DIVIDE, number, it) },
                )
            }
            SettingsHintsSwitch(
                label = R.string.highest_digits_hint_allowed,
                enabled = model.divisionHighestDigitsHintAllowed,
                onEnabledChange = { model.updateHighestDigitsHintAllowed(MathOperator.DIVIDE, it) },
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.mixed_multiplication_division), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.mixed_hints_information),
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                fontSize = 16.sp,
            )
            (1..12).forEach { number ->
                SettingsNumberRow(
                    number = number,
                    enabled = number in model.mixedNumbers,
                    onEnabledChange = { model.updateNumberAvailability(MathOperator.MIXED, number, it) },
                )
            }
        }
    }
}

@Composable
private fun SettingsHintsSwitch(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    label: Int = R.string.show_hints,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label), fontSize = 19.sp)
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun SettingsNumberRow(number: Int, enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(number.toString(), fontSize = 19.sp)
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun PracticeScreen(model: PracticeViewModel) {
    val calculation = model.currentCalculation ?: return
    val hintLines = if (model.showHintsFor(calculation)) calculation.hintLines() else null
    val highestDigitsHint = model.highestDigitsHintFor(calculation)
    var showHintDialog by remember(model.calculationNumber) { mutableStateOf(false) }
    var showHighestDigitsHint by remember(model.calculationNumber) { mutableStateOf(false) }
    var showGreen by remember { mutableStateOf(false) }
    val isWrong = model.wrongDialogCalculation != null
    val isBlockingDialog = isWrong || model.retryCalculation != null ||
        model.showLeaveConfirmation || model.showTimeUpDialog
    val targetColor = when {
        isWrong -> MildRed
        showGreen -> MildGreen
        else -> DefaultGray
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 400),
        label = "answer background",
    )

    LaunchedEffect(model.correctFlashSequence) {
        if (model.correctFlashSequence > 0) {
            showGreen = true
            delay(600)
            showGreen = false
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = { BackBar(model::goBack, backgroundColor) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${model.calculationNumber} / ${model.totalCalculationCount}",
                    fontSize = 16.sp,
                    color = Color.DarkGray,
                )
                if (model.timerIsArmed || model.showTimeUpDialog) {
                    Text(
                        "⏱ ${formatTimer(model.remainingTimerSeconds)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray,
                    )
                } else if (model.stopwatchIsArmed) {
                    Text(
                        "⏲ ${formatTimer(model.elapsedStopwatchSeconds)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray,
                    )
                }
                Row {
                    if (highestDigitsHint.isNotEmpty() && !showHighestDigitsHint && !isBlockingDialog) {
                        TextButton(
                            onClick = { showHighestDigitsHint = true },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Text("%", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (hintLines != null && !isBlockingDialog) {
                        TextButton(
                            onClick = { showHintDialog = true },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Text("?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(0.65f))
            Text(
                text = calculation.expression,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 56.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))
            AnswerInput(
                questionNumber = model.calculationNumber,
                expectedAnswer = calculation.expectedAnswer,
                initialAnswer = if (showHighestDigitsHint) highestDigitsHint else "",
                autoEnter = model.autoEnter,
                enabled = !isBlockingDialog,
                onDigitEntered = model::onAnswerDigitEntered,
                onSubmit = model::submitAnswer,
            )
            Spacer(Modifier.weight(1.35f))
        }
    }

    if (showHintDialog && !isBlockingDialog) {
        AlertDialog(
            onDismissRequest = { showHintDialog = false },
            containerColor = DefaultGray,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    hintLines?.forEach { line ->
                        if (line.isBlank()) {
                            Spacer(Modifier.height(14.dp))
                        } else {
                            Text(
                                text = line,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 19.sp,
                                softWrap = false,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Button(onClick = { showHintDialog = false }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            },
        )
    } else if (model.showTimeUpDialog) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = DefaultGray,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("⏱", fontSize = 54.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Text("🥳", fontSize = 72.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(onClick = model::acknowledgeTimeUp) {
                        Text(stringResource(R.string.yay), fontSize = 20.sp)
                    }
                }
            },
        )
    } else if (model.showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = model::continuePractice,
            containerColor = DefaultGray,
            title = { Text(stringResource(R.string.leave_set), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.leave_progress_warning)) },
            confirmButton = {
                Button(onClick = model::continuePractice) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.continue_label))
                        CalculatorIcon()
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = model::confirmLeavePractice) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.leave))
                        DoorIcon()
                    }
                }
            },
        )
    } else if (model.wrongDialogCalculation != null) {
        val wrongCalculation = model.wrongDialogCalculation ?: return
        AlertDialog(
            onDismissRequest = {},
            containerColor = DefaultGray,
            text = {
                Text(
                    stringResource(R.string.correct_answer_format, wrongCalculation.expression, wrongCalculation.expectedAnswer),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 40.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(onClick = model::acceptCorrectAnswer) {
                        Text(stringResource(R.string.ok_smile), fontSize = 18.sp)
                    }
                }
            },
        )
    } else if (model.retryCalculation != null) {
        val retryCalculation = model.retryCalculation ?: return
        AlertDialog(
            onDismissRequest = {},
            containerColor = DefaultGray,
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        retryCalculation.expression,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 40.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    AnswerInput(
                        questionNumber = model.retryPromptSequence,
                        expectedAnswer = retryCalculation.expectedAnswer,
                        initialAnswer = "",
                        autoEnter = model.autoEnter,
                        enabled = true,
                        onDigitEntered = model::onAnswerDigitEntered,
                        onSubmit = model::submitRetryAnswer,
                    )
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun AnswerInput(
    questionNumber: Int,
    expectedAnswer: Int,
    initialAnswer: String,
    autoEnter: Boolean,
    enabled: Boolean,
    onDigitEntered: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var answer by remember(questionNumber, initialAnswer) {
        mutableStateOf(TextFieldValue(initialAnswer, selection = TextRange(initialAnswer.length)))
    }
    var answerWasSubmitted by remember(questionNumber) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submitCurrentAnswer() {
        if (answer.text.isNotEmpty() && !answerWasSubmitted) {
            answerWasSubmitted = true
            onSubmit(answer.text)
        }
    }

    LaunchedEffect(questionNumber, initialAnswer) {
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Let Compose render the final digit once before auto-enter moves to the
    // next question. Without this small delay, one-digit correct answers such
    // as 9 can appear never to have been entered at all.
    LaunchedEffect(autoEnter, answer, expectedAnswer) {
        if (autoEnter &&
            !answerWasSubmitted &&
            answer.text.length == expectedAnswer.toString().length
        ) {
            delay(AUTO_ENTER_VISIBILITY_MILLIS)
            submitCurrentAnswer()
        }
    }

    OutlinedTextField(
        value = answer,
        onValueChange = { value ->
            if (value.text.all(Char::isDigit)) {
                val digitWasAdded = value.text.length > answer.text.length
                answer = value
                if (digitWasAdded) onDigitEntered()
            }
        },
        enabled = enabled,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submitCurrentAnswer() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .focusRequester(focusRequester),
    )
}

@Composable
private fun ResultsScreen(errorCount: Int, onContinue: () -> Unit) {
    val trophyCount = trophyCountForErrors(errorCount)
    GrayPage {
        Text(stringResource(R.string.great_work), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        Text(
            if (trophyCount > 0) List(trophyCount) { "🏆" }.joinToString(" ") else "😊",
            fontSize = 62.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            if (errorCount == 0) {
                stringResource(R.string.perfect_round)
            } else {
                stringResource(
                    R.string.mistakes_format,
                    errorCount,
                    stringResource(if (errorCount == 1) R.string.mistake else R.string.mistakes),
                )
            },
            fontSize = 21.sp,
        )
        Spacer(Modifier.height(42.dp))
        Button(onClick = onContinue, modifier = Modifier.height(56.dp)) {
            Text(stringResource(R.string.choose_another_table), fontSize = 19.sp)
        }
    }
}

private fun trophyCountForErrors(errorCount: Int?): Int = when (errorCount) {
    0 -> 3
    1 -> 2
    2 -> 1
    else -> 0
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatHistoryDate(timestampMillis: Long): String =
    SimpleDateFormat("EEE", Locale.getDefault())
        .format(Date(timestampMillis))
        .trimEnd('.') + "., " +
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMillis))

private fun formatHistoryDuration(entry: TimerHistoryEntry): String {
    val elapsedMillis = if (entry.elapsedMillis > 0L) {
        entry.elapsedMillis
    } else {
        entry.durationMinutes * 60_000L
    }
    return formatTimer(((elapsedMillis + 999L) / 1000L).toInt())
}

private fun historyCups(entry: TimerHistoryEntry): Int {
    if (entry.numberOfCalculations == 0) return 0
    val percentage = entry.correctCalculations * 100.0 / entry.numberOfCalculations
    return when {
        percentage >= 98.0 -> 3
        percentage >= 95.0 -> 2
        percentage >= 90.0 -> 1
        else -> 0
    }
}

@Composable
private fun CalculatorIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        drawRoundRect(
            color = color,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(3.dp.toPx(), 6.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(size.width - 3.dp.toPx(), 6.dp.toPx()),
            strokeWidth = 1.8.dp.toPx(),
        )
        listOf(5f to 10f, 10f to 10f, 5f to 14f, 10f to 14f).forEach { (x, y) ->
            drawCircle(color = color, radius = 1.2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x.dp.toPx(), y.dp.toPx()))
        }
    }
}

@Composable
private fun DoorIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        val left = 2.dp.toPx()
        val top = 2.dp.toPx()
        val bottom = size.height - 2.dp.toPx()
        val frameRight = 10.dp.toPx()

        // Flat door frame, like a conventional exit sign.
        drawLine(color, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, top), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(frameRight, top), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(frameRight, top), androidx.compose.ui.geometry.Offset(frameRight, bottom), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(frameRight, bottom), stroke.width)

        // Small escape-style walking figure moving from left to right.
        drawCircle(
            color = color,
            radius = 1.35.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 5.5.dp.toPx()),
        )
        drawLine(color, androidx.compose.ui.geometry.Offset(5.dp.toPx(), 7.dp.toPx()), androidx.compose.ui.geometry.Offset(6.dp.toPx(), 10.5.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(5.5.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.Offset(3.5.dp.toPx(), 10.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(5.5.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.Offset(8.dp.toPx(), 9.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(6.dp.toPx(), 10.5.dp.toPx()), androidx.compose.ui.geometry.Offset(4.dp.toPx(), 14.5.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(6.dp.toPx(), 10.5.dp.toPx()), androidx.compose.ui.geometry.Offset(8.dp.toPx(), 14.dp.toPx()), stroke.width)

        // Direction arrow to the right of the door.
        val arrowY = 10.dp.toPx()
        drawLine(color, androidx.compose.ui.geometry.Offset(11.dp.toPx(), arrowY), androidx.compose.ui.geometry.Offset(17.dp.toPx(), arrowY), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(17.dp.toPx(), arrowY), androidx.compose.ui.geometry.Offset(14.5.dp.toPx(), 7.5.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(17.dp.toPx(), arrowY), androidx.compose.ui.geometry.Offset(14.5.dp.toPx(), 12.5.dp.toPx()), stroke.width)
    }
}

@Composable
private fun GrayPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DefaultGray)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun BackBar(onBack: () -> Unit, background: Color = DefaultGray) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .height(52.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back), fontSize = 18.sp, color = Color(0xFF35268D))
        }
    }
}
