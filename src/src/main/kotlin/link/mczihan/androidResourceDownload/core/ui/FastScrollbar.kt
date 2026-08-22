package link.mczihan.androidResourceDownload.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 快速滚动滑块：鼠标悬停时变深变粗，可拖拽滚动。
 * 列表可滚动时始终显示，项目过少自动隐藏。
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(-1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val canScroll by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }
    val visible = itemCount > 0 && canScroll

    // 直接计算，itemCount 变化时触发重组（解决任务逐个添加时滑块不更新的问题）
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewportH = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    val avgH = if (visibleItems.isNotEmpty()) {
        visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
    } else viewportH.coerceAtLeast(1f)
    val fullyVisible = (viewportH / avgH).coerceAtLeast(1f)
    val totalScroll = ((itemCount - fullyVisible) * avgH).coerceAtLeast(1f)

    val scrollProgress = when {
        !visible -> 0f
        !listState.canScrollForward && listState.canScrollBackward -> 1f
        !listState.canScrollBackward && listState.canScrollForward -> 0f
        else -> {
            val current = listState.firstVisibleItemIndex * avgH + listState.firstVisibleItemScrollOffset
            (current / totalScroll).coerceIn(0f, 1f)
        }
    }

    val thumbHeightPercent = if (itemCount > 0) {
        (fullyVisible / itemCount).coerceIn(0.15f, 0.75f)
    } else 0f

    // rememberUpdatedState：pointerInput 不重启也能读到最新值
    val latestThumbPct by rememberUpdatedState(thumbHeightPercent)
    val latestItemCount by rememberUpdatedState(itemCount)
    val latestAvgH by rememberUpdatedState(avgH)
    val latestTotalScroll by rememberUpdatedState(totalScroll)

    if (visible) {
        val displayProgress = if (dragProgress >= 0f) dragProgress else scrollProgress
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(10.dp)
                .padding(end = 2.dp, top = 4.dp, bottom = 4.dp)
                .hoverable(interactionSource),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            try {
                                val trackH = size.height
                                // 从 rememberUpdatedState 读取最新值（每次手势开始时都是最新的）
                                val thumbH = trackH * latestThumbPct
                                val movableRange = (trackH - thumbH).coerceAtLeast(1f)
                                val currentTotal = latestItemCount
                                val dragAvgH = latestAvgH
                                val dragTotal = latestTotalScroll

                                fun scrollToY(y: Float) {
                                    val ratio = ((y - thumbH / 2) / movableRange).coerceIn(0f, 1f)
                                    dragProgress = ratio
                                    val targetPixels = ratio * dragTotal
                                    val index = (targetPixels / dragAvgH).toInt().coerceIn(0, currentTotal - 1)
                                    val offset = (targetPixels - index * dragAvgH).toInt()
                                        .coerceIn(0, dragAvgH.toInt().coerceAtLeast(1) - 1)
                                    scope.launch { listState.scrollToItem(index, offset) }
                                }

                                scrollToY(down.position.y)
                                down.consume()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (event.type == PointerEventType.Release || !change.pressed) break
                                    scrollToY(change.position.y)
                                    change.consume()
                                }
                            } finally {
                                isDragging = false
                                dragProgress = -1f
                            }
                        }
                    },
            )
            ScrollbarThumb(
                thumbHeightPercent = thumbHeightPercent,
                scrollProgress = displayProgress,
                active = isHovered || isDragging,
            )
        }
    }
}

/**
 * 基于 ScrollState 的快速滚动滑块（用于 Column + verticalScroll 等场景）。
 */
@Composable
fun FastScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val canScroll = scrollState.maxValue > 0
    val visible = canScroll

    val scrollProgress by remember {
        derivedStateOf {
            if (scrollState.maxValue <= 0) 0f
            else (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
        }
    }

    val thumbHeightPercent by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) {
                (1f - scrollState.maxValue.toFloat() / (scrollState.maxValue + 2000f)).coerceIn(0.15f, 0.75f)
            } else 0f
        }
    }

    if (visible) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(10.dp)
                .padding(end = 2.dp, top = 4.dp, bottom = 4.dp)
                .hoverable(interactionSource),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(thumbHeightPercent) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            try {
                                val trackH = size.height
                                val thumbH = trackH * thumbHeightPercent
                                val movableRange = (trackH - thumbH).coerceAtLeast(1f)

                                fun scrollToY(y: Float) {
                                    val ratio = ((y - thumbH / 2) / movableRange).coerceIn(0f, 1f)
                                    val target = (ratio * scrollState.maxValue).toInt().coerceIn(0, scrollState.maxValue)
                                    scope.launch { scrollState.scrollTo(target) }
                                }

                                scrollToY(down.position.y)
                                down.consume()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (event.type == PointerEventType.Release || !change.pressed) break
                                    scrollToY(change.position.y)
                                    change.consume()
                                }
                            } finally {
                                isDragging = false
                            }
                        }
                    },
            )
            ScrollbarThumb(
                thumbHeightPercent = thumbHeightPercent,
                scrollProgress = scrollProgress,
                active = isHovered || isDragging,
            )
        }
    }
}

@Composable
private fun BoxScope.ScrollbarThumb(
    thumbHeightPercent: Float,
    scrollProgress: Float,
    active: Boolean,
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .fillMaxHeight(thumbHeightPercent)
            .graphicsLayer {
                // this.size.height 是 thumb 自身高度 = 父容器高度 * thumbHeightPercent
                // 需要除以 thumbHeightPercent 得到父容器实际高度，再乘可移动比例
                val parentHeight = this.size.height / thumbHeightPercent
                translationY = parentHeight * scrollProgress * (1f - thumbHeightPercent)
            }
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(if (active) 8.dp else 4.dp)
                .clip(MaterialTheme.shapes.small)
                .graphicsLayer {
                    alpha = if (active) 0.7f else 0.35f
                }
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}
