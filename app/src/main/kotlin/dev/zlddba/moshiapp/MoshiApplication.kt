package dev.zlddba.moshiapp

import android.app.Application
import ly.count.android.sdk.Countly
import ly.count.android.sdk.CountlyConfig

class MoshiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.COUNTLY_APP_KEY.isEmpty()) return
        val config = CountlyConfig(
            this,
            BuildConfig.COUNTLY_APP_KEY,
            "http://www.linkzdsada.dpdns.org:9401",
        ).enableAutomaticViewTracking()
        Countly.sharedInstance().init(config)
    }
}
