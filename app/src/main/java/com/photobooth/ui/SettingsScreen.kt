package com.photobooth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.AppSettings
import com.photobooth.Strings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val lang by settings.language.collectAsState()
    val watermarkText by settings.watermarkText.collectAsState()
    val watermarkEnabled by settings.watermarkEnabled.collectAsState()
    val countdownSeconds by settings.countdownSeconds.collectAsState()
    val prepareSeconds by settings.prepareSeconds.collectAsState()
    val mirrorLiveView by settings.mirrorLiveView.collectAsState()

    val s = Strings.get(lang)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2A3E),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(s.back, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = s.settings,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Language
        SettingsSection(s.language) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LanguageChip("Dansk", lang == "da") { settings.setLanguage("da") }
                LanguageChip("English", lang == "en") { settings.setLanguage("en") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Watermark
        SettingsSection(s.watermark) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.watermarkEnabled, color = Color(0xFFB0B0C0), fontSize = 16.sp)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = watermarkEnabled,
                    onCheckedChange = { settings.setWatermarkEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF6C63FF),
                        checkedTrackColor = Color(0xFF3D3980),
                    ),
                )
            }

            if (watermarkEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { settings.setWatermarkText(it) },
                    label = { Text(s.watermarkText) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6C63FF),
                        unfocusedBorderColor = Color(0xFF3A3A4E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF6C63FF),
                        unfocusedLabelColor = Color(0xFF808090),
                        cursorColor = Color(0xFF6C63FF),
                    ),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (lang == "da") "F.eks. \"Bryllup 2026\" eller \"@mitfirma\"" else "E.g. \"Wedding 2026\" or \"@mybrand\"",
                    fontSize = 13.sp,
                    color = Color(0xFF606070),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Countdown
        SettingsSection(s.countdown) {
            SliderSetting(
                label = s.countdown,
                value = countdownSeconds,
                range = 3..10,
                suffix = s.seconds,
                onValueChange = { settings.setCountdownSeconds(it) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            SliderSetting(
                label = s.prepareTime,
                value = prepareSeconds,
                range = 0..10,
                suffix = s.seconds,
                onValueChange = { settings.setPrepareSeconds(it) },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mirror
        SettingsSection("Live View") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.mirrorLiveView, color = Color(0xFFB0B0C0), fontSize = 16.sp)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = mirrorLiveView,
                    onCheckedChange = { settings.setMirrorLiveView(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF6C63FF),
                        checkedTrackColor = Color(0xFF3D3980),
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A2E))
            .padding(20.dp),
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6C63FF),
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) Modifier.background(Color(0xFF6C63FF))
                else Modifier.border(1.dp, Color(0xFF3A3A4E), RoundedCornerShape(12.dp))
            )
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color(0xFFB0B0C0),
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label, color = Color(0xFFB0B0C0), fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "$value $suffix",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF6C63FF),
                activeTrackColor = Color(0xFF6C63FF),
                inactiveTrackColor = Color(0xFF2A2A3E),
            ),
        )
    }
}
