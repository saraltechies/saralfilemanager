package com.saraltechies.saralfilemanager.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.saraltechies.saralfilemanager.MainActivity
import com.saraltechies.saralfilemanager.R

/**
 * Long-press launcher shortcuts: Free up space, Downloads, Images and Search. Published at runtime so they also work for the
 * ".debug" package.
 */
object AppShortcuts {

    const val ID_DOWNLOADS = "downloads"
    const val ID_IMAGES = "images"
    const val ID_SEARCH = "search"
    const val ID_CLEAN = "clean"

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, ID_CLEAN, R.string.shortcut_clean, R.drawable.ic_shortcut_clean),
            shortcut(context, ID_DOWNLOADS, R.string.shortcut_downloads, R.drawable.ic_shortcut_download),
            shortcut(context, ID_IMAGES, R.string.shortcut_images, R.drawable.ic_shortcut_image),
            shortcut(context, ID_SEARCH, R.string.shortcut_search, R.drawable.ic_shortcut_search),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, id: String, label: Int, icon: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_SHORTCUT)
                    .putExtra(MainActivity.EXTRA_SHORTCUT, id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            .build()
}
