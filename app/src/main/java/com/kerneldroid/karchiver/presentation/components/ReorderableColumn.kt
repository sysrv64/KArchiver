package com.kerneldroid.karchiver.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val DragSlop = 6.dp

@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    itemKey: (T) -> Any,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    itemHeight: Dp,
    itemSpacing: Dp,
    modifier: Modifier = Modifier,
    onDragMoveStarted: () -> Unit = {},
    dragHandle: @Composable () -> Unit = { Box(Modifier.size(width = 48.dp, height = 56.dp)) },
    content: @Composable ColumnScope.(item: T, index: Int, isDragging: Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val stepPx = with(density) { (itemHeight + itemSpacing).toPx() }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragMoveStarted by rememberUpdatedState(onDragMoveStarted)

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var moved by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    val settleOffset = remember { Animatable(0f) }

    val targetIndex = when {
        draggingIndex < 0 || !moved -> draggingIndex
        else -> (draggingIndex + (dragOffset / stepPx).roundToInt()).coerceIn(0, items.lastIndex.coerceAtLeast(0))
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
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                label = "reorderShift"
            )
            val scale by animateFloatAsState(
                targetValue = if (isDragging) 1.03f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "reorderScale"
            )
            val elevation by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "reorderElevation"
            )
            val translation = when {
                isDragging && settling -> settleOffset.value
                isDragging -> dragOffset
                else -> animatedShift
            }

            key(itemKey(item)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 2f else 0f)
                        .shadow(elevation, RoundedCornerShape(30.dp), clip = false)
                        .background(
                            color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                            shape = RoundedCornerShape(30.dp)
                        )
                        .graphicsLayer {
                            translationY = translation
                            scaleX = scale
                            scaleY = scale
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        content(item, index, isDragging)
                    }
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .pointerInput(index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    settling = false
                                    draggingIndex = index
                                    dragOffset = 0f
                                    moved = false
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    if (!moved && kotlin.math.abs(dragOffset) > with(density) { DragSlop.toPx() }) {
                                        moved = true
                                        currentOnDragMoveStarted()
                                    }
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    val to = if (moved && from >= 0) {
                                        (from + (dragOffset / stepPx).roundToInt()).coerceIn(0, items.lastIndex.coerceAtLeast(0))
                                    } else from

                                    if (from >= 0 && to != from) {
                                        settling = true
                                        val start = dragOffset
                                        val target = (to - from) * stepPx
                                        scope.launch {
                                            settleOffset.snapTo(start)
                                            settleOffset.animateTo(
                                                targetValue = target,
                                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
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
                                    }
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                    moved = false
                                    settling = false
                                }
                            )
                        }
                    ) {
                        dragHandle()
                    }
                }
            }
        }
    }
}
