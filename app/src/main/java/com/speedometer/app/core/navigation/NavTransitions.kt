package com.speedometer.app.core.navigation

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Screen transitions for the whole nav host: a clean cross-fade, and a slide from the left for Trips, Insights and Settings. No zoom, no scaling and no
 * size change, so a screen keeps exactly its size and place the whole time.
 *
 * Going forward, the new screen fades in on top of the old one, which stays perfectly still underneath and disappears when the
 * new one is fully visible. Going back is the mirror: the screen being left fades out on top of the one underneath, which
 * does not move. Because one screen is always still and the other only changes its opacity, there is no moment where both
 * are half transparent (no dip to the background), and nothing is ever resized.
 *
 * It is short (under 250 ms) and switches off completely when the phone's animations are turned off.
 */
class NavTransitions(private val reduceMotion: Boolean) {
    private val fadeMs = 220
    private val slideMs = 280
    private val easing = FastOutSlowInEasing

    private val secondary = setOf(Routes.History, Routes.Insights, Routes.Settings)

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.homeToSecondary() =
        initialState.destination.route == Routes.Home && targetState.destination.route in secondary

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.secondaryToHome() =
        initialState.destination.route in secondary && targetState.destination.route == Routes.Home

    fun enter(s: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        reduceMotion -> EnterTransition.None
        // Trips, Insights and Settings come in from the left edge, moving to the right, over a Home that stays still.
        s.homeToSecondary() -> slideInHorizontally(tween(slideMs, easing = easing)) { -it }
        else -> fadeIn(tween(fadeMs, easing = easing))
    }

    @Suppress("UNUSED_PARAMETER")
    fun exit(s: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = ExitTransition.None

    @Suppress("UNUSED_PARAMETER")
    fun popEnter(s: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = EnterTransition.None

    fun popExit(s: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        reduceMotion -> ExitTransition.None
        // Going back, the screen slides out to the left, the way it came in.
        s.secondaryToHome() -> slideOutHorizontally(tween(slideMs, easing = easing)) { -it }
        else -> fadeOut(tween(fadeMs, easing = easing))
    }

    companion object {
        /** True when the user turned animations off (the "Animator duration scale" developer or accessibility option). */
        fun reduceMotion(context: Context): Boolean = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
