package io.agora.mediarelay.rtc

import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.Utils
import io.agora.mediarelay.BuildConfig
import io.agora.mediarelay.tools.LogTool
import io.agora.rtc2.Constants

import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.RtcEngineEx
import io.agora.rtc2.video.VideoEncoderConfiguration
import java.util.concurrent.Executors

object AgoraRtcEngineInstance {

    val videoEncoderConfiguration = VideoEncoderConfiguration().apply {
        orientationMode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
        mirrorMode = VideoEncoderConfiguration.MIRROR_MODE_TYPE.MIRROR_MODE_DISABLED
        dimensions = RtcSettings.mVideoDimensions
        frameRate = RtcSettings.mFrameRate
        bitrate = RtcSettings.mBitRate
    }

    private val workingExecutor = Executors.newSingleThreadExecutor()

    private var innerRtcEngine: RtcEngineEx? = null

    var mAppId: String = BuildConfig.AGORA_APP_ID
        private set(value) {
            field = value
        }
    var mAccessKey: String = ""
        private set(value) {
            field = value
        }

    var mSecretKey: String = ""
        private set(value) {
            field = value
        }

    private var mVideoInfoListener: IVideoInfoListener? = null

    const val TAG = "AgoraRtcImpl"

    var eventListener: IAgoraRtcClient.IChannelEventListener? = null

    fun setAppKeys(appId: String, accessKey: String, secretKey: String) {
        mAppId = appId
        mAccessKey = accessKey
        mSecretKey = secretKey
    }

    fun setVideoInfoListener(listener: IVideoInfoListener?) {
        mVideoInfoListener = listener
    }

    val rtcEngine: RtcEngineEx
        get() {
            if (innerRtcEngine == null) {
                val config = RtcEngineConfig()
                config.mContext = Utils.getApp()
                config.mAppId = mAppId
                config.mEventHandler = object : IRtcEngineEventHandler() {

                    override fun onError(err: Int) {
                        super.onError(err)
                        LogTool.e(TAG, "error:code=$err, message=${RtcEngine.getErrorDescription(err)}")
                    }

                    override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                        super.onJoinChannelSuccess(channel, uid, elapsed)
                        LogTool.d(TAG, "onJoinChannelSuccess channelId $channel, uid $uid")
                        ThreadUtils.runOnUiThread {
                            rtcEngine.setEnableSpeakerphone(false)
                            rtcEngine.setEnableSpeakerphone(true)
                            eventListener?.onChannelJoined?.invoke(channel, uid)
                        }
                    }

                    override fun onLeaveChannel(stats: RtcStats?) {
                        super.onLeaveChannel(stats)
                        LogTool.d(TAG, "onLeaveChannel")
                        ThreadUtils.runOnUiThread {
                            eventListener?.onLeaveChannel?.invoke()
                        }
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        super.onUserJoined(uid, elapsed)
                        LogTool.d(TAG, "onUserJoined: $uid")
                        ThreadUtils.runOnUiThread {
                            eventListener?.onUserJoined?.invoke(uid)
                        }
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        super.onUserOffline(uid, reason)
                        LogTool.d(TAG, "onUserOffline: $uid")
                        ThreadUtils.runOnUiThread {
                            eventListener?.onUserOffline?.invoke(uid)
                        }
                    }

                    override fun onLocalVideoStats(source: Constants.VideoSourceType?, stats: LocalVideoStats?) {
                        super.onLocalVideoStats(source, stats)
                        stats ?: return
                        mVideoInfoListener?.onLocalVideoStats(source, stats)
                    }

                    override fun onRemoteVideoStats(stats: RemoteVideoStats?) {
                        super.onRemoteVideoStats(stats)
                        stats ?: return
                        ThreadUtils.runOnUiThread {
                            mVideoInfoListener?.onRemoteVideoStats(stats)
                        }
                    }

                    override fun onUplinkNetworkInfoUpdated(info: UplinkNetworkInfo?) {
                        super.onUplinkNetworkInfoUpdated(info)
                        ThreadUtils.runOnUiThread {
                            mVideoInfoListener?.onUplinkNetworkInfoUpdated(info)
                        }
                    }

                    override fun onDownlinkNetworkInfoUpdated(info: DownlinkNetworkInfo?) {
                        super.onDownlinkNetworkInfoUpdated(info)
                        ThreadUtils.runOnUiThread {
                            mVideoInfoListener?.onDownlinkNetworkInfoUpdated(info)
                        }
                    }

                    override fun onClientRoleChangeFailed(reason: Int, currentRole: Int) {
                        super.onClientRoleChangeFailed(reason, currentRole)
                        LogTool.d(TAG, "onClientRoleChangeFailed: reason:$reason,currentRole:$currentRole")
                    }

                    override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray) {
                        super.onStreamMessage(uid, streamId, data)
                        LogTool.d(TAG, "onStreamMessage: uid:$uid,streamId:$streamId,${String(data)}")
                        ThreadUtils.runOnUiThread {
                            eventListener?.onStreamMessage?.invoke(uid, streamId, data)
                        }
                    }

                    override fun onFirstRemoteVideoFrame(uid: Int, width: Int, height: Int, elapsed: Int) {
                        super.onFirstRemoteVideoFrame(uid, width, height, elapsed)
                        LogTool.d(TAG, "onFirstRemoteVideoFrame: uid:$uid,width:$width,height:$height}")
                    }

                    override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
                        super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
                        LogTool.d(TAG, "onRemoteVideoStateChanged: uid:$uid,state:$state,reason:$reason")
                    }

                    override fun onTranscodingUpdated() {
                        super.onTranscodingUpdated()
                        LogTool.d(TAG, "onTranscodingUpdated")
                    }

                    val layoutInfoMap = mutableMapOf<Int, String>()

                    override fun onTranscodedStreamLayoutInfo(uid: Int, info: VideoLayoutInfo?) {
                        super.onTranscodedStreamLayoutInfo(uid, info)
                        LogTool.d(TAG, "onTranscodedStreamLayoutInfo: uid:$uid,infoSize:$info")

                        val oldInfo = layoutInfoMap.getOrDefault(uid, null)
                        if (oldInfo == null || oldInfo != info?.toString()) {
                            ThreadUtils.runOnUiThread {
                                eventListener?.onTranscodedStreamLayoutInfo?.invoke(uid, info)
                            }
                        }
                    }
                }
                innerRtcEngine = (RtcEngine.create(config) as RtcEngineEx).apply {
                    setParameters("{\"rtc.video.end2end_bwe\":false}") // 关闭端到端反馈
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
