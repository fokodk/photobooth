package com.photobooth.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.Strings
import kotlinx.coroutines.delay

@Composable
fun CapturingScreen(s: Strings.Lang) {
    var flashAlpha by remember { mutableFloatStateOf(1f) }
    val animatedFlash by animateFloatAsState(
        targetValue = flashAlpha,
        animationSpec = tween(durationMillis = 400, easing = EaseOut),
        label = "flash",
    )

    LaunchedEffect(Unit) {
        delay(50)
        flashAlpha = 0f
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotsAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotsAlpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F1A)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "\uD83D\uDCF8", fontSize = 80.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = s.takingPhoto,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(dotsAlpha),
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFF6C63FF),
                    strokeWidth = 4.dp,
                )
            }
        }
        if (animatedFlash > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(animatedFlash)
                    .background(Color.White),
            )
        }
    }
}
