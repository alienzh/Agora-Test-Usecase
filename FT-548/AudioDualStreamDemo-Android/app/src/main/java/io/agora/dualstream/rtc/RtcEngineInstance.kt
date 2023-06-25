package io.agora.dualstream.rtc

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.agora.dualstream.BuildConfig
import io.agora.dualstream.MApp
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

    private var eventListener: IChannelEventListener? = null

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

                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        Log.d(TAG, "onJoinChannelSuccess:channel=$channel,uid=$uid")
                        mainHandler.post {
                            eventListener?.onChannelJoined?.invoke(uid)
                        }
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        Log.d(TAG, "onUserJoined:uid=$uid")
                        mainHandler.post {
                            eventListener?.onUserJoined?.invoke(uid)
                        }
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        Log.d(TAG, "onUserOffline:uid=$uid")
                        mainHandler.post {
                            eventListener?.onUserOffline?.invoke(uid)
                        }
                    }

                    override fun onLeaveChannel(stats: RtcStats?) {
                        Log.d(TAG, "onLeaveChannel")
                    }
                }
                innerRtcEngine = (RtcEngine.create(config) as RtcEngineEx).apply {
                    enableVideo()
                }
            }
            return innerRtcEngine!!
        }

    fun joinChannel(
        channelId: String,
        rtcUid: Int,
        mediaOptions: ChannelMediaOptions,
        eventListener: IChannelEventListener
    ) {
        RtcEngineInstance.eventListener = eventListener
        rtcEngine.joinChannel("", channelId, rtcUid, mediaOptions)
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
                    Log.d(TAG, "low onJoinChannelSuccess:channel=$channel,uid=$uid")
                    mainHandler.post {
                        eventListener.onChannelJoined?.invoke(uid)
                    }
                }

                override fun onUserJoined(uid: Int, elapsed: Int) {
                    Log.d(TAG, "low onUserJoined:uid=$uid")
                    mainHandler.post {
                        eventListener.onUserJoined?.invoke(uid)
                    }
                }

                override fun onUserOffline(uid: Int, reason: Int) {
                    Log.d(TAG, "low onUserOffline:uid=$uid")
                    mainHandler.post {
                        eventListener.onUserOffline?.invoke(uid)
                    }
                }

                override fun onLeaveChannel(stats: RtcStats?) {
                    Log.d(TAG, "low onLeaveChannel")
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

    fun leaveChannel() {
        rtcEngine.leaveChannel()
    }

    fun updateChannelMediaOptionsEx(options: ChannelMediaOptions, rtcConnection: RtcConnection) {
        rtcEngine.updateChannelMediaOptionsEx(options, rtcConnection)
    }

    fun updateChannelMediaOptions(options: ChannelMediaOptions) {
        rtcEngine.updateChannelMediaOptions(options)
    }

    fun muteRemoteAudioStreamEx(uid: Int, mute: Boolean, rtcConnection: RtcConnection) {
        rtcEngine.muteRemoteAudioStreamEx(uid, mute, rtcConnection)
    }

    fun muteRemoteAudioStream(uid: Int, mute: Boolean) {
        rtcEngine.muteRemoteAudioStream(uid, mute)
    }

    fun muteAllRemoteAudioStreamsEx(mute: Boolean, rtcConnection: RtcConnection) {
        rtcEngine.muteAllRemoteAudioStreamsEx(mute, rtcConnection)
    }

    fun muteAllRemoteAudioStreams(mute: Boolean) {
        rtcEngine.muteAllRemoteAudioStreams(mute)
    }

    fun destroy() {
        innerRtcEngine?.let {
            workingExecutor.execute { RtcEngine.destroy() }
            innerRtcEngine = null
        }
    }
}
