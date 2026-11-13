package com.speedometer.app.core.ads

import android.app.Activity
import androidx.annotation.MainThread
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.speedometer.app.core.common.isNetworkConnected

/**
 * Asks UMP for the consent state and shows the consent form when it is required. [onScreenShown] runs exactly once
 * on every path (offline, error, form shown or not) with (consentFormWasShown, canRequestAds). Persist `canRequestAds`
 * and check it before every ad request.
 */
@MainThread
fun Activity.showGDPRScreen(onScreenShown: (Boolean, Boolean) -> Unit) {
    val consentInfo = UserMessagingPlatform.getConsentInformation(this)
    // Offline (or on an error) keep the answer the user already gave. Reporting "false" here used to switch ads off
    // until the next online cold start, so ads looked slow or missing after any offline launch.
    if (!isNetworkConnected()) {
        onScreenShown(false, consentInfo.canRequestAds())
        return
    }
    val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
    consentInfo.requestConsentInfoUpdate(
        this,
        params,
        {
            if (consentInfo.isConsentFormAvailable) {
                UserMessagingPlatform.loadConsentForm(
                    this,
                    { form ->
                        if (consentInfo.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                            form.show(this) { onScreenShown(true, consentInfo.canRequestAds()) }
                        } else {
                            onScreenShown(false, consentInfo.canRequestAds())
                        }
                    },
                    { onScreenShown(false, consentInfo.canRequestAds()) },
                )
            } else {
                onScreenShown(false, consentInfo.canRequestAds())
            }
        },
        { onScreenShown(false, consentInfo.canRequestAds()) },
    )
}
