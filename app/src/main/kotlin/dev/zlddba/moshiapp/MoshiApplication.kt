package dev.zlddba.moshiapp

import android.app.Application
import dev.zlddba.moshiapp.data.repo.HelpSeeder
import dev.zlddba.moshiapp.domain.index.IndexOrchestrator
import ly.count.android.sdk.Countly
import ly.count.android.sdk.CountlyConfig

class MoshiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        IndexOrchestrator.start(this)
        HelpSeeder.start(this)
        if (BuildConfig.COUNTLY_APP_KEY.isEmpty()) return
        val config = CountlyConfig(
            this,
            BuildConfig.COUNTLY_APP_KEY,
            "http://www.linkzdsada.dpdns.org:9401",
        ).enableAutomaticViewTracking()
        Countly.sharedInstance().init(config)
    }
}
