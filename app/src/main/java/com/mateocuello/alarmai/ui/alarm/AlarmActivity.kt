package com.mateocuello.alarmai.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateocuello.alarmai.ui.theme.AlarmAITheme
import com.mateocuello.alarmai.ui.theme.DarkBg
import com.mateocuello.alarmai.ui.theme.PrimaryPurple
import com.mateocuello.alarmai.ui.theme.SecondaryPink
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

        // Show activity over lock screen
        setupLockScreenFlags()

        val isDirectInvoke = intent.getBooleanExtra("is_direct_invoke", false)
        if (isDirectInvoke) {
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
                    color = DarkBg
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
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0B0F19), // Deep cosmic black-blue
            Color(0xFF1E1B4B), // Deep indigo
            Color(0xFF0F172A)  // Slate 900
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AlarmAI",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Alarm",
                        tint = Color.White
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
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
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
                .size(150.dp)
                .scale(scale)
                .background(PrimaryPurple.copy(alpha = 0.2f), shape = CircleShape)
                .border(2.dp, PrimaryPurple, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = "Alarm Icon",
                tint = PrimaryPurple,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = timeText,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp),
            color = Color.White,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Rise and shine! Time to start the day.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(60.dp)
                .border(2.dp, SecondaryPink, RoundedCornerShape(30.dp)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text(
                text = "Dismiss & Talk",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun LoadingLayout(status: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = SecondaryPink,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
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
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Quick reply chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textVal,
                    onValueChange = { textVal = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...", color = Color.White.copy(alpha = 0.5f)) },
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
                                tint = PrimaryPurple
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                val micBgColor = if (state == AlarmState.LISTENING) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.15f)
                val micIconColor = if (state == AlarmState.LISTENING) Color.White else Color.White.copy(alpha = 0.7f)
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(micBgColor, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start Listening",
                        tint = micIconColor
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(align = if (isUser) Alignment.End else Alignment.Start)
                .background(
                    brush = if (isUser) {
                        Brush.linearGradient(
                            colors = listOf(PrimaryPurple, SecondaryPink)
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))
                        )
                    },
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
                color = Color.White
            )
        }
    }
}

@Composable
fun UserSpeechBubble(userSpeech: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(align = Alignment.End)
                .background(
                    color = PrimaryPurple.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
                )
                .border(
                    width = 1.dp,
                    color = PrimaryPurple.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "\"$userSpeech\"",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                ),
                color = Color.White.copy(alpha = 0.9f)
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .background(
                    Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).scale(dot1Scale).background(MaterialTheme.colorScheme.tertiary, CircleShape))
            Box(modifier = Modifier.size(8.dp).scale(dot2Scale).background(MaterialTheme.colorScheme.tertiary, CircleShape))
            Box(modifier = Modifier.size(8.dp).scale(dot3Scale).background(MaterialTheme.colorScheme.tertiary, CircleShape))
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

    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(150.dp, 40.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            val barCount = 15
            val barWidth = 4.dp.toPx()
            val space = 6.dp.toPx()
            
            for (i in 0 until barCount) {
                val distanceFromCenter = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                val baseHeight = (20.dp.toPx() * (1f - distanceFromCenter)).coerceAtLeast(4.dp.toPx())
                val phaseOffset = i * 0.3f
                val waveFactor = kotlin.math.sin(wavePhase * 2 * kotlin.math.PI.toFloat() + phaseOffset)
                
                val barHeight = baseHeight * (0.3f + 0.7f * animatedVolume) * (0.8f + 0.2f * waveFactor)
                
                val x = (width - (barCount * barWidth + (barCount - 1) * space)) / 2 + i * (barWidth + space)
                val y = centerY - barHeight / 2
                
                drawRoundRect(
                    color = tertiaryColor.copy(alpha = 0.8f - 0.4f * distanceFromCenter),
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
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Speaking",
            tint = SecondaryPink.copy(alpha = 0.8f),
            modifier = Modifier
                .size(20.dp)
                .scale(scale)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Agent is speaking...",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryPink.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun QuickReplyChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f)
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
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Error Icon",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Close", color = Color.White)
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Retry", color = Color.White)
            }
        }
    }
}
