package com.speedometer.app.core.ads

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Lets Compose screens reach the [AdsController]. */
@HiltViewModel
class AdsViewModel @Inject constructor(val ads: AdsController) : ViewModel()
