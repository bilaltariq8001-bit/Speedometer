package com.speedometer.app.core.designsystem

import android.provider.Settings
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * A looping Lottie animation from res/raw. It is decoration, so it is hidden from screen readers. It plays only while
 * the screen is resumed, and stays on its first frame when the user turned animations off in the phone settings.
 */
@Composable
fun LottieAnim(@RawRes raw: Int, modifier: Modifier = Modifier, iterations: Int = LottieConstants.IterateForever) {
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(raw))
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    val progress by animateLottieCompositionAsState(
        composition, iterations = iterations, isPlaying = resumed && !reduceMotion, restartOnPlay = false,
    )
    LottieAnimation(composition, { if (reduceMotion) 0f else progress }, modifier.clearAndSetSemantics { })
}
