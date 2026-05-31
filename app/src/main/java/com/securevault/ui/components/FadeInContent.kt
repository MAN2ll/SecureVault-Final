package com.securevault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FadeInContent(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = delayMillis)) +
                slideInVertically(animationSpec = tween(durationMillis = 300, delayMillis = delayMillis)) {
                    it / 4
                },
        content = { content() }
    )
}

@Composable
fun StaggeredFadeIn(
    items: List<@Composable () -> Unit>,
    staggerDelay: Int = 100
) {
    items.forEachIndexed { index, item ->
        FadeInContent(
            visible = true,
            delayMillis = index * staggerDelay,
            content = item
        )
    }
}
