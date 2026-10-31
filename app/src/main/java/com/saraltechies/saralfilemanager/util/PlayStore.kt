package com.saraltechies.saralfilemanager.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play links and the in-app review sheet. Both go through the Play Store app on the phone, so
 * SaralFileManager still needs no internet permission.
 */
object PlayStore {

    const val SARALQRSCAN = "com.saraltechies.saralqrscan"
    const val SARAL_AUTHENTICATOR = "com.saraltechies.saralauthenticator"
    const val SARALREADER = "com.saraltechies.saralreader"
    const val WEBSITE = "https://saraltechies.com/"
    const val SOURCE_CODE = "https://github.com/saraltechies/saralfilemanager"
    const val PRIVACY_POLICY = "https://saraltechies.com/saralfilemanager/privacy-policy"

    /** This app's own listing, also from the ".debug" build. */
    fun ownPackage(context: Context): String = context.packageName.removeSuffix(".debug")

    /** Opens an app's Play listing in the Play Store app, or in the browser when Play isn't installed. */
    fun openListing(context: Context, packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
            .setPackage("com.android.vending")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    /** Opens a web page in the person's own browser (SaralFileManager itself has no internet access). */
    fun openUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Shows Play's rating sheet over the app. Play decides whether it actually appears (it has its own quota)
     * and never reports whether the person rated, so callers just record that they asked.
     */
    suspend fun requestReview(activity: Activity) {
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.launchReview(activity, manager.requestReview())
        }
    }
}
