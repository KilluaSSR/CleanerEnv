package killua.dev.cleaner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import killua.dev.arsenal.data.settings.SettingsReactorRegistry
import killua.dev.arsenal.error.ArsenalErrorHandler
import killua.dev.arsenal.error.ErrorConfig
import killua.dev.cleaner.data.backend.DaemonConfigRelay
import killua.dev.cleaner.data.backend.RuleFilePublisher
import killua.dev.cleaner.data.backend.WatchFilePublisher
import javax.inject.Inject

@HiltAndroidApp
class CleanerApplication : Application() {

    @Inject
    lateinit var settingsReactorRegistry: SettingsReactorRegistry

    @Inject
    lateinit var ruleFilePublisher: RuleFilePublisher

    @Inject
    lateinit var daemonConfigRelay: DaemonConfigRelay

    @Inject
    lateinit var watchFilePublisher: WatchFilePublisher

    override fun onCreate() {
        super.onCreate()
        settingsReactorRegistry.ensureStarted()
        ruleFilePublisher.start()
        watchFilePublisher.start()
        daemonConfigRelay.start()
        ArsenalErrorHandler.install(
            application = this,
            config = ErrorConfig(restartActivity = CleanerActivity::class.java, showStackTrace = BuildConfig.DEBUG),
        )
    }
}
