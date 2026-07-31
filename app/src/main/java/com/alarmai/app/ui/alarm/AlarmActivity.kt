package com.alarmai.app.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alarmai.app.ui.theme.Accent
import com.alarmai.app.ui.theme.AccentLine
import com.alarmai.app.ui.theme.AccentSurface
import com.alarmai.app.ui.theme.AlarmAITheme
import com.alarmai.app.ui.theme.ControlHeight
import com.alarmai.app.ui.theme.Danger
import com.alarmai.app.ui.theme.Ink
import com.alarmai.app.ui.theme.Line
import com.alarmai.app.ui.theme.PrimaryButton
import com.alarmai.app.ui.theme.SecondaryButton
import com.alarmai.app.ui.theme.Spacing
import com.alarmai.app.ui.theme.Surface1
import com.alarmai.app.ui.theme.Surface2
import com.alarmai.app.ui.theme.TextPrimary
import com.alarmai.app.ui.theme.TextSecondary
import com.alarmai.app.ui.theme.TextTertiary
import com.alarmai.app.ui.theme.appTextFieldColors
import androidx.compose.animation.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

class AlarmActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Show activity over lock screen
        setupLockScreenFlags()

        // Only on a genuinely new launch. getIntent() survives recreation, so without the
        // savedInstanceState check a rotation would restart the whole briefing on the
        // retained ViewModel, racing the session already in progress.
        if (savedInstanceState == null && intent.getBooleanExtra("is_direct_invoke", false)) {
            viewModel.dismissAndTalk()
        }

        setContent {
            AlarmAITheme {
                val state by viewModel.uiState.collectAsState()
                val chatMessages by viewModel.chatMessages.collectAsState()
                val userSpeech by viewModel.userSpeech.collectAsState()
                val statusMessage by viewModel.statusMessage.collectAsState()
                val geminiModelName by viewModel.geminiModelName.collectAsState()
                val micVolume by viewModel.micVolume.collectAsState()

                // If finished, close the activity
                LaunchedEffect(state) {
                    if (state == AlarmState.FINISHED) {
                        finish()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Ink
                ) {
                    AlarmScreenContent(
                        state = state,
                        chatMessages = chatMessages,
                        userSpeech = userSpeech,
                        statusMessage = statusMessage,
                        geminiModelName = geminiModelName,
                        micVolume = micVolume,
                        onDismiss = { viewModel.dismissAndTalk() },
                        onClose = { viewModel.forceClose() },
                        onSendText = { text -> viewModel.processUserSpeech(text) },
                        onMicClick = { viewModel.startListeningManual() },
                        onRetry = { viewModel.retry() }
                    )
                }
            }
        }
    }

    /**
     * The activity is `singleInstance`, so a new alarm (or a second "Talk to AI Assistant Now")
     * is delivered here rather than to onCreate. Without this the user would be shown the
     * previous session's stale FINISHED/ERROR screen while the new alarm rings.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupLockScreenFlags()
        viewModel.resetForNewAlarm()
        if (intent.getBooleanExtra("is_direct_invoke", false)) {
            viewModel.dismissAndTalk()
        }
    }

    private fun setupLockScreenFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}

/** Bubble geometry, shared so the three bubble types cannot drift apart. */
private val BubbleRadius = 14.dp
private val BubbleTailRadius = 4.dp

private fun bubbleShape(isUser: Boolean) = RoundedCornerShape(
    topStart = BubbleRadius,
    topEnd = BubbleRadius,
    bottomStart = if (isUser) BubbleRadius else BubbleTailRadius,
    bottomEnd = if (isUser) BubbleTailRadius else BubbleRadius
)

@Composable
fun AlarmScreenContent(
    state: AlarmState,
    chatMessages: List<ChatMessage>,
    userSpeech: String,
    statusMessage: String,
    geminiModelName: String,
    micVolume: Float,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onSendText: (String) -> Unit,
    onMicClick: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxHeight()
                // Keeps the top bar out from under the status bar and the chat input out from
                // under the nav bar; imePadding lifts the input above the keyboard.
                .safeDrawingPadding()
                .imePadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AlarmAI",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Surface2)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Alarm",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Central Area depending on State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                    },
                    label = "state_transition"
                ) { targetState ->
                    when (targetState) {
                        AlarmState.RINGING -> RingingLayout(onDismiss)
                        AlarmState.FETCHING_DATA -> LoadingLayout(statusMessage)
                        AlarmState.SPEAKING, AlarmState.LISTENING, AlarmState.THINKING -> {
                            ChatLayout(
                                chatMessages = chatMessages,
                                state = targetState,
                                userSpeech = userSpeech,
                                micVolume = micVolume,
                                onSendText = onSendText,
                                onMicClick = onMicClick
                            )
                        }
                        AlarmState.ERROR -> {
                            ErrorLayout(
                                status = statusMessage,
                                onRetry = onRetry,
                                onClose = onClose
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun RingingLayout(onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    // A restrained 8% breath. The old 25% jump was the loudest thing on the screen.
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val timeText = remember {
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(scale)
                .background(Surface1, CircleShape)
                .border(1.dp, AccentLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = "Alarm Icon",
                tint = Accent,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xxl))

        Text(
            text = timeText,
            style = MaterialTheme.typography.displayLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            text = "Rise and shine. Time to start the day.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(56.dp))

        PrimaryButton(
            text = "Dismiss & talk",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}

@Composable
fun LoadingLayout(status: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = Accent,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChatLayout(
    chatMessages: List<ChatMessage>,
    state: AlarmState,
    userSpeech: String,
    micVolume: Float,
    onSendText: (String) -> Unit,
    onMicClick: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var textVal by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on messages list change or transient user speech change
    LaunchedEffect(chatMessages.size, userSpeech) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Chat list area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(chatMessages) { message ->
                ChatBubble(message = message)
            }

            if (state == AlarmState.THINKING) {
                item {
                    AgentThinkingBubble()
                }
            }

            if (state == AlarmState.LISTENING && userSpeech.isNotBlank()) {
                item {
                    UserSpeechBubble(userSpeech = userSpeech)
                }
            }
        }

        // Action items and input
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual feedback indicator for Speaking or Listening
            if (state == AlarmState.LISTENING) {
                VoiceInputVisualizer(micVolume = micVolume)
            } else if (state == AlarmState.SPEAKING) {
                AgentSpeakingIndicator()
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Quick reply chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Skip", "Read Schedule", "Exit").forEach { label ->
                    QuickReplyChip(
                        text = label,
                        onClick = {
                            onSendText(label)
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    )
                }
            }

            // Chat input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = textVal,
                    onValueChange = { textVal = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (textVal.isNotBlank()) {
                                    onSendText(textVal)
                                    textVal = ""
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (textVal.isNotBlank()) Accent else TextTertiary
                            )
                        }
                    },
                    colors = appTextFieldColors(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )

                val listening = state == AlarmState.LISTENING
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(ControlHeight)
                        .background(if (listening) Accent else Surface2, CircleShape)
                        .border(1.dp, if (listening) Accent else Line, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start Listening",
                        tint = if (listening) TextPrimary else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val shape = bubbleShape(isUser)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(align = if (isUser) Alignment.End else Alignment.Start)
                // Flat fills: the user turn carries the accent, the agent turn a neutral surface.
                // Solid colour is what separates them now, not a gradient.
                .background(if (isUser) Accent else Surface1, shape)
                .border(1.dp, if (isUser) Accent else Line, shape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun UserSpeechBubble(userSpeech: String) {
    val shape = bubbleShape(isUser = true)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(align = Alignment.End)
                // Tinted rather than solid accent, so an in-progress transcription is visibly
                // provisional next to a committed user turn.
                .background(AccentSurface, shape)
                .border(1.dp, AccentLine, shape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs)
        ) {
            Text(
                text = "\"$userSpeech\"",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                ),
                color = TextSecondary
            )
        }
    }
}

@Composable
fun AgentThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    val shape = bubbleShape(isUser = false)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .background(Surface1, shape)
                .border(1.dp, Line, shape)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).scale(dot1Scale).background(TextSecondary, CircleShape))
            Box(modifier = Modifier.size(6.dp).scale(dot2Scale).background(TextSecondary, CircleShape))
            Box(modifier = Modifier.size(6.dp).scale(dot3Scale).background(TextSecondary, CircleShape))
        }
    }
}

@Composable
fun VoiceInputVisualizer(micVolume: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_mic_visualizer")

    val normalizedVolume = ((micVolume + 2f) / 12f).coerceIn(0f, 1f)
    val animatedVolume by animateFloatAsState(
        targetValue = normalizedVolume,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "animated_volume"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val barColor = Accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(150.dp, 32.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            val barCount = 15
            val barWidth = 3.dp.toPx()
            val space = 6.dp.toPx()

            for (i in 0 until barCount) {
                val distanceFromCenter = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                val baseHeight = (18.dp.toPx() * (1f - distanceFromCenter)).coerceAtLeast(3.dp.toPx())
                val phaseOffset = i * 0.3f
                val waveFactor = kotlin.math.sin(wavePhase * 2 * kotlin.math.PI.toFloat() + phaseOffset)

                val barHeight = baseHeight * (0.3f + 0.7f * animatedVolume) * (0.8f + 0.2f * waveFactor)

                val x = (width - (barCount * barWidth + (barCount - 1) * space)) / 2 + i * (barWidth + space)
                val y = centerY - barHeight / 2

                drawRoundRect(
                    color = barColor.copy(alpha = 0.9f - 0.5f * distanceFromCenter),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                )
            }
        }
    }
}

@Composable
fun AgentSpeakingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_speaking")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speaking_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Speaking",
            tint = TextSecondary,
            modifier = Modifier
                .size(16.dp)
                .scale(scale)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = "Agent is speaking...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
fun QuickReplyChip(
    text: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(Surface2)
            .border(1.dp, Line, shape)
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
    }
}

@Composable
fun ErrorLayout(
    status: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(Spacing.xl)
    ) {
        // A warning glyph, not the close X the old layout reused — an X next to a Close button
        // read as a second dismiss affordance rather than as an error.
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            tint = Danger,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(text = "Close", onClick = onClose)
            PrimaryButton(text = "Retry", onClick = onRetry)
        }
    }
}
