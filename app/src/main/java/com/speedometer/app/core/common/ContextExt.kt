package com.speedometer.app.core.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity behind a (possibly wrapped) context, or null. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
