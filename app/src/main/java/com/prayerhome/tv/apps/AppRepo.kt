package com.prayerhome.tv.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppEntry(
    val pkg: String,
    val label: String,
    /** The app's own 16:9 TV banner, when it ships one. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val launch: Intent,
)

/** Installed TV apps with their real banners/icons, refreshed when apps are added or removed. */
class AppRepo(private val context: Context) {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        reload()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = reload()
        }, filter)
    }

    fun reload() {
        scope.launch { _apps.value = query() }
    }

    private fun query(): List<AppEntry> {
        val pm = context.packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (category in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(q, PackageManager.MATCH_ALL)) {
                val ai = ri.activityInfo
                if (ai.packageName == context.packageName || ai.packageName in HIDDEN || !seen.add(ai.packageName)) continue
                val banner = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull()
                val icon = runCatching { ai.loadIcon(pm) }.getOrNull()
                val launch = Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                out += AppEntry(
                    pkg = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    banner = banner?.let { render(it, 320, 180) },
                    icon = icon?.let { render(it, 128, 128) },
                    launch = launch,
                )
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    private fun render(d: Drawable, w: Int, h: Int): ImageBitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        return b.asImageBitmap()
    }

    companion object {
        // system plumbing that shouldn't clutter the apps page
        private val HIDDEN = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvrecommendations",
            "com.tcl.tv.tclhome_passive",
        )
    }
}
