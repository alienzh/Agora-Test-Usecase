package io.agora.dualstream

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.agora.rtc2.*
import java.util.concurrent.Executors

data class IChannelEventListener constructor(
    var onChannelJoined: ((uid: Int) -> Unit)? = null,
    var onUserJoined: ((uid: Int) -> Unit)? = null,
    var onUserOffline: ((uid: Int) -> Unit)? = null,
)

object RtcEngineInstance {

    private val workingExecutor = Executors.newSingleThreadExecutor()

    private var innerRtcEngine: RtcEngineEx? = null

    const val TAG = "AgoraRtcImpl"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val rtcEngine: RtcEngineEx
        get() {
            if (innerRtcEngine == null) {
                val config = RtcEngineConfig()
                config.mContext = MApp.instance()
                config.mAppId = BuildConfig.RTC_APP_ID
                config.mEventHandler = object : IRtcEngineEventHandler() {

                    override fun onError(err: Int) {
                        super.onError(err)
                        Log.e(
                            TAG,
                            "onError:code=$err, message=${RtcEngine.getErrorDescription(err)}"
                        )
                    }
                }
                innerRtcEngine = (RtcEngine.create(config) as RtcEngineEx).apply {
                    enableVideo()
                }
            }
            return innerRtcEngine!!
        }

    /**
     * 加入频道
     */
    fun joinChannelEx(
        connection: RtcConnection,
        mediaOptions: ChannelMediaOptions,
        eventListener: IChannelEventListener,
    ) {
        rtcEngine.joinChannelEx(
            "",
            connection,
            mediaOptions,
            object : IRtcEngineEventHandler() {
                override fun onError(err: Int) {
                    super.onError(err)
                    Log.e(
                        TAG, "onError:code=$err, message=${RtcEngine.getErrorDescription(err)}"
                    )
                }

                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    Log.d(TAG, "onJoinChannelSuccess:channel=$channel,uid=$uid")
                    mainHandler.post {
                        eventListener.onChannelJoined?.invoke(uid)
                    }
                }

                override fun onUserJoined(uid: Int, elapsed: Int) {
                    Log.d(TAG, "onUserJoined:uid=$uid")
                    mainHandler.post {
                        eventListener.onUserJoined?.invoke(uid)
                    }
                }

                override fun onUserOffline(uid: Int, reason: Int) {
                    Log.d(TAG, "onUserOffline:uid=$uid")
                    mainHandler.post {
                        eventListener.onUserOffline?.invoke(uid)
                    }
                }

                override fun onLeaveChannel(stats: RtcStats?) {
                    Log.d(TAG, "onLeaveChannel")
                }
            }
        )
    }

    /**
     * 离开频道
     */
    fun leaveChannelEx(rtcConnection: RtcConnection) {
        rtcEngine.leaveChannelEx(rtcConnection)
    }

    fun updateChannelMediaOptionsEx(options: ChannelMediaOptions, rtcConnection: RtcConnection) {
        rtcEngine.updateChannelMediaOptionsEx(options, rtcConnection)
    }

    fun muteRemoteAudioStreamEx(uid:Int,mute:Boolean, rtcConnection: RtcConnection) {
        rtcEngine.muteRemoteAudioStreamEx(uid,mute, rtcConnection)
    }


    fun destroy() {
        innerRtcEngine?.let {
            workingExecutor.execute { RtcEngine.destroy() }
            innerRtcEngine = null
        }
    }
}
