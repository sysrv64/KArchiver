package com.kerneldroid.karchiver.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

const val HoldToMenuMillis = 1600L

private val DragSlop = 6.dp

@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    itemKey: (T) -> Any,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onHoldStill: (item: T) -> Unit,
    itemHeight: Dp,
    itemSpacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(item: T, index: Int, isDragging: Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val stepPx = with(density) { (itemHeight + itemSpacing).toPx() }
    val slopPx = with(density) { DragSlop.toPx() }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnHold by rememberUpdatedState(onHoldStill)

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var moved by remember { mutableStateOf(false) }
    var holdFired by remember { mutableStateOf(false) }
    var session by remember { mutableIntStateOf(0) }
    var settling by remember { mutableStateOf(false) }
    val settleOffset = remember { Animatable(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val targetIndex = when {
        draggingIndex < 0 || !moved -> draggingIndex
        else -> (draggingIndex + (dragOffset / stepPx).roundToInt())
            .coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(session) {
        if (session == 0) return@LaunchedEffect
        delay(HoldToMenuMillis)
        if (draggingIndex >= 0 && !moved && !settling) {
            val held = items.getOrNull(draggingIndex)
            holdFired = true
            draggingIndex = -1
            dragOffset = 0f
            if (held != null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnHold(held)
            }
        }
    }

    Column(modifier) {
        items.forEachIndexed { index, item ->
            val isDragging = index == draggingIndex
            val shift = when {
                draggingIndex < 0 || isDragging -> 0f
                draggingIndex < targetIndex && index in (draggingIndex + 1)..targetIndex -> -stepPx
                draggingIndex > targetIndex && index in targetIndex until draggingIndex -> stepPx
                else -> 0f
            }
            val animatedShift by animateFloatAsState(
                targetValue = shift,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
                label = "reorderShift"
            )
            val scale by animateFloatAsState(
                targetValue = if (isDragging) 1.04f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "reorderScale"
            )
            val elevation by animateDpAsState(
                targetValue = if (isDragging) 12.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "reorderElevation"
            )
            val translation = when {
                isDragging && settling -> settleOffset.value
                isDragging -> dragOffset
                else -> animatedShift
            }

            key(itemKey(item)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .shadow(elevation, RoundedCornerShape(30.dp), clip = isDragging)
                        .background(
                            color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                            else Color.Transparent,
                            shape = RoundedCornerShape(30.dp)
                        )
                        .graphicsLayer {
                            translationY = translation
                            scaleX = scale
                            scaleY = scale
                        }
                        .pointerInput(itemKey(item), items.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    settleJob?.cancel()
                                    settling = false
                                    draggingIndex = index
                                    dragOffset = 0f
                                    moved = false
                                    holdFired = false
                                    session++
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (!holdFired) {
                                        dragOffset += amount.y
                                        if (abs(dragOffset) > slopPx) moved = true
                                    }
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    val to = if (!holdFired && moved && from >= 0) {
                                        (from + (dragOffset / stepPx).roundToInt())
                                            .coerceIn(0, (items.size - 1).coerceAtLeast(0))
                                    } else {
                                        from
                                    }
                                    if (!holdFired && from >= 0 && to != from) {
                                        settling = true
                                        val start = dragOffset
                                        val target = (to - from) * stepPx
                                        settleJob = scope.launch {
                                            settleOffset.snapTo(start)
                                            settleOffset.animateTo(
                                                targetValue = target,
                                                animationSpec = spring(
                                                    dampingRatio = 0.8f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                            currentOnMove(from, to)
                                            draggingIndex = -1
                                            dragOffset = 0f
                                            moved = false
                                            settling = false
                                            settleOffset.snapTo(0f)
                                        }
                                    } else {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                        moved = false
                                        holdFired = false
                                    }
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                    moved = false
                                    holdFired = false
                                    settling = false
                                }
                            )
                        }
                ) {
                    content(item, index, isDragging)
                }
            }
        }
    }
}

fun Modifier.holdToReveal(durationMillis: Long = HoldToMenuMillis, onHold: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onHold)
    pointerInput(durationMillis) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val held = try {
                withTimeout(durationMillis) { waitForUpOrCancellation() }
                false
            } catch (_: TimeoutCancellationException) {
                true
            }
            if (held) current()
        }
    }
}
