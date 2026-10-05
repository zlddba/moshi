package dev.zlddba.moshiapp

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import dev.zlddba.moshiapp.activities.launchPage.LaunchActivity
import dev.zlddba.moshiapp.activities.lockPage.LockActivity
import dev.zlddba.moshiapp.data.repo.HelpSeeder
import dev.zlddba.moshiapp.domain.device.DeviceTier
import dev.zlddba.moshiapp.domain.index.IndexOrchestrator
import dev.zlddba.moshiapp.domain.security.AppLock

class MoshiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DeviceTier.applyAutoDowngrade(this)
        IndexOrchestrator.start(this)
        HelpSeeder.start(this)
        registerActivityLifecycleCallbacks(AppLifecycleWatcher())
    }
}

private class AppLifecycleWatcher : Application.ActivityLifecycleCallbacks {

    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        started++
        if (started != 1) return
        if (activity is LockActivity) return
        if (activity is LaunchActivity) return
        if (!AppLock.shouldLockOnForeground(activity)) return
        activity.startActivity(Intent(activity, LockActivity::class.java))
    }

    override fun onActivityStopped(activity: Activity) {
        if (started > 0) started--
        if (started == 0) AppLock.onEnterBackground(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
