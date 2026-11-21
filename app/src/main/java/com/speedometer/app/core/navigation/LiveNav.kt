package com.speedometer.app.core.navigation

import androidx.navigation.NavHostController

/**
 * The navigation controller of the activity that is alive right now. A tab interstitial opens Trips, Insights or Settings
 * from its callback, which can arrive after the activity was rebuilt (a landscape ad can turn the screen, and a turn
 * rebuilds the activity). Navigating the old, destroyed controller then crashed the app. The tab goes to the live
 * controller instead, or waits here until the new one is ready.
 */
object LiveNav {
    private var controller: NavHostController? = null
    private var pending: String? = null

    fun attach(nav: NavHostController) { controller = nav }

    fun detach(nav: NavHostController) { if (controller === nav) controller = null }

    /** Opens [target] as a tab (Home stays below it). */
    fun openTab(target: String) {
        val nav = controller
        if (nav == null) { pending = target; return }
        val ok = runCatching { navigateTab(nav, target) }.isSuccess
        if (!ok) pending = target
    }

    /** A tab that could not open while the activity was being rebuilt; opened once the new screen host is ready. */
    fun takePending(): String? = pending.also { pending = null }

    fun navigateTab(nav: NavHostController, target: String) {
        nav.navigate(target) {
            popUpTo(Routes.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
