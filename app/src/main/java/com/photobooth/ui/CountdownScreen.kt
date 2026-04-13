package com.photobooth.ui

import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun CountdownScreen(
    liveViewFrame: Bitmap?,
    onTriggerAF: () -> Unit,
    onCountdownFinished: () -> Unit,
    countdownSeconds: Int = 5,
    prepareSeconds: Int = 5,
    mirrorLiveView: Boolean = true,
    s: Strings.Lang,
) {
    var phase by remember { mutableStateOf("prepare") }
    var prepareCount by remember { mutableIntStateOf(prepareSeconds) }
    var currentNumber by remember { mutableIntStateOf(countdownSeconds) }

    val scale by animateFloatAsState(
        targetValue = if (currentNumber > 0) 1.2f else 0.8f,
        animationSpec = tween(durationMillis = 300, easing = EaseOut),
        label = "countdownScale",
    )

    LaunchedEffect(Unit) {
        // Create ToneGenerator on IO thread to avoid blocking main thread
        val toneGen = withContext(Dispatchers.IO) {
            try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) } catch (_: Exception) { null }
        }

        // Phase 1: Get in position
        if (prepareSeconds > 0) {
            for (sec in prepareSeconds downTo 1) {
                prepareCount = sec
                delay(1000)
            }
        }

        // Phase 2: Countdown
        phase = "countdown"
        for (num in countdownSeconds downTo 1) {
            currentNumber = num
            withContext(Dispatchers.IO) {
                try { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (_: Exception) {}
            }
            if (num == 3) onTriggerAF()
            delay(1000)
        }
        withContext(Dispatchers.IO) {
            try { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 200) } catch (_: Exception) {}
        }
        currentNumber = 0
        delay(100)
        withContext(Dispatchers.IO) { toneGen?.release() }
        onCountdownFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A)),
        contentAlignment = Alignment.Center,
    ) {
        if (liveViewFrame != null) {
            Image(
                bitmap = liveViewFrame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = if (mirrorLiveView) -1f else 1f),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000)),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (phase == "prepare") {
                Text(
                    text = s.getInPosition,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6C63FF),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$prepareCount",
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0x80FFFFFF),
                )
            } else {
                if (currentNumber > 0) {
                    Text(
                        text = "$currentNumber",
                        fontSize = 220.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (currentNumber) {
                            5, 4 -> Color(0xFF6C63FF)
                            3 -> Color(0xFFFFB347)
                            2 -> Color(0xFFFF6584)
                            1 -> Color(0xFF4ECB71)
                            else -> Color.White
                        },
                        modifier = Modifier.scale(scale),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (currentNumber >= 3) s.getReady else s.smile,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xAAFFFFFF),
                )
            }
        }
    }
}
