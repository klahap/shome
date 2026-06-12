package de.quati.shome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.quati.shome.model.PositionPercent

@Composable
fun PositionPicker(
    stepSize: Int = 5,
    selectedPosition: PositionPercent,
    onSelectedPositionChange: (PositionPercent) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: Float = 2f,
    itemHeightDp: Dp = 48.dp,
) {
    fun PositionPercent.toIndex() = value / stepSize
    fun Int.toPositionPercent() = PositionPercent((this * stepSize).coerceIn(0..100))

    val itemHeightPx = with(LocalDensity.current) { itemHeightDp.toPx() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedPosition.toIndex(),
    )
    val centerOffset = (visibleItems / 2)
    val verticalPadding = itemHeightDp * visibleItems / 2 - itemHeightDp / 2

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            // snap to nearest item when scroll ends
            val rawOffset = listState.firstVisibleItemScrollOffset
            val idx = listState.firstVisibleItemIndex
            val snapped = if (rawOffset > itemHeightPx / 2) idx + 1 else idx
            val target = snapped.coerceIn(0, PositionPercent(100).toIndex())
            listState.animateScrollToItem(target)
            onSelectedPositionChange(target.toPositionPercent())
        }
    }

    // keep list in sync when value changes externally
    LaunchedEffect(selectedPosition) {
        if (listState.firstVisibleItemIndex != selectedPosition.toIndex()) {
            listState.animateScrollToItem(selectedPosition.toIndex())
        }
    }

    Box(
        modifier = modifier
            .height(itemHeightDp * visibleItems)
            .width(80.dp)
            .clipToBounds(),
    ) {
        // selection highlight band
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeightDp)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                )
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = verticalPadding),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items((PositionPercent(0).toIndex()..PositionPercent(100).toIndex()).toList()) { item ->
                val isSelected = item == selectedPosition.toIndex()
                Box(
                    modifier = Modifier
                        .height(itemHeightDp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.toPositionPercent().toString(),
                        fontSize = if (isSelected) 28.sp else 22.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
            }
        }

        // top + bottom fade overlays
        listOf(Alignment.TopCenter, Alignment.BottomCenter).forEach { alignment ->
            Box(
                modifier = Modifier
                    .align(alignment)
                    .fillMaxWidth()
                    .height(itemHeightDp * centerOffset)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (alignment == Alignment.TopCenter)
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    Color.Transparent,
                                )
                            else
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface,
                                ),
                        )
                    )
            )
        }
    }
}
