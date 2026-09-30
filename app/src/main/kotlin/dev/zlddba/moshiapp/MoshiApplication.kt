package dev.zlddba.moshiapp

import android.app.Application
import dev.zlddba.moshiapp.data.repo.HelpSeeder
import dev.zlddba.moshiapp.domain.index.IndexOrchestrator

class MoshiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        IndexOrchestrator.start(this)
        HelpSeeder.start(this)
    }
}
