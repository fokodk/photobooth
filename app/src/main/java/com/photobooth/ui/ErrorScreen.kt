package com.photobooth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.Strings

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    s: Strings.Lang,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text = "\u26A0\uFE0F", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = s.oops,
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFFFF6B6B),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color(0xFFB0B0C0),
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.height(64.dp).fillMaxWidth(0.6f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
            ) {
                Text(
                    text = s.tryAgain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
