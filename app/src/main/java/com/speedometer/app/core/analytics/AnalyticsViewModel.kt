package com.speedometer.app.core.analytics

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Lets Compose screens reach [Analytics]. */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(val analytics: Analytics) : ViewModel()
