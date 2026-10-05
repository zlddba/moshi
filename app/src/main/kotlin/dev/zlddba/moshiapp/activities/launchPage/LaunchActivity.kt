package dev.zlddba.moshiapp.activities.launchPage

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.zlddba.moshiapp.activities.firstLaunchPage.FirstLaunchActivity
import dev.zlddba.moshiapp.activities.lockPage.LockActivity
import dev.zlddba.moshiapp.activities.mainPage.MainActivity
import dev.zlddba.moshiapp.data.prefs.FirstLaunchPrefs
import dev.zlddba.moshiapp.domain.security.AppLock
import dev.zlddba.moshiapp.domain.utils.AppInfoHelper
import dev.zlddba.moshiapp.domain.utils.CountdownManagerHelper
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

class LaunchActivity : ComponentActivity() {
    val countDownManager = CountdownManagerHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val appVersionName = AppInfoHelper.getAppVersionName(this)
        setContent {
            MoshiTheme {
                LaunchPageScreen(version = appVersionName)
            }
        }

        countDownManager.startCountdown(
            totalMillis = 2000L,
            tickIntervalMillis = 200L,
            onFinish = {
                if (AppLock.isLockEnabled(this)) {
                    startActivity(LockActivity.routeIntent(this))
                } else {
                    val nextTarget = if (FirstLaunchPrefs(this).isFirstLaunchCompleted()) {
                        MainActivity::class.java
                    } else {
                        FirstLaunchActivity::class.java
                    }
                    startActivity(Intent(this, nextTarget))
                }
                finish()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownManager.dispose()
    }
}