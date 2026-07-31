package com.alarmai.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.media.RingtoneManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.alarmai.app.data.model.GeminiModels
import com.alarmai.app.data.repository.VoiceManager
import com.alarmai.app.receiver.AlarmReceiver
import com.alarmai.app.receiver.AlarmScheduler
import com.alarmai.app.ui.theme.Accent
import com.alarmai.app.ui.theme.AlarmAITheme
import com.alarmai.app.ui.theme.ControlHeight
import com.alarmai.app.ui.theme.FieldHint
import com.alarmai.app.ui.theme.FieldLabel
import com.alarmai.app.ui.theme.Ink
import com.alarmai.app.ui.theme.Line
import com.alarmai.app.ui.theme.PrimaryButton
import com.alarmai.app.ui.theme.SecondaryButton
import com.alarmai.app.ui.theme.SectionCard
import com.alarmai.app.ui.theme.SelectablePill
import com.alarmai.app.ui.theme.Spacing
import com.alarmai.app.ui.theme.Surface2
import com.alarmai.app.ui.theme.Surface3
import com.alarmai.app.ui.theme.TextPrimary
import com.alarmai.app.ui.theme.TextSecondary
import com.alarmai.app.ui.theme.TextTertiary
import com.alarmai.app.ui.theme.appTextFieldColors

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Undo a stream mute left behind if a previous voice session's process was killed.
        VoiceManager.recoverStaleMute(this)
        setContent {
            AlarmAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Ink
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadAlarm()
        viewModel.fetchLocation()
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val alarm by viewModel.alarm.collectAsState()
    val geminiKey by viewModel.geminiKey.collectAsState()
    val geminiModel by viewModel.geminiModel.collectAsState()
    val newsTopics by viewModel.newsTopics.collectAsState()
    val alarmVolume by viewModel.alarmVolume.collectAsState()
    val alarmRingtoneUri by viewModel.alarmRingtoneUri.collectAsState()
    val language by viewModel.language.collectAsState()
    val voiceName by viewModel.voiceName.collectAsState()
    val tonePreference by viewModel.tonePreference.collectAsState()
    val availableVoices by viewModel.availableVoices.collectAsState()

    var showGeminiPassword by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Permissions list
    val permissionsToRequest = mutableListOf(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val calendarGranted = permissions[android.Manifest.permission.READ_CALENDAR] ?: false
        val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        val locationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (locationGranted) {
            viewModel.fetchLocation()
        }

        if (!calendarGranted || !audioGranted || !locationGranted) {
            Toast.makeText(
                context,
                "Please grant all permissions in app settings for full functionality.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val uriString = uri?.toString() ?: ""
            viewModel.saveAlarmRingtoneUri(uriString)
        }
    }

    val launchRingtonePicker = {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Ringtone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            if (alarmRingtoneUri.isNotEmpty()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarmRingtoneUri))
            } else {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, defaultUri)
            }
        }
        ringtonePickerLauncher.launch(intent)
    }

    // Request permissions at startup
    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
        checkExactAlarmPermission(context)
        checkFullScreenIntentPermission(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            // Content insets so the title clears the status bar and the last card clears the
            // gesture nav bar (edge-to-edge is enforced at targetSdk 35+); imePadding keeps the
            // focused text field above the keyboard.
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Header
        Column(modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.md)) {
            Text(
                text = "AlarmAI",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Text(
                text = "Your voice-powered morning assistant",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }

        // Wake-up time
        SectionCard(title = "Wake-Up Time", icon = Icons.Default.Alarm) {
            val formattedTime = String.format("%02d:%02d", alarm.hour, alarm.minute)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                    modifier = Modifier
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    viewModel.updateAlarmTime(hour, minute)
                                },
                                alarm.hour,
                                alarm.minute,
                                true
                            ).show()
                        }
                        .padding(vertical = Spacing.xs)
                )

                Switch(
                    checked = alarm.isActive,
                    onCheckedChange = { viewModel.toggleAlarmActive(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = Accent,
                        checkedBorderColor = Accent,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = Surface2,
                        uncheckedBorderColor = Line
                    )
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                val daysList = listOf(
                    java.util.Calendar.MONDAY to "M",
                    java.util.Calendar.TUESDAY to "T",
                    java.util.Calendar.WEDNESDAY to "W",
                    java.util.Calendar.THURSDAY to "T",
                    java.util.Calendar.FRIDAY to "F",
                    java.util.Calendar.SATURDAY to "S",
                    java.util.Calendar.SUNDAY to "S"
                )

                daysList.forEach { (dayInt, label) ->
                    val isSelected = alarm.daysOfWeek.contains(dayInt)
                    SelectablePill(
                        text = label,
                        selected = isSelected,
                        onClick = { viewModel.toggleAlarmDay(dayInt) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("day_toggle_$dayInt")
                            .semantics { this.selected = isSelected }
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = if (alarm.isActive) "Alarm is scheduled" else "Alarm is disabled",
                style = MaterialTheme.typography.bodyMedium,
                color = if (alarm.isActive) TextSecondary else TextTertiary
            )
        }

        // Sound
        SectionCard(title = "Sound & Volume", icon = Icons.AutoMirrored.Filled.VolumeUp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FieldLabel("Alarm volume")
                Text(
                    text = "$alarmVolume%",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Slider(
                value = alarmVolume.toFloat(),
                onValueChange = { viewModel.saveAlarmVolume(it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Line
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.md))

            FieldLabel("Ringtone")
            Spacer(Modifier.height(Spacing.sm))
            SecondaryButton(
                text = getRingtoneTitle(context, alarmRingtoneUri),
                onClick = { launchRingtonePicker() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Assistant persona
        SectionCard(title = "Assistant Persona & Voice", icon = Icons.Default.Translate) {
            FieldLabel("Assistant language")
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                val langs = listOf(
                    "es" to "Español",
                    "en" to "English"
                )
                langs.forEach { (langId, displayName) ->
                    SelectablePill(
                        text = displayName,
                        selected = language == langId,
                        onClick = { viewModel.saveLanguage(langId) },
                        modifier = Modifier
                            .weight(1f)
                            .height(ControlHeight)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            FieldLabel("Text-to-speech voice")
            Spacer(Modifier.height(Spacing.sm))
            var expandedVoices by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = if (voiceName.isEmpty()) {
                        "System default"
                    } else {
                        voiceName.substringAfterLast(".").take(25)
                    },
                    onClick = { expandedVoices = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = expandedVoices,
                    onDismissRequest = { expandedVoices = false },
                    // material3 1.2.0 has no `containerColor` parameter yet, so the surface is
                    // painted through the modifier.
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(Surface3)
                ) {
                    DropdownMenuItem(
                        text = { Text("System default", color = TextPrimary) },
                        onClick = {
                            viewModel.saveVoiceName("")
                            expandedVoices = false
                        }
                    )
                    availableVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.substringAfterLast("."), color = TextPrimary) },
                            onClick = {
                                viewModel.saveVoiceName(voice)
                                expandedVoices = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            FieldLabel("Tone & communication style")
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = tonePreference,
                onValueChange = { viewModel.saveTonePreference(it) },
                placeholder = { Text("e.g. Sarcastic and funny, warm and energetic, formal and brief...") },
                colors = appTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }

        // Credentials
        SectionCard(title = "Agent Credentials", icon = Icons.Default.Key) {
            FieldLabel("Gemini API key")
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = geminiKey,
                onValueChange = { viewModel.saveGeminiKey(it) },
                placeholder = { Text("Paste your Gemini API key") },
                singleLine = true,
                visualTransformation = if (showGeminiPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showGeminiPassword = !showGeminiPassword }) {
                        Icon(
                            imageVector = if (showGeminiPassword) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = "Toggle Visibility",
                            tint = TextSecondary
                        )
                    }
                },
                colors = appTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.lg))

            FieldLabel("AI model")
            FieldHint(
                text = "Falls back to the next model automatically if this one is busy",
                modifier = Modifier.padding(top = 2.dp, bottom = Spacing.sm)
            )
            var expandedModels by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = GeminiModels.displayName(geminiModel),
                    onClick = { expandedModels = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = expandedModels,
                    onDismissRequest = { expandedModels = false },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(Surface3)
                ) {
                    GeminiModels.CHAIN.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = GeminiModels.displayName(model),
                                    color = if (model == geminiModel) TextPrimary else TextSecondary
                                )
                            },
                            onClick = {
                                viewModel.saveGeminiModel(model)
                                expandedModels = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            FieldLabel("News topics")
            FieldHint(
                text = "Headlines from Google News (no API key needed)",
                modifier = Modifier.padding(top = 2.dp, bottom = Spacing.sm)
            )
            OutlinedTextField(
                value = newsTopics,
                onValueChange = { viewModel.saveNewsTopics(it) },
                placeholder = { Text("technology, science, world") },
                singleLine = true,
                colors = appTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Developer tools
        SectionCard(title = "Developer Testing Tools", icon = Icons.Default.PlayArrow) {
            SecondaryButton(
                text = "Test alarm (fires in 5 seconds)",
                onClick = { triggerTestAlarm(context) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton(
                text = "Talk to AI assistant now",
                onClick = {
                    val intent = Intent(context, com.alarmai.app.ui.alarm.AlarmActivity::class.java).apply {
                        putExtra("is_direct_invoke", true)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

private fun triggerTestAlarm(context: Context) {
    Toast.makeText(context, "Alarm will trigger in 5 seconds. Lock your phone now!", Toast.LENGTH_SHORT).show()
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        action = AlarmScheduler.ACTION_TEST_ALARM
        putExtra("is_test", true)
    }
    // Must NOT reuse REQUEST_CODE_MAIN: Intent.filterEquals ignores extras, so sharing the
    // request code would make this the same PendingIntent as the real alarm and overwrite it.
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        AlarmScheduler.REQUEST_CODE_TEST,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val triggerTime = System.currentTimeMillis() + 5000

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Test alarm failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

private fun checkExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(
                context,
                "Please enable 'Alarms & Reminders' permission in settings for exact alarms.",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general settings
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            }
        }
    }
}

private fun checkFullScreenIntentPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.canUseFullScreenIntent()) {
            Toast.makeText(
                context,
                "Please enable 'Full Screen Notifications' permission for the alarm to show over the lockscreen.",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            }
        }
    }
}

fun getRingtoneTitle(context: Context, uriString: String): String {
    if (uriString.isEmpty()) return "Default alarm sound"
    return try {
        val uri = Uri.parse(uriString)
        val ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone?.getTitle(context) ?: "Unknown ringtone"
    } catch (e: Exception) {
        "Unknown ringtone"
    }
}
