package com.speedometer.app.core.ads

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.core.common.findActivity

/**
 * One Back path for a screen. Returns the lambda for the screen's arrow; the system Back and the gesture run the same
 * code through a [BackHandler]. The interstitial for [placement] shows first, and [leave] (the navigation) runs only in
 * its dismiss callback, once. A second Back while the ad is on its way is consumed.
 *
 * @param placement Remote Config prefix of this screen's back ad, for example `gauge_style_back`.
 * @param onInternalBack return true when the press only closed something inside the screen: no ad, no navigation.
 */
@Composable
fun rememberBackPressAd(
    placement: String,
    enabled: Boolean = true,
    onInternalBack: () -> Boolean = { false },
    leave: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val ads = hiltViewModel<AdsViewModel>().ads
    val internalBack by rememberUpdatedState(onInternalBack)
    val currentLeave by rememberUpdatedState(leave)

    val onBackPress: () -> Unit = remember(placement, ads, context) {
        {
            if (!internalBack()) {
                val activity = context.findActivity()
                if (activity == null) currentLeave() else ads.showBackAd(activity, placement) { currentLeave() }
            }
        }
    }
    BackHandler(enabled = enabled, onBack = onBackPress)
    return onBackPress
}
