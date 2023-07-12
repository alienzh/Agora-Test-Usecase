package io.agora.mediarelay.rtc

import io.agora.mediarelay.BuildConfig
import io.agora.mediarelay.MApp
import io.agora.mediarelay.tools.LogTool

import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.RtcEngineEx
import io.agora.rtc2.video.VideoEncoderConfiguration
import java.util.concurrent.Executors

object AgoraRtcEngineInstance {

    val videoEncoderConfiguration = VideoEncoderConfiguration().apply {
        orientationMode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
    }
    private val workingExecutor = Executors.newSingleThreadExecutor()

    private var innerRtcEngine: RtcEngineEx? = null

    const val TAG = "AgoraRtcImpl"

    var eventListener: IAgoraRtcClient.IChannelEventListener? = null

    val rtcEngine: RtcEngineEx
        get() {
            if (innerRtcEngine == null) {
                val config = RtcEngineConfig()
                config.mContext = MApp.instance()
                config.mAppId = BuildConfig.RTC_APP_ID
                config.mEventHandler = object : IRtcEngineEventHandler() {

                    override fun onError(err: Int) {
                        super.onError(err)
                        LogTool.e(TAG, "error:code=$err, message=${RtcEngine.getErrorDescription(err)}")
                    }

                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        super.onJoinChannelSuccess(channel, uid, elapsed)
                        eventListener?.onChannelJoined?.invoke(true)
                        LogTool.d(TAG, "join channel $channel")
                    }

                    override fun onLeaveChannel(stats: RtcStats?) {
                        super.onLeaveChannel(stats)
                        eventListener?.onChannelJoined?.invoke(false)
                        LogTool.d(TAG, "leave channel")
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        super.onUserJoined(uid, elapsed)
                        eventListener?.onUserJoined?.invoke(uid)
                        LogTool.d(TAG, "onUserJoined: $uid")
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        super.onUserOffline(uid, reason)
                        eventListener?.onUserOffline?.invoke(uid)
                        LogTool.d(TAG, "onUserOffline: $uid")
                    }

                    override fun onRtmpStreamingStateChanged(url: String?, state: Int, errCode: Int) {
                        super.onRtmpStreamingStateChanged(url, state, errCode)
                        eventListener?.onRtmpStreamingStateChanged?.invoke(url ?: "", state, errCode)
                        LogTool.d(TAG, "onRtmpStreamingStateChanged:$url state:$state, errCode:$errCode")
                    }

                    override fun onRtmpStreamingEvent(url: String?, event: Int) {
                        super.onRtmpStreamingEvent(url, event)
                        LogTool.d(TAG, "onRtmpStreamingEvent:$url event:$event")
                    }

                    override fun onChannelMediaRelayStateChanged(state: Int, code: Int) {
                        super.onChannelMediaRelayStateChanged(state, code)
                        eventListener?.onChannelMediaRelayStateChanged?.invoke(state, code)
                        LogTool.d(TAG, "onChannelMediaRelayStateChanged state: $state, code: $code")
                    }
                }
                innerRtcEngine = (RtcEngine.create(config) as RtcEngineEx).apply {
                    enableVideo()
                }
            }
            return innerRtcEngine!!
        }



    fun destroy() {
        innerRtcEngine?.let {
            workingExecutor.execute { RtcEngine.destroy() }
            innerRtcEngine = null
        }
    }
}
