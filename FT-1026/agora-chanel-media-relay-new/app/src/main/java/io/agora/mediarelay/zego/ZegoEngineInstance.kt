package io.agora.mediarelay.zego

import android.os.Build
import android.os.Handler
import android.os.Looper
import com.blankj.utilcode.util.Utils
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.constants.ZegoScenario
import im.zego.zegoexpress.constants.ZegoVideoConfigPreset
import im.zego.zegoexpress.entity.ZegoEngineProfile
import im.zego.zegoexpress.entity.ZegoVideoConfig
import io.agora.mediarelay.BuildConfig
import java.util.Random

object ZegoEngineInstance {
    private const val TAG = "ZegoEngineInstance"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private fun runOnMainThread(runnable: Runnable) {
        if (Thread.currentThread() === mainHandler.looper.thread) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    private var innerUserId = ""

    val userId: String
        get() {
            if (innerUserId.isEmpty()) {
                val random = Random()
                innerUserId = ("Android_" + Build.MODEL).replace(" ".toRegex(), "_") + "_" + random.nextInt(1000)
            }
            return innerUserId
        }

    private val profile by lazy {
        ZegoEngineProfile().apply {
            application = Utils.getApp()
            appID = BuildConfig.ZEGO_APP_ID.toLongOrNull() ?: 0L
            appSign = BuildConfig.ZEGO_APP_SIGN
            scenario = ZegoScenario.HIGH_QUALITY_VIDEO_CALL
        }
    }

    var zVideoConfig = ZegoVideoConfig(ZegoVideoConfigPreset.PRESET_1080P)

    private var innerEngine: ZegoExpressEngine? = null

    val engine: ZegoExpressEngine
        get() {
            if (innerEngine == null) {
                innerEngine = ZegoExpressEngine.createEngine(profile, null)
            }
            return innerEngine!!
        }

    fun destroy() {
        innerEngine?.let {
            it.logoutRoom()
            innerEngine = null
        }
    }
}