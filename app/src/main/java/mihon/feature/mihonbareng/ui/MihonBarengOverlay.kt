package mihon.feature.mihonbareng.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.feature.mihonbareng.MihonBarengManager
import mihon.feature.mihonbareng.MihonBarengPreferences
import mihon.feature.mihonbareng.model.ActiveDoodle
import mihon.feature.mihonbareng.model.BarengSessionState
import mihon.feature.mihonbareng.model.BarengSyncMode
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

private data class FloatingItem(
    val id: String,
    val emoji: String,
    val senderName: String,
    val startXRatio: Float,
)

private data class OverlayStroke(
    val id: String,
    val userName: String,
    val colorHex: String,
    val opacity: Float,
    val strokeWidth: Float,
    val points: List<Offset>,
    val endTimestamp: Long? = null,
)

private val DOODLE_COLORS = listOf(
    "#FF69B4" to "Pink",
    "#4FC3F7" to "Biru",
    "#BA68C8" to "Ungu",
    "#FFFFFF" to "Putih",
    "#FF5252" to "Merah",
    "#69F0AE" to "Hijau",
)

private fun parseColorHex(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = cleanHex.toLong(16)
        if (cleanHex.length == 6) {
            Color(colorInt or 0x00000000FF000000L)
        } else {
            Color(colorInt)
        }
    } catch (_: Exception) {
        Color(0xFFFF69B4)
    }
}

private fun parsePoints(pointsStr: String, widthPx: Float, heightPx: Float): List<Offset> {
    if (pointsStr.isBlank()) return emptyList()
    return pointsStr.split(';').mapNotNull { pair ->
        val parts = pair.split(',')
        if (parts.size == 2) {
            val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
            val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
            Offset(x * widthPx, y * heightPx)
        } else {
            null
        }
    }
}

private fun formatPoints(points: List<Offset>, widthPx: Float, heightPx: Float): String {
    val safeWidth = if (widthPx > 0f) widthPx else 1080f
    val safeHeight = if (heightPx > 0f) heightPx else 1920f
    return points.joinToString(";") {
        val rx = (it.x / safeWidth).coerceIn(0f, 1f)
        val ry = (it.y / safeHeight).coerceIn(0f, 1f)
        "${"%.3f".format(rx)},${"%.3f".format(ry)}"
    }
}

@Composable
fun MihonBarengOverlay(
    manager: MihonBarengManager,
    preferences: MihonBarengPreferences,
    currentPage: Int,
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionState by manager.sessionState.collectAsState()
    val activeSession = sessionState as? BarengSessionState.Active ?: return

    val remoteState by manager.remoteState.collectAsState()
    val remoteDoodle by manager.doodleFlow.collectAsState()
    val hostTouch by manager.hostTouchFlow.collectAsState()
    val isConnected by manager.isConnected.collectAsState()

    val floatingReactions = remember { mutableStateListOf<FloatingItem>() }
    var isDoodleModeActive by remember { mutableStateOf(false) }

    var selectedColorHex by remember { mutableStateOf(preferences.doodleColorHex.get()) }
    var selectedOpacity by remember { mutableFloatStateOf(preferences.doodleOpacity.get()) }
    var selectedStrokeWidth by remember { mutableFloatStateOf(preferences.doodleStrokeWidth.get()) }

    var localActiveStroke by remember { mutableStateOf<OverlayStroke?>(null) }
    var remoteActiveStroke by remember { mutableStateOf<OverlayStroke?>(null) }
    val fadingStrokes = remember { mutableStateListOf<OverlayStroke>() }

    var currentStrokeId by remember { mutableStateOf("") }
    var lastDoodleSendTime by remember { mutableLongStateOf(0L) }

    // Heartbeat ticker for fading strokes
    var animTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(33L)
            animTick = System.currentTimeMillis()
            val now = animTick
            val toRemove = fadingStrokes.filter { (now - (it.endTimestamp ?: now)) > 2500L }
            if (toRemove.isNotEmpty()) {
                fadingStrokes.removeAll(toRemove)
            }
        }
    }

    // Remote Doodle listener
    LaunchedEffect(remoteDoodle) {
        val doodle = remoteDoodle ?: return@LaunchedEffect
        if (doodle.active) {
            val pts = parsePoints(doodle.points, 1080f, 1920f)
            remoteActiveStroke = OverlayStroke(
                id = doodle.id,
                userName = doodle.userName,
                colorHex = doodle.colorHex,
                opacity = doodle.opacity,
                strokeWidth = doodle.strokeWidth,
                points = pts,
            )
        } else {
            remoteActiveStroke?.let { stroke ->
                fadingStrokes.add(stroke.copy(endTimestamp = System.currentTimeMillis()))
            }
            remoteActiveStroke = null
        }
    }

    // Reaction listener
    LaunchedEffect(Unit) {
        manager.reactionsFlow.collectLatest { reaction ->
            val item = FloatingItem(
                id = reaction.id,
                emoji = reaction.emoji,
                senderName = reaction.userName,
                startXRatio = 0.2f + Random.nextFloat() * 0.6f,
            )
            floatingReactions.add(item)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().takeIf { it > 0f } ?: 1080f
        val heightPx = constraints.maxHeight.toFloat().takeIf { it > 0f } ?: 1920f

        // 1. Doodle Touch Capture Layer (When Doodle Mode is Active)
        if (isDoodleModeActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedColorHex, selectedOpacity, selectedStrokeWidth) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val newId = UUID.randomUUID().toString()
                                currentStrokeId = newId
                                val pts = listOf(offset)
                                localActiveStroke = OverlayStroke(
                                    id = newId,
                                    userName = activeSession.currentUserName,
                                    colorHex = selectedColorHex,
                                    opacity = selectedOpacity,
                                    strokeWidth = selectedStrokeWidth,
                                    points = pts,
                                )
                                manager.sendDoodle(
                                    id = newId,
                                    colorHex = selectedColorHex,
                                    opacity = selectedOpacity,
                                    strokeWidth = selectedStrokeWidth,
                                    currentX = offset.x / widthPx,
                                    currentY = offset.y / heightPx,
                                    points = formatPoints(pts, widthPx, heightPx),
                                    active = true,
                                )
                            },
                            onDrag = { change, _ ->
                                val currentPt = change.position
                                val currentStroke = localActiveStroke
                                if (currentStroke != null) {
                                    val newPts = currentStroke.points + currentPt
                                    localActiveStroke = currentStroke.copy(points = newPts)

                                    val now = System.currentTimeMillis()
                                    if (now - lastDoodleSendTime > 40L) {
                                        lastDoodleSendTime = now
                                        manager.sendDoodle(
                                            id = currentStrokeId,
                                            colorHex = selectedColorHex,
                                            opacity = selectedOpacity,
                                            strokeWidth = selectedStrokeWidth,
                                            currentX = currentPt.x / widthPx,
                                            currentY = currentPt.y / heightPx,
                                            points = formatPoints(newPts, widthPx, heightPx),
                                            active = true,
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                localActiveStroke?.let { stroke ->
                                    val finished = stroke.copy(endTimestamp = System.currentTimeMillis())
                                    fadingStrokes.add(finished)
                                    manager.sendDoodle(
                                        id = stroke.id,
                                        colorHex = stroke.colorHex,
                                        opacity = stroke.opacity,
                                        strokeWidth = stroke.strokeWidth,
                                        currentX = 0f,
                                        currentY = 0f,
                                        points = formatPoints(stroke.points, widthPx, heightPx),
                                        active = false,
                                    )
                                }
                                localActiveStroke = null
                            },
                            onDragCancel = {
                                localActiveStroke?.let { stroke ->
                                    val finished = stroke.copy(endTimestamp = System.currentTimeMillis())
                                    fadingStrokes.add(finished)
                                    manager.sendDoodle(
                                        id = stroke.id,
                                        colorHex = stroke.colorHex,
                                        opacity = stroke.opacity,
                                        strokeWidth = stroke.strokeWidth,
                                        currentX = 0f,
                                        currentY = 0f,
                                        points = formatPoints(stroke.points, widthPx, heightPx),
                                        active = false,
                                    )
                                }
                                localActiveStroke = null
                            },
                        )
                    },
            )
        }

        // 2. Doodle Canvas Rendering Layer (Local + Remote + Fading)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = System.currentTimeMillis()
            val strokesToDraw = fadingStrokes + listOfNotNull(localActiveStroke, remoteActiveStroke)

            strokesToDraw.forEach { stroke ->
                val alpha = if (stroke.endTimestamp != null) {
                    val progress = (now - stroke.endTimestamp).toFloat() / 2500f
                    (stroke.opacity * (1f - progress)).coerceIn(0f, 1f)
                } else {
                    stroke.opacity
                }

                if (alpha > 0.01f && stroke.points.isNotEmpty()) {
                    val color = parseColorHex(stroke.colorHex).copy(alpha = alpha)

                    if (stroke.points.size == 1) {
                        drawCircle(
                            color = color,
                            radius = stroke.strokeWidth * 1.5f,
                            center = stroke.points[0],
                        )
                    } else {
                        val path = Path().apply {
                            moveTo(stroke.points[0].x, stroke.points[0].y)
                            for (i in 1 until stroke.points.size) {
                                val prev = stroke.points[i - 1]
                                val curr = stroke.points[i]
                                val midX = (prev.x + curr.x) / 2f
                                val midY = (prev.y + curr.y) / 2f
                                quadraticBezierTo(prev.x, prev.y, midX, midY)
                            }
                            lineTo(stroke.points.last().x, stroke.points.last().y)
                        }

                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = stroke.strokeWidth.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }

        // 3. Doodle Active Name Tags
        listOfNotNull(localActiveStroke, remoteActiveStroke).forEach { stroke ->
            val latestPt = stroke.points.lastOrNull()
            if (latestPt != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = parseColorHex(stroke.colorHex).copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (latestPt.x - 40f).roundToInt().coerceIn(10, (widthPx - 160).roundToInt()),
                                y = (latestPt.y - 80f).roundToInt().coerceIn(10, (heightPx - 80).roundToInt()),
                            )
                        },
                ) {
                    Text(
                        text = "${stroke.userName} ✏️",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // 4. Host Touch Indicator (On Viewer Screen)
        if (!activeSession.isHost) {
            hostTouch?.let { touch ->
                if (touch.active) {
                    val touchX = touch.x * widthPx
                    val touchY = touch.y * heightPx

                    val transition = rememberInfiniteTransition(label = "HostTouchRipple")
                    val rippleAlpha by transition.animateFloat(
                        initialValue = 0.7f,
                        targetValue = 0.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = EaseOut),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "rippleAlpha",
                    )
                    val rippleRadius by transition.animateFloat(
                        initialValue = 10f,
                        targetValue = 28f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = EaseOut),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "rippleRadius",
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color(0xFF2196F3).copy(alpha = rippleAlpha),
                            radius = rippleRadius.dp.toPx(),
                            center = Offset(touchX, touchY),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                        drawCircle(
                            color = Color(0xFF2196F3).copy(alpha = 0.35f),
                            radius = 16.dp.toPx(),
                            center = Offset(touchX, touchY),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(touchX, touchY),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E88E5).copy(alpha = 0.92f),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (touchX + 16f).roundToInt().coerceIn(10, (widthPx - 140).roundToInt()),
                                    y = (touchY - 32f).roundToInt().coerceIn(10, (heightPx - 60).roundToInt()),
                                )
                            },
                    ) {
                        Text(
                            text = "${touch.userName} 👆",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // 5. Disconnect Alert Banner
        if (!isConnected) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .clickable { manager.reconnect() },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "⚠️ Koneksi terputus...",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "Sambung Lagi ↻",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // 6. Floating Reactions
        floatingReactions.forEach { item ->
            FloatingReactionItem(
                item = item,
                onFinished = { floatingReactions.remove(item) },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        // 7. Loose Sync Host Indicator (When host is on another page)
        if (activeSession.roomInfo.mode == BarengSyncMode.LOOSE && !activeSession.isHost) {
            val hostPage = remoteState?.pageIndex ?: 0
            if (hostPage != currentPage) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp)
                        .clickable { onJumpToPage(hostPage) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Host di Halaman ${hostPage + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lompat ke Host ➔",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // 8. Doodle Tool Palette (Shown when Doodle Mode is Active)
        AnimatedVisibility(
            visible = isDoodleModeActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Mode Doodle (Coretan Sementara)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Color Palette
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DOODLE_COLORS.forEach { (hex, _) ->
                            val color = parseColorHex(hex)
                            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable {
                                        selectedColorHex = hex
                                        preferences.doodleColorHex.set(hex)
                                    },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Width & Opacity Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Widths
                        listOf(4f to "Halus", 7f to "Sedang", 12f to "Tebal").forEach { (w, label) ->
                            val isSelected = selectedStrokeWidth == w
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.clickable {
                                    selectedStrokeWidth = w
                                    preferences.doodleStrokeWidth.set(w)
                                },
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Clear Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.clickable {
                                fadingStrokes.clear()
                                localActiveStroke = null
                            },
                        ) {
                            Text(
                                text = "Hapus",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        // Close Doodle Mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { isDoodleModeActive = false },
                        ) {
                            Text(
                                text = "Tutup",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        // 9. Quick Reaction & Doodle Floating Action Bar
        QuickReactionFloatingBar(
            onSendReaction = { manager.sendReaction(it) },
            isDoodleActive = isDoodleModeActive,
            onToggleDoodle = { isDoodleModeActive = !isDoodleModeActive },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 84.dp),
        )
    }
}

@Composable
private fun FloatingReactionItem(
    item: FloatingItem,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            offsetY.animateTo(
                targetValue = -700f,
                animationSpec = tween(durationMillis = 2200, easing = EaseOut),
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 2200, delayMillis = 400),
            )
            onFinished()
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(x = (item.startXRatio * 700).roundToInt(), y = offsetY.value.roundToInt()) }
            .alpha(alpha.value),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = item.emoji,
                fontSize = 32.sp,
            )
            Text(
                text = item.senderName,
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun QuickReactionFloatingBar(
    onSendReaction: (String) -> Unit,
    isDoodleActive: Boolean,
    onToggleDoodle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val emojis = listOf("❤️", "😂", "😮", "🔥", "👏")

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (expanded) {
                emojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onSendReaction(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }

                // Temporary Doodle Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDoodleActive) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onToggleDoodle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✏️",
                        fontSize = 18.sp,
                    )
                }
            }

            // Expand / Collapse pill trigger
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (expanded) "✕" else "🎉",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
