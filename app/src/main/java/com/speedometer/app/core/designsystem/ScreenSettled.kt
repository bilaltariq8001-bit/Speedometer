package com.speedometer.app.core.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.first

/**
 * True once the screen's enter transition has finished. Heavy parts that the user does not need in the first 300 ms (native
 * ads, which inflate Android views and start a WebView, and maps) wait for it, so the slide or fade itself only has to draw
 * plain Compose content and stays at full frame rate. Outside a nav destination it is always true.
 */
val LocalScreenSettled = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(true) }

/** Tracks [scope]'s enter transition. Once settled it stays settled, so nothing disappears while the screen slides out. */
@Composable
fun rememberScreenSettled(scope: AnimatedVisibilityScope): State<Boolean> {
    val settled = remember { mutableStateOf(false) }
    val transition = scope.transition
    LaunchedEffect(transition) {
        snapshotFlow { transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible }
            .first { it }
        settled.value = true
    }
    return settled
}

/** Draws [content] only after the screen's enter transition has finished (see [LocalScreenSettled]). */
@Composable
fun AfterTransition(content: @Composable () -> Unit) {
    if (LocalScreenSettled.current.value) content()
}
