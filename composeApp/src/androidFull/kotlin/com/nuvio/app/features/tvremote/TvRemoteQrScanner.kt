package com.nuvio.app.features.tvremote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.touchlab.kermit.Logger
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_remote_scanner_close
import nuvio.composeapp.generated.resources.tv_remote_scanner_hint
import nuvio.composeapp.generated.resources.tv_remote_scanner_not_nuvio
import nuvio.composeapp.generated.resources.tv_remote_scanner_open_settings
import nuvio.composeapp.generated.resources.tv_remote_scanner_permission_body
import nuvio.composeapp.generated.resources.tv_remote_scanner_permission_grant
import nuvio.composeapp.generated.resources.tv_remote_scanner_permission_title
import nuvio.composeapp.generated.resources.tv_remote_scanner_title
import nuvio.composeapp.generated.resources.tv_remote_scanner_torch
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("PhoneTvRemote")

/**
 * Full screen in-app QR scanner (camera preview inside the app, no external activity).
 * Only Nuvio TV pairing codes are accepted: any other QR shows a hint and scanning goes on.
 */
@Composable
internal fun TvRemoteQrScanner(onResult: (String?) -> Unit) {
    val callback by rememberUpdatedState(onResult)
    Dialog(
        onDismissRequest = { callback(null) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        BackHandler { callback(null) }
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            )
        }
        var asked by remember { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            asked = true
            if (!ok) log.w { "Camera permission denied: QR scanner unavailable" }
        }
        LaunchedEffect(Unit) {
            if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                ScannerContent(
                    onScanned = { text -> callback(text) },
                    onClose = { callback(null) },
                )
            } else {
                PermissionContent(
                    showSettings = asked,
                    onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", context.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    onClose = { callback(null) },
                )
            }
        }
    }
}

@Composable
private fun ScannerContent(onScanned: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = LocalHapticFeedback.current
    var torch by remember { mutableStateOf(false) }
    var wrongCode by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val scanned by rememberUpdatedState(onScanned)

    val barcodeView = remember {
        BarcodeView(context).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            cameraSettings.isAutoFocusEnabled = true
            cameraSettings.isContinuousFocusEnabled = true
        }
    }
    DisposableEffect(barcodeView) {
        barcodeView.decodeContinuous(
            BarcodeCallback { result ->
                val text = result?.text ?: return@BarcodeCallback
                if (done) return@BarcodeCallback
                if (TvRemotePairingInfo.parse(text) != null) {
                    done = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    log.i { "Nuvio TV QR code scanned" }
                    barcodeView.pause()
                    scanned(text)
                } else if (!wrongCode) {
                    log.d { "Scanned a QR code that is not a Nuvio TV code" }
                    wrongCode = true
                }
            },
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!done) barcodeView.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) barcodeView.resume()
        onDispose {
            lifecycle.removeObserver(observer)
            barcodeView.pause()
        }
    }
    LaunchedEffect(wrongCode) {
        if (wrongCode) {
            delay(2_500L)
            wrongCode = false
        }
    }
    LaunchedEffect(torch) { runCatching { barcodeView.setTorch(torch) } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { barcodeView }, modifier = Modifier.fillMaxSize())
        val frame = minOf(maxWidth, maxHeight) * 0.68f
        ScannerOverlay(frameSize = frame.value)

        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(
                    onClick = onClose,
                    icon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.tv_remote_scanner_close)) },
                )
                Text(
                    text = stringResource(Res.string.tv_remote_scanner_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(
                    onClick = { torch = !torch },
                    active = torch,
                    icon = {
                        Icon(
                            if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = stringResource(Res.string.tv_remote_scanner_torch),
                        )
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = wrongCode, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = stringResource(Res.string.tv_remote_scanner_not_nuvio),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .background(Color(0xE6B3261E), RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Text(
                text = stringResource(Res.string.tv_remote_scanner_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }
    }
}

/** Dimmed background with a rounded window, animated corners and a scan line. */
@Composable
private fun ScannerOverlay(frameSize: Float) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "qr-scan")
    val line by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse),
        label = "qr-scan-line",
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val side = frameSize.dp.toPx()
        val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f - 24.dp.toPx())
        val radius = 28.dp.toPx()
        drawRect(Color.Black.copy(alpha = 0.62f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = topLeft,
            size = Size(side, side),
            cornerRadius = CornerRadius(radius),
            blendMode = BlendMode.Clear,
        )
        // Corner brackets.
        val stroke = 5.dp.toPx()
        val arm = side * 0.16f
        val l = topLeft.x
        val t = topLeft.y
        val r = topLeft.x + side
        val b = topLeft.y + side
        fun corner(startX: Float, startY: Float, dx: Float, dy: Float, arcStart: Float) {
            drawLine(accent, Offset(startX + dx * radius, startY), Offset(startX + dx * (radius + arm), startY), stroke, StrokeCap.Round)
            drawLine(accent, Offset(startX, startY + dy * radius), Offset(startX, startY + dy * (radius + arm)), stroke, StrokeCap.Round)
            drawArc(
                color = accent,
                startAngle = arcStart,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(if (dx > 0) startX else startX - 2 * radius, if (dy > 0) startY else startY - 2 * radius),
                size = Size(2 * radius, 2 * radius),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        corner(l, t, 1f, 1f, 180f)
        corner(r, t, -1f, 1f, 270f)
        corner(l, b, 1f, -1f, 90f)
        corner(r, b, -1f, -1f, 0f)
        // Scan line.
        val y = t + side * line
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, accent, Color.Transparent),
                startX = l + 16.dp.toPx(),
                endX = r - 16.dp.toPx(),
            ),
            start = Offset(l + 16.dp.toPx(), y),
            end = Offset(r - 16.dp.toPx(), y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, active: Boolean = false, icon: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (active) Color.White else Color.Black.copy(alpha = 0.5f),
            contentColor = if (active) Color.Black else Color.White,
        ),
    ) { icon() }
}

@Composable
private fun PermissionContent(
    showSettings: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.NoPhotography, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.tv_remote_scanner_permission_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.tv_remote_scanner_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = if (showSettings) onOpenSettings else onGrant) {
            Text(
                stringResource(
                    if (showSettings) Res.string.tv_remote_scanner_open_settings else Res.string.tv_remote_scanner_permission_grant,
                ),
            )
        }
        TextButton(onClick = onClose) {
            Text(stringResource(Res.string.tv_remote_scanner_close), color = Color.White.copy(alpha = 0.8f))
        }
    }
}
