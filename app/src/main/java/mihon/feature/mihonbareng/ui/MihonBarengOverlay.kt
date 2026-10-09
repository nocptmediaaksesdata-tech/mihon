package mihon.feature.mihonbareng.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.feature.mihonbareng.MihonBarengManager
import mihon.feature.mihonbareng.model.BarengSessionState
import mihon.feature.mihonbareng.model.BarengSyncMode
import kotlin.math.roundToInt
import kotlin.random.Random

private data class FloatingItem(
    val id: String,
    val emoji: String,
    val senderName: String,
    val startXRatio: Float,
)

@Composable
fun MihonBarengOverlay(
    manager: MihonBarengManager,
    currentPage: Int,
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionState by manager.sessionState.collectAsState()
    val activeSession = sessionState as? BarengSessionState.Active ?: return

    val remoteState by manager.remoteState.collectAsState()
    val pointer by manager.pointerFlow.collectAsState()

    val floatingReactions = remember { mutableStateListOf<FloatingItem>() }
    var isPointerModeActive by remember { mutableStateOf(false) }

    // Listen to reactions
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
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // 1. Pointer Touch Capture Layer (When Pointer Mode is active)
        if (isPointerModeActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                manager.sendPointer(
                                    x = (offset.x / widthPx).coerceIn(0f, 1f),
                                    y = (offset.y / heightPx).coerceIn(0f, 1f),
                                    active = true,
                                )
                            },
                            onDrag = { change, _ ->
                                manager.sendPointer(
                                    x = (change.position.x / widthPx).coerceIn(0f, 1f),
                                    y = (change.position.y / heightPx).coerceIn(0f, 1f),
                                    active = true,
                                )
                            },
                            onDragEnd = {
                                manager.sendPointer(0f, 0f, active = false)
                            },
                            onDragCancel = {
                                manager.sendPointer(0f, 0f, active = false)
                            },
                        )
                    },
            )
        }

        // 2. Remote Pointer Display Layer
        pointer?.let { p ->
            if (p.active) {
                val targetX = p.x * widthPx
                val targetY = p.y * heightPx

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Outer glow ripple
                    drawCircle(
                        color = Color.Red.copy(alpha = 0.35f),
                        radius = 28.dp.toPx(),
                        center = Offset(targetX, targetY),
                    )
                    drawCircle(
                        color = Color.Red.copy(alpha = 0.7f),
                        radius = 18.dp.toPx(),
                        center = Offset(targetX, targetY),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                    // Inner bright laser dot
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = Offset(targetX, targetY),
                    )
                    drawCircle(
                        color = Color.Red,
                        radius = 5.dp.toPx(),
                        center = Offset(targetX, targetY),
                    )
                }
            }
        }

        // 3. Floating Reactions Layer
        floatingReactions.forEach { item ->
            FloatingReactionItem(
                item = item,
                onFinished = { floatingReactions.remove(item) },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        // 4. Loose Sync Host Indicator (When host is on another page)
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

        // 5. Quick Reaction & Pointer Floating Action Bar
        QuickReactionFloatingBar(
            onSendReaction = { manager.sendReaction(it) },
            isPointerActive = isPointerModeActive,
            onTogglePointer = { isPointerModeActive = !isPointerModeActive },
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
    isPointerActive: Boolean,
    onTogglePointer: () -> Unit,
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

                // Laser Pointer button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isPointerActive) Color.Red else Color.Transparent)
                        .clickable { onTogglePointer() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "🎯",
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
