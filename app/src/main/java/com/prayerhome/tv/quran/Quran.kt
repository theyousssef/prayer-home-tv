package com.prayerhome.tv.quran

import android.content.Context
import android.content.Intent

/** The separate Quran Reader app (com.prayerhome.quran): the Quran button just opens it. */
object Quran {
    const val PKG = "com.prayerhome.quran"

    fun launchIntent(context: Context): Intent? {
        val pm = context.packageManager
        return (pm.getLeanbackLaunchIntentForPackage(PKG) ?: pm.getLaunchIntentForPackage(PKG))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}
