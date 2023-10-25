package io.agora.sttmediaplayer

import android.content.Context
import android.util.Log
import io.agora.aigc.sdk.AIGCService
import io.agora.aigc.sdk.AIGCServiceCallback
import io.agora.aigc.sdk.AIGCServiceConfig
import io.agora.aigc.sdk.constants.Language
import io.agora.aigc.sdk.model.AIRole
import io.agora.aigc.sdk.model.SceneMode

class STTServiceManager private constructor() {
    companion object {
        private val TAG = "Agora-" + STTServiceManager::class.java.simpleName

        @Volatile
        private var mInstance: STTServiceManager? = null
        val instance: STTServiceManager
            get() {
                if (mInstance == null) {
                    synchronized(STTServiceManager::class.java) {
                        if (mInstance == null) {
                            mInstance = STTServiceManager()
                        }
                    }
                }
                return mInstance!!
            }
    }

    private var mAIGCService: AIGCService? = null
    fun initAIGCService(serviceCallback: AIGCServiceCallback, onContext: Context) {
        Log.d(TAG, "initSTTService")
        if (null == mAIGCService) {
            mAIGCService = AIGCService.create()
        }
        mAIGCService?.initialize(AIGCServiceConfig().apply {
            val uid = KeyCenter.getUserUid()
            val rtmToken = KeyCenter.getRtmToken(uid)
            Log.d(TAG,"initialize uid:$uid,rtm token:$rtmToken")
            this.context = onContext
            this.callback = serviceCallback
            this.enableLog = true
            this.enableSaveLogToFile = true
            this.userName = "AI"
            this.appId = KeyCenter.APP_ID
            this.rtmToken = rtmToken
            this.userId = uid.toString()
            this.enableMultiTurnShortTermMemory = false
            this.speechRecognitionFiltersLength = 0
            this.input = SceneMode().apply {
                this.language = Language.ZH_CN
                this.speechFrameSampleRates = 16000
                this.speechFrameChannels = 1
                this.speechFrameBits = 16
            }
            this.output = SceneMode().apply {
                this.language = Language.ZH_CN
                this.speechFrameSampleRates = 16000
                this.speechFrameChannels = 1
                this.speechFrameBits = 16
            }
        })
    }

    val aIGCService: AIGCService?
        get() = mAIGCService
    val currentAIRole: AIRole?
        get() = mAIGCService?.getCurrentRole()

    fun destroy() {
        AIGCService.destroy()
        mAIGCService = null
    }
}