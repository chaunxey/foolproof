package com.foolproof.base.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** 从 Compose 的 LocalContext（可能是 ContextWrapper）里找到宿主 Activity。 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
