package dev.zlddba.moshiapp.domain.utils

import android.os.CountDownTimer

class CountdownManagerHelper {
    private var countDownTimer: CountDownTimer? = null

    var isRunning: Boolean = false
        private set

    fun startCountdown(
        totalMillis: Long,
        tickIntervalMillis: Long = 1000,
        onTick: ((remainingMillis: Long) -> Unit)? = null,
        onFinish: () -> Unit
    ) {
        require(totalMillis>0){"总倒计时时间必须大于0"}
        require(tickIntervalMillis > 0) { "更新间隔必须大于0" }
        cancelCountdown()

        countDownTimer = object : CountDownTimer(totalMillis, tickIntervalMillis) {
            override fun onTick(millisUntilFinished: Long) {
                onTick?.invoke(millisUntilFinished)
            }

            override fun onFinish() {
                isRunning = false
                onFinish()
            }
        }

        isRunning = true
        countDownTimer?.start()
    }

    fun cancelCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
        isRunning = false
    }

    fun dispose() {
        cancelCountdown()
    }
}