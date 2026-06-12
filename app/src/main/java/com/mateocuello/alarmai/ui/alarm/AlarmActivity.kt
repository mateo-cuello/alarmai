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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show activity over lock screen
        setupLockScreenFlags()

        setContent {
            AlarmAITheme {
                val state by viewModel.uiState.collectAsState()
                val agentSpeech by viewModel.agentSpeech.collectAsState()
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
                        agentSpeech = agentSpeech,
                        userSpeech = userSpeech,
                        statusMessage = statusMessage,
                        geminiModelName = geminiModelName,
                        micVolume = micVolume,
                        onDismiss = { viewModel.dismissAndTalk() },
                        onClose = { viewModel.forceClose() },
                        onSendText = { text -> viewModel.processUserSpeech(text) },
                        onMicClick = { viewModel.startListeningManual() }
                    )
                }
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
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
    agentSpeech: String,
    userSpeech: String,
    statusMessage: String,
    geminiModelName: String,
    micVolume: Float,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onSendText: (String) -> Unit,
    onMicClick: () -> Unit
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
            .padding(24.dp),
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
                    .padding(top = 16.dp),
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
                when (state) {
                    AlarmState.RINGING -> RingingLayout(onDismiss)
                    AlarmState.FETCHING_DATA -> LoadingLayout(statusMessage)
                    AlarmState.SPEAKING -> SpeakingLayout(agentSpeech, onSendText, onMicClick)
                    AlarmState.LISTENING -> ListeningLayout(userSpeech, micVolume, onSendText, onMicClick)
                    AlarmState.THINKING -> ThinkingLayout()
                    else -> {}
                }
            }

            // Footer
            val displayName = when (geminiModelName) {
                "gemini-3.5-flash" -> "Gemini 3.5 Flash"
                "gemini-3.1-flash-lite" -> "Gemini 3.1 Flash"
                else -> "Gemini"
            }
            Text(
                text = "Powered by $displayName",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
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
fun SpeakingLayout(
    speech: String,
    onSendText: (String) -> Unit,
    onMicClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_agent")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "agent_scale"
    )

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var textVal by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section with speaker and card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .background(SecondaryPink.copy(alpha = 0.2f), shape = CircleShape)
                    .border(2.dp, SecondaryPink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Agent Speaking",
                    tint = SecondaryPink,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f))
            ) {
                Text(
                    text = speech,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom section with quick actions and keyboard fallback
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick reply chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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

            // Input text field and mic button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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

                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF06B6D4), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start Listening",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ListeningLayout(
    userSpeech: String,
    micVolume: Float,
    onSendText: (String) -> Unit,
    onMicClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_mic")
    
    // Smooth the volume changes to avoid jittery movements
    // RmsdB is typically -2f to 10f. Let's normalize it to 0f to 1f.
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
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var textVal by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section with listening state
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Concentrate glowing canvas waves around the mic icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                // Wave Canvas in the background
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = this.center
                    val baseRadius = 52.dp.toPx()
                    val maxRadius = 110.dp.toPx()
                    
                    // We draw 3 concentric glowing waves
                    for (i in 0..2) {
                        val waveProgress = (wavePhase + i / 3f) % 1f
                        val volumeMultiplier = 0.3f + 0.7f * animatedVolume
                        val currentRadius = baseRadius + waveProgress * (maxRadius - baseRadius) * volumeMultiplier
                        
                        // Alpha fades out as it expands
                        val alpha = (1f - waveProgress) * 0.4f * volumeMultiplier
                        
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF06B6D4).copy(alpha = alpha),
                                    Color(0xFF06B6D4).copy(alpha = alpha * 0.5f),
                                    Color(0xFF06B6D4).copy(alpha = 0f)
                                ),
                                center = center,
                                radius = currentRadius.coerceAtLeast(1f)
                            ),
                            radius = currentRadius,
                            center = center
                        )
                    }
                }

                // Central Mic Circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.White.copy(alpha = 0.08f), shape = CircleShape)
                        .border(1.5.dp, Color(0xFF06B6D4).copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Listening",
                        tint = Color(0xFF06B6D4),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Listening...",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF06B6D4),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (userSpeech.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f))
                ) {
                    Text(
                        text = "\"$userSpeech\"",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Text(
                    text = "Say something like 'what is the weather?'",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom section with quick actions and keyboard fallback
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick reply chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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

            // Input text field and mic button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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

                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF06B6D4), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start Listening",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun QuickReplyChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
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
fun ThinkingLayout() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = Color(0xFF06B6D4),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Agent is thinking...",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
