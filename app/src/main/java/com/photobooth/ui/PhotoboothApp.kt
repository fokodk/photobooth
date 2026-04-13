package com.photobooth.ui

import androidx.compose.runtime.*
import com.photobooth.PhotoboothViewModel
import com.photobooth.PhotoboothViewModel.UiState
import com.photobooth.Strings

@Composable
fun PhotoboothApp(viewModel: PhotoboothViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val photoCount by viewModel.photoCount.collectAsState()
    val liveViewFrame by viewModel.liveViewFrame.collectAsState()
    val lang by viewModel.settings.language.collectAsState()
    val countdownSec by viewModel.settings.countdownSeconds.collectAsState()
    val prepareSec by viewModel.settings.prepareSeconds.collectAsState()
    val mirrorLV by viewModel.settings.mirrorLiveView.collectAsState()

    val s = Strings.get(lang)

    var showSettings by remember { mutableStateOf(false) }

    val downloadUrl = remember {
        val ip = viewModel.photoServer.getLocalIpAddress()
        if (ip != null) "http://$ip:${viewModel.photoServer.port}" else null
    }

    if (showSettings) {
        SettingsScreen(
            settings = viewModel.settings,
            onBack = { showSettings = false },
        )
        return
    }

    when (val state = uiState) {
        is UiState.Idle -> {
            WelcomeScreen(
                photoCount = photoCount,
                onTapToCapture = viewModel::startCountdown,
                onOpenSettings = { showSettings = true },
                s = s,
            )
        }
        is UiState.Countdown -> {
            CountdownScreen(
                liveViewFrame = liveViewFrame,
                onTriggerAF = viewModel::triggerAutofocus,
                onCountdownFinished = viewModel::capturePhoto,
                countdownSeconds = countdownSec,
                prepareSeconds = prepareSec,
                mirrorLiveView = mirrorLV,
                s = s,
            )
        }
        is UiState.Capturing -> {
            CapturingScreen(s = s)
        }
        is UiState.Preview -> {
            PreviewScreen(
                photoFile = state.photoFile,
                downloadUrl = downloadUrl,
                onDismiss = viewModel::dismissPreview,
                onRetake = viewModel::retakeFromPreview,
                s = s,
            )
        }
        is UiState.Error -> {
            ErrorScreen(
                message = state.message,
                onRetry = viewModel::resetToIdle,
                s = s,
            )
        }
        is UiState.NotConnected -> {
            ErrorScreen(
                message = s.connectCamera,
                onRetry = viewModel::connectCamera,
                s = s,
            )
        }
    }
}
