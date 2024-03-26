package io.agora.mediarelay.rtc

import io.agora.mediarelay.BuildConfig
import io.agora.mediarelay.MApp
import io.agora.mediarelay.rtc.transcoder.RestfulTranscoder
import io.agora.mediarelay.tools.LogTool
import io.agora.rtc2.ClientRoleOptions
import io.agora.rtc2.Constants

import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.RtcEngineEx
import io.agora.rtc2.video.VideoEncoderConfiguration
import java.util.concurrent.Executors

object AgoraRtcEngineInstance {

    val videoEncoderConfiguration = VideoEncoderConfiguration().apply {
        orientationMode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
        mirrorMode = VideoEncoderConfiguration.MIRROR_MODE_TYPE.MIRROR_MODE_ENABLED
        dimensions = RtcSettings.mVideoDimensions
        frameRate = RtcSettings.mFrameRate
        bitrate = RtcSettings.mBitRate
    }

    private val workingExecutor = Executors.newSingleThreadExecutor()

    private var innerRtcEngine: RtcEngineEx? = null

    private var innerTranscoder: RestfulTranscoder? = null

    private var mAppId: String = BuildConfig.AGORA_APP_ID
    private var mCustomerKey: String = BuildConfig.AGORA_APP_ID
    private var mSecret: String = BuildConfig.AGORA_APP_ID

    private var mVideoInfoListener: IVideoInfoListener? = null

    const val TAG = "AgoraRtcImpl"

    var eventListener: IAgoraRtcClient.IChannelEventListener? = null

    fun setAppKeys(appId: String, customerKey: String, secret: String) {
        mAppId = appId
        mCustomerKey = customerKey
        mSecret = secret
    }

    fun setVideoInfoListener(listener: IVideoInfoListener?) {
        mVideoInfoListener = listener
    }

    val transcoder: RestfulTranscoder
        get() {
            if (innerTranscoder == null) {
                innerTranscoder = RestfulTranscoder(mAppId, mCustomerKey, mSecret)
            }
            return innerTranscoder!!
        }

    val rtcEngine: RtcEngineEx
        get() {
            if (innerRtcEngine == null) {
                val config = RtcEngineConfig()
                config.mContext = MApp.instance()
                config.mAppId = mAppId
                config.mEventHandler = object : IRtcEngineEventHandler() {

                    override fun onError(err: Int) {
                        super.onError(err)
                        LogTool.e(TAG, "error:code=$err, message=${RtcEngine.getErrorDescription(err)}")
                    }

                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        super.onJoinChannelSuccess(channel, uid, elapsed)
                        rtcEngine.setEnableSpeakerphone(false)
                        rtcEngine.setEnableSpeakerphone(true)
                        eventListener?.onChannelJoined?.invoke()
                        LogTool.d(TAG, "onJoinChannelSuccess channelId $channel")
                    }

                    override fun onLeaveChannel(stats: RtcStats?) {
                        super.onLeaveChannel(stats)
                        eventListener?.onLeaveChannel?.invoke()
                        LogTool.d(TAG, "onLeaveChannel")
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

                    override fun onLocalVideoStats(source: Constants.VideoSourceType?, stats: LocalVideoStats?) {
                        super.onLocalVideoStats(source, stats)
                        stats ?: return
                        mVideoInfoListener?.onLocalVideoStats(source, stats)
                    }

                    override fun onRemoteVideoStats(stats: RemoteVideoStats?) {
                        super.onRemoteVideoStats(stats)
                        stats ?: return
                        mVideoInfoListener?.onRemoteVideoStats(stats)
                    }

                    override fun onUplinkNetworkInfoUpdated(info: UplinkNetworkInfo?) {
                        super.onUplinkNetworkInfoUpdated(info)
                        mVideoInfoListener?.onUplinkNetworkInfoUpdated(info)
                    }

                    override fun onDownlinkNetworkInfoUpdated(info: DownlinkNetworkInfo?) {
                        super.onDownlinkNetworkInfoUpdated(info)
                        mVideoInfoListener?.onDownlinkNetworkInfoUpdated(info)
                    }

                    override fun onClientRoleChanged(oldRole: Int, newRole: Int, newRoleOptions: ClientRoleOptions?) {
                        super.onClientRoleChanged(oldRole, newRole, newRoleOptions)
                        eventListener?.onClientRoleChanged?.invoke(oldRole, newRole, newRoleOptions)
                        LogTool.d(TAG, "onClientRoleChanged: oldRole:$oldRole,newRole:$newRole")
                    }

                    override fun onClientRoleChangeFailed(reason: Int, currentRole: Int) {
                        super.onClientRoleChangeFailed(reason, currentRole)
                        LogTool.d(TAG, "onClientRoleChangeFailed: reason:$reason,currentRole:$currentRole")
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
            innerTranscoder = null
        }
    }
}
