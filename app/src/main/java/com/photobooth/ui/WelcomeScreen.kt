package com.photobooth.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.Strings

@Composable
fun WelcomeScreen(
    photoCount: Int,
    onTapToCapture: () -> Unit,
    onOpenSettings: () -> Unit,
    s: Strings.Lang,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                onTapToCapture()
            },
        contentAlignment = Alignment.Center,
    ) {
        // Settings gear - top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A2E))
                .clickable { onOpenSettings() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "\u2699\uFE0F",
                fontSize = 24.sp,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .scale(pulseScale)
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF6C63FF),
                                Color(0xFFFF6584),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(Color(0x40FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\uD83D\uDCF7",
                        fontSize = 56.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = s.tapForPhoto,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = s.tapAnywhere,
                fontSize = 18.sp,
                color = Color(0x80FFFFFF),
                textAlign = TextAlign.Center,
            )
        }

        if (photoCount > 0) {
            Text(
                text = "$photoCount ${s.photosTaken}",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xAA6C63FF),
                    fontSize = 16.sp,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            )
        }
    }
}
