package smol_app.home

import AppState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import smol_access.SL
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppState.refreshButton(onRefresh: () -> Unit) {
    SmolTooltipArea(
        tooltip = { SmolTooltipText(text = "Refresh mod list.") },
        delayMillis = SmolTooltipArea.delay
    ) {
        val areModsLoading = SL.access.areModsLoading.collectAsState().value
        IconButton(
            onClick = { if (!areModsLoading) onRefresh.invoke() },
            modifier = Modifier.padding(start = 16.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition()
            val angle by infiniteTransition.animateFloat(
                initialValue = 0F,
                targetValue = 360F,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutLinearInEasing)
                )
            )

            Icon(
                painter = painterResource("refresh.svg"),
                modifier = Modifier
                    .graphicsLayer {
                        if (areModsLoading) {
                            rotationZ = angle
                        }
                    },
                contentDescription = "Refresh"
            )
        }
    }
}