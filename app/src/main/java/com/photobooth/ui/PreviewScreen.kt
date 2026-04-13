package com.photobooth.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.photobooth.Strings
import kotlinx.coroutines.delay
import java.io.File

private const val AUTO_DISMISS_SECONDS = 20

@Composable
fun PreviewScreen(
    photoFile: File,
    downloadUrl: String?,
    onDismiss: () -> Unit,
    onRetake: () -> Unit,
    s: Strings.Lang,
) {
    val context = LocalContext.current
    var secondsRemaining by remember { mutableIntStateOf(AUTO_DISMISS_SECONDS) }
    val progress by animateFloatAsState(
        targetValue = secondsRemaining.toFloat() / AUTO_DISMISS_SECONDS,
        animationSpec = tween(1000),
        label = "timerProgress",
    )

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
        onDismiss()
    }

    val photoBitmap = remember(photoFile) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photoFile.absolutePath, options)
        val maxDim = maxOf(options.outWidth, options.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 1600) sampleSize *= 2
        BitmapFactory.decodeFile(
            photoFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    }

    val qrBitmap = remember(downloadUrl) {
        downloadUrl?.let { generateQrCode(it, 280) }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .padding(16.dp),
    ) {
        // Left: Photo
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0xFF4ECB71), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(s.saved, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Right: QR + share + buttons
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (qrBitmap != null) {
                Text(
                    text = s.scanToDownload,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(2.dp, Color(0xFF6C63FF), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = s.connectHotspot,
                    fontSize = 11.sp,
                    color = Color(0x70FFFFFF),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { sharePhoto(context, photoFile) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF),
                    contentColor = Color.White,
                ),
            ) {
                Text(s.shareNearby, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF6C63FF),
                trackColor = Color(0xFF2A2A3E),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6584)),
                ) {
                    Text(s.newPhoto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xAAFFFFFF)),
                ) {
                    Text(s.done, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("${secondsRemaining}s", fontSize = 12.sp, color = Color(0x50FFFFFF))
        }
    }
}

private fun sharePhoto(context: Context, photoFile: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    } catch (e: Exception) {
        android.util.Log.e("PreviewScreen", "Share failed", e)
    }
}

private fun generateQrCode(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) { null }
}
