package io.agora.mediarelay

import android.app.Application
import io.agora.logging.FileLogger
import io.agora.logging.LogManager

/**
 * @author create by zhangwei03
 */
class MApp : Application() {

    companion object {
        private lateinit var app: Application

        @JvmStatic
        fun instance(): Application {
            return app
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        LogManager.instance().addLogger(
            FileLogger(
                getExternalFilesDir(null)!!.path,
                "agorademo",
                (1024 * 1024).toLong(),
                3,
            )
        )

    }
}