package io.agora.mediarelay.rtc

import android.os.Handler
import android.os.Looper
import com.blankj.utilcode.util.Utils
import io.agora.mediarelay.BuildConfig
import io.agora.mediarelay.rtc.transcoder.RestfulTranscoder
import io.agora.mediarelay.tools.LogTool
import io.agora.rtc2.ClientRoleOptions
import io.agora.rtc2.Constants

import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.RtcEngineEx
import io.agora.rtc2.UserInfo
import io.agora.rtc2.video.VideoEncoderConfiguration
import java.util.concurrent.Executors

object AgoraRtcEngineInstance {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private fun runOnMainThread(runnable: Runnable) {
        if (Thread.currentThread() === mainHandler.looper.thread) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    val videoEncoderConfiguration = VideoEncoderConfiguration().apply {
        orientationMode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
        mirrorMode = VideoEncoderConfiguration.MIRROR_MODE_TYPE.MIRROR_MODE_DISABLED
        dimensions = RtcSettings.mVideoDimensions
        frameRate = RtcSettings.mFrameRate
        bitrate = RtcSettings.mBitRate
    }

    private val workingExecutor = Executors.newSingleThreadExecutor()

    private var innerRtcEngine: RtcEngineEx? = null

    private var innerTranscoder: RestfulTranscoder? = null

    val mAppId: String = BuildConfig.AGORA_APP_ID
    private var mAccessKey: String = BuildConfig.AGORA_ACCESS_KEY
    private var mSecretKey: String = BuildConfig.AGORA_SECRET_KEY

    private var mVideoInfoListener: IVideoInfoListener? = null

    const val TAG = "AgoraRtcImpl"

    var eventListener: IAgoraRtcClient.IChannelEventListener? = null


    fun setVideoInfoListener(listener: IVideoInfoListener?) {
        mVideoInfoListener = listener
    }

    val transcoder: RestfulTranscoder
        get() {
            if (innerTranscoder == null) {
                innerTranscoder = RestfulTranscoder(mAppId, mAccessKey, mSecretKey)
            }
            return innerTranscoder!!
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
                        runOnMainThread {
                            rtcEngine.setEnableSpeakerphone(false)
                            rtcEngine.setEnableSpeakerphone(true)
                            eventListener?.onChannelJoined?.invoke(channel, uid)
                        }
                    }

                    override fun onLeaveChannel(stats: RtcStats?) {
                        super.onLeaveChannel(stats)
                        LogTool.d(TAG, "onLeaveChannel")
                        runOnMainThread {
                            eventListener?.onLeaveChannel?.invoke()
                        }
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        super.onUserJoined(uid, elapsed)
                        LogTool.d(TAG, "onUserJoined: $uid")
                        runOnMainThread {
                            eventListener?.onUserJoined?.invoke(uid)
                        }
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        super.onUserOffline(uid, reason)
                        LogTool.d(TAG, "onUserOffline: $uid")
                        runOnMainThread {
                            eventListener?.onUserOffline?.invoke(uid)
                        }
                    }

                    override fun onRtmpStreamingStateChanged(url: String?, state: Int, errCode: Int) {
                        super.onRtmpStreamingStateChanged(url, state, errCode)
                        LogTool.d(TAG, "onRtmpStreamingStateChanged:$url state:$state, errCode:$errCode")
                        runOnMainThread {
                            eventListener?.onRtmpStreamingStateChanged?.invoke(url ?: "", state, errCode)
                        }
                    }

                    override fun onRtmpStreamingEvent(url: String?, event: Int) {
                        super.onRtmpStreamingEvent(url, event)
                        LogTool.d(TAG, "onRtmpStreamingEvent:$url event:$event")
                    }

                    override fun onLocalVideoStats(source: Constants.VideoSourceType?, stats: LocalVideoStats?) {
                        super.onLocalVideoStats(source, stats)
                        stats ?: return
                        mVideoInfoListener?.onLocalVideoStats(source, stats)
                    }

                    override fun onRemoteVideoStats(stats: RemoteVideoStats?) {
                        super.onRemoteVideoStats(stats)
                        stats ?: return
                        runOnMainThread {
                            mVideoInfoListener?.onRemoteVideoStats(stats)
                        }
                    }

                    override fun onUplinkNetworkInfoUpdated(info: UplinkNetworkInfo?) {
                        super.onUplinkNetworkInfoUpdated(info)
                        runOnMainThread {
                            mVideoInfoListener?.onUplinkNetworkInfoUpdated(info)
                        }
                    }

                    override fun onDownlinkNetworkInfoUpdated(info: DownlinkNetworkInfo?) {
                        super.onDownlinkNetworkInfoUpdated(info)
                        runOnMainThread {
                            mVideoInfoListener?.onDownlinkNetworkInfoUpdated(info)
                        }
                    }

                    override fun onClientRoleChanged(oldRole: Int, newRole: Int, newRoleOptions: ClientRoleOptions?) {
                        super.onClientRoleChanged(oldRole, newRole, newRoleOptions)
                        LogTool.d(TAG, "onClientRoleChanged: oldRole:$oldRole,newRole:$newRole")
                        runOnMainThread {
                            eventListener?.onClientRoleChanged?.invoke(oldRole, newRole, newRoleOptions)
                        }
                    }

                    override fun onClientRoleChangeFailed(reason: Int, currentRole: Int) {
                        super.onClientRoleChangeFailed(reason, currentRole)
                        LogTool.d(TAG, "onClientRoleChangeFailed: reason:$reason,currentRole:$currentRole")
                    }

                    override fun onLocalUserRegistered(uid: Int, userAccount: String) {
                        super.onLocalUserRegistered(uid, userAccount)
                        LogTool.d(TAG, "onLocalUserRegistered: uid:$uid,userAccount:$userAccount")
                        runOnMainThread {
                            eventListener?.onLocalUserRegistered?.invoke(uid, userAccount)
                        }
                    }

                    override fun onUserInfoUpdated(uid: Int, userInfo: UserInfo) {
                        super.onUserInfoUpdated(uid, userInfo)
                        LogTool.d(TAG, "onUserInfoUpdated: uid:$uid,userInfo:${userInfo.uid}-${userInfo.userAccount}")
                        runOnMainThread {
                            eventListener?.onUserInfoUpdated?.invoke(uid, userInfo)
                        }
                    }

                    override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray) {
                        super.onStreamMessage(uid, streamId, data)
                        LogTool.d(TAG, "onStreamMessage: uid:$uid,streamId:$streamId,${String(data)}")
                        runOnMainThread {
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
                }
                innerRtcEngine = (RtcEngine.create(config) as RtcEngineEx).apply {
                    setParameters("{\"rtc.video.end2end_bwe\":false}") // 关闭端到端反馈
                    setLogFilter(65535)
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
