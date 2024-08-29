package io.agora.mediarelay

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.annotation.Size
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.Utils
import im.zego.zegoexpress.entity.ZegoUser
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentLivingBinding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.rtc.transcoder.AgoraTranscodeSetting
import io.agora.mediarelay.rtc.transcoder.ChannelUid
import io.agora.mediarelay.tools.FileUtils
import io.agora.mediarelay.tools.LogTool
import io.agora.mediarelay.widget.DashboardFragment
import io.agora.mediarelay.widget.OnFastClickListener
import io.agora.mediarelay.zego.ZegoEngineInstance
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.DataStreamConfig
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcConnection
import io.agora.rtc2.UserInfo
import io.agora.rtc2.video.ImageTrackOptions
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * @author create by zhangwei03
 */
class LivingFragment : BaseUiFragment<FragmentLivingBinding>() {
    companion object {
        private const val TAG = "LivingFragment"

        const val KEY_CHANNEL_ID: String = "key_channel_id"
        const val KEY_ROLE: String = "key_role"
    }

    // 推流状态
    @Volatile
    private var publishedRtmp = false

    private val channelName by lazy { arguments?.getString(KEY_CHANNEL_ID) ?: "" }

    private val role by lazy {
        arguments?.getInt(KEY_ROLE, Constants.CLIENT_ROLE_BROADCASTER) ?: Constants.CLIENT_ROLE_BROADCASTER
    }

    private val isOwner: Boolean
        get() = role == Constants.CLIENT_ROLE_BROADCASTER

    private val rtcEngine by lazy { AgoraRtcEngineInstance.rtcEngine }

    /**
     * cdn 链接 码率 index
     * 0 720p, 1 1080p
     * [KeyCenter.mBitrateList]
     */
    private var cdnPosition = 0

    @Volatile
    private var audienceStatus: AudienceStatus = AudienceStatus.CDN_Audience

    private val isInPk get() = remotePkChannel.isNotEmpty() || remoteZegoRoomId.isNotEmpty()

    // pk 频道
    private var remotePkChannel = ""

    // 用户 account
    private val userAccount by lazy {
        KeyCenter.rtcAccount(isOwner, channelName)
    }

    // 房主 account
    private val ownerAccount get() = channelName

    //key account，value rtc-uid
    private val uidMapping = mutableMapOf<String, Int>()

    // zego 频道
    private var remoteZegoRoomId = ""

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentLivingBinding {
        return FragmentLivingBinding.inflate(inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initRtcEngine()
    }

    private fun initView() {
        if (isOwner) {
//            binding.layoutChannel.isVisible = true
//            binding.btSubmitPk.isVisible = true
//            binding.layoutZego.isVisible = true
//            binding.btPkZego.isVisible = true
//            binding.btSwitchCarma.isVisible = true
//            binding.btMuteMic.isVisible = true
//            binding.btMuteCarma.isVisible = true

            binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
            binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
            binding.groupBroadcaster.isVisible = true
        } else {
//            binding.layoutChannel.isVisible = false
//            binding.btSubmitPk.isVisible = false
//            binding.layoutZego.isVisible = false
//            binding.btPkZego.isVisible = false
//
//
//            binding.btMuteMic.isVisible = false
//            binding.btMuteCarma.isVisible = false
            binding.groupBroadcaster.isVisible = false
        }
        binding.tvChannelId.text = "$channelName(${KeyCenter.cdnMakes})"
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btPkAgora.setOnClickListener {
            if (remotePkChannel.isNotEmpty()) { // pk 中停止pk
                binding.btPkAgora.text = getString(R.string.start_pk_agora)
                binding.etPkAgoraChannel.setText("")
                remotePkChannel = ""
                stopPk()
                uidMapping[userAccount]?.let { ownerUid ->
                    val channelUid = ChannelUid(channelName, ownerUid, userAccount)
                    updateRtmpStreamEnable(channelUid)
                }
                binding.groupPkZego.isVisible = true
            } else {
                val channelId = binding.etPkAgoraChannel.text.toString()
                if (channelId.isEmpty()) {
                    ToastUtils.showShort("Please enter agora channelId")
                    return@setOnClickListener
                }
                binding.btPkAgora.text = getString(R.string.stop_pk_agora)
                remotePkChannel = channelId
                startPk(channelId)

                binding.groupPkZego.isVisible = false
            }
        }
        binding.btSwitchCarma.setOnClickListener {
            rtcEngine.switchCamera()
        }
        binding.btMuteMic.setOnClickListener {
            if (muteLocalAudio) {
                muteLocalAudio = false
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                rtcEngine.muteLocalAudioStream(false)
            } else {
                muteLocalAudio = true
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_off)
                rtcEngine.muteLocalAudioStream(true)
            }
        }
        binding.btMuteCarma.setOnClickListener {
            if (muteLocalVideo) {
                muteLocalVideo = false
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
                rtcEngine.startPreview()
                val imageTrackOptions = ImageTrackOptions(FileUtils.blackImage, 15)
                rtcEngine.enableVideoImageSource(false, imageTrackOptions)
            } else {
                muteLocalVideo = true
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_off)
                rtcEngine.stopPreview()
                val imageTrackOptions = ImageTrackOptions(FileUtils.blackImage, 15)
                rtcEngine.enableVideoImageSource(true, imageTrackOptions)
            }
        }
        val dashboardFragment = DashboardFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.fl_dashboard, dashboardFragment)
            .commit()
        binding.btDashboard.setOnClickListener {
            it.isSelected = !it.isSelected
            dashboardFragment.setOn(it.isSelected)
            binding.flDashboard.isVisible = it.isSelected
        }
        binding.btPkZego.setOnClickListener(object :
            OnFastClickListener(message = getString(R.string.click_too_fast)) {
            override fun onClickJacking(view: View) {
                if (remoteZegoRoomId.isNotEmpty()) { // pk 中停止pk
                    binding.btPkZego.text = getString(R.string.start_pk_zego)
                    binding.etPkZegoRoomid.setText("")
                    remoteZegoRoomId = ""
                    stopPk()
                    uidMapping[userAccount]?.let { ownerUid ->
                        val channelUid = ChannelUid(channelName, ownerUid, userAccount)
                        updateRtmpStreamEnable(channelUid)
                    }
                    binding.groupPk.isVisible = true
                } else {
                    val zegoRoomId = binding.etPkZegoRoomid.text.toString()
                    if (zegoRoomId.isEmpty()) {
                        ToastUtils.showShort("Please enter zego roomId")
                        return
                    }
                    binding.btPkZego.text =  getString(R.string.stop_pk_zego)
                    remoteZegoRoomId = zegoRoomId
                    startPk(zegoRoomId)
                    binding.groupPk.isVisible = false
                }
            }
        })
    }

    private var muteLocalAudio = false
    private var muteLocalVideo = false

    private val eventListener = IAgoraRtcClient.IChannelEventListener(
        onChannelJoined = { channel, uid ->
            if (isOwner) {
                startSendRemoteChannel()
                setRtmpStreamEnable(true)
                startPushToZego()
            } else {
                //超级画质
                val ret1 = rtcEngine.setParameters("{\"rtc.video.enable_sr\":{\"enabled\":true, \"mode\": 2}}")
                val ret2 = rtcEngine.setParameters("{\"rtc.video.sr_type\":20}")
                Log.d(TAG, "enable_sr ret：$ret1, sr_type ret：$ret2")
            }
        },
        onUserInfoUpdated = { uid, userInfo ->
            uidMapping[userInfo.userAccount] = userInfo.uid
        },
        onUserJoined = { uid ->
            if (isOwner) {
                // nothing
            } else {
                // 当前房间主播
                if (uidMapping[channelName] == uid) {
                    setupRemoteVideo(uid)
                }
            }
        },
        onUserOffline = { offlineUid ->
        },

        onRtmpStreamingStateChanged = { url, state, code ->
            when (state) {
                Constants.RTMP_STREAM_PUBLISH_STATE_RUNNING -> {
                    if (code == Constants.RTMP_STREAM_PUBLISH_REASON_OK) {
                        ToastUtils.showShort("rtmp stream publish state running")
                    }
                }

                Constants.RTMP_STREAM_PUBLISH_STATE_FAILURE -> {
                    ToastUtils.showShort("rtmp stream publish state failure: $code")
                }
            }
        },
        onLocalUserRegistered = { uid, userAccount ->
            uidMapping[userAccount] = uid
            this.onLocalUserRegistered?.invoke(uid, userAccount)
            this.onLocalUserRegistered = null
        },
        onStreamMessage = { uid, streamId, data ->
            try {
                if (isOwner) return@IChannelEventListener
                val strMsg = String(data)
                val jsonMsg = JSONObject(strMsg)
                if (jsonMsg.getString("cmd") == "StartPk") { //同步远端 remotePk
                    val channel = jsonMsg.getString("channel")
                    if (isInPk) return@IChannelEventListener
                    remotePkChannel = channel
                    startPk(remotePkChannel)
                } else if (jsonMsg.getString("cmd") == "StopPk") {
                    if (!isInPk) return@IChannelEventListener
                    val tempChannel = remotePkChannel
                    remotePkChannel = ""
                    stopPk()
                }
            } catch (e: Exception) {
                Log.e(TAG, "onStreamMessage")
            }
        }
    )

    private fun initRtcEngine() {
        checkRequirePerms {
            AgoraRtcEngineInstance.eventListener = eventListener
            if (isOwner) {
                registerAccount { uid, userCount ->
                    setupLocalVideo(uid)
                    joinChannel(userCount, uid)
                }
            } else {
                registerAccount { uid, userAccount ->
                    joinChannel(userAccount, uid)
                }
            }
        }
    }

    private var onLocalUserRegistered: ((uid: Int, userAccount: String) -> Unit)? = null

    private fun registerAccount(onLocalUserRegistered: ((uid: Int, userCount: String) -> Unit)) {
        if (RtcSettings.mEnableUserAccount) {
            val existUid = uidMapping[userAccount]
            if (existUid != null) {
                onLocalUserRegistered.invoke(existUid, userAccount)
            } else {
                this.onLocalUserRegistered = onLocalUserRegistered
                rtcEngine.registerLocalUserAccount(AgoraRtcEngineInstance.mAppId, userAccount)
            }
        } else {
            val uid = userAccount.toInt()
            uidMapping[userAccount] = uid
            val ownerUid = channelName.toInt()
            uidMapping[channelName] = ownerUid
            onLocalUserRegistered.invoke(uid, userAccount)
        }
    }

    /**推流到CDN*/
    private fun setRtmpStreamEnable(enable: Boolean) {
        // TODO: 暂停推 cdn
//        val userId = uidMapping[userAccount] ?: return
//        if (enable) {
//            // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
//            val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
//            if (publishedRtmp) {
//                AgoraRtcEngineInstance.transcoder.stopRtmpStream(userId, null)
//                publishedRtmp = false
//            }
//            AgoraRtcEngineInstance.transcoder.startRtmpStreamWithTranscoding(
//                TranscodeSetting.liveTranscoding(
//                    RtcSettings.mEnableUserAccount,
//                    userId,
//                    channelName,
//                    pushUrl,
//                    ChannelUid(channelName, userId, userAccount)
//                )
//            ) { succeed, code, message ->
//                if (succeed) {
//                    publishedRtmp = true
//                     ToastUtils.showShort("start rtmp stream success！")
//                } else {
//                     ToastUtils.showShort("start rtmp stream error, $code, $message")
//                }
//            }
//        } else {
//            // 删除一个推流地址。
//            AgoraRtcEngineInstance.transcoder.stopRtmpStream(userId) { succeed, code, message ->
//                if (succeed) {
//                    publishedRtmp = false
//                } else {
//                     ToastUtils.showShort("stop rtmp stream error, $code, $message")
//                }
//            }
//        }
    }

    /**新主播进来，更新CDN推流*/
    private fun updateRtmpStreamEnable(@Size(min = 1) vararg channelUids: ChannelUid) {
        // TODO: 暂停推 cdn
//        val userId = uidMapping[userAccount] ?: return
//        // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
//        val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
//        AgoraRtcEngineInstance.transcoder.updateRtmpTranscoding(
//            TranscodeSetting.liveTranscoding(
//                RtcSettings.mEnableUserAccount,
//                userId,
//                channelName,
//                pushUrl,
//                *channelUids
//            )
//        ) { succeed, code, message ->
//            if (succeed) {
//                publishedRtmp = true
//                 ToastUtils.showShort("update rtmp stream success！")
//            } else {
//                 ToastUtils.showShort("update rtmp stream error, $code, $message")
//            }
//        }
    }

    /**pk 模式,*/
    private fun updatePkMode(remotePkUid: Int) {
        val act = activity ?: return
        binding.videoPKLayout.videoContainer.isVisible = true
        binding.layoutVideoContainer.isVisible = false
        binding.layoutVideoContainer.removeAllViews()
        if (isOwner) { // 主播
            binding.videoPKLayout.iBroadcasterAView.removeAllViews()
            binding.videoPKLayout.iBroadcasterAView.addView(localTexture)

            uidMapping[userAccount]?.let { uid ->
                rtcEngine.setupLocalVideo(
                    VideoCanvas(localTexture, Constants.RENDER_MODE_ADAPTIVE, uid)
                )
            }

            val remoteTexture = TextureView(act)
            binding.videoPKLayout.iBroadcasterBView.removeAllViews()
            binding.videoPKLayout.iBroadcasterBView.addView(remoteTexture)

            // pk 频道主播
            remoteRtcConnection?.let {
                rtcEngine.setupRemoteVideoEx(
                    VideoCanvas(remoteTexture, Constants.RENDER_MODE_FIT, remotePkUid).apply {
                        mirrorMode = Constants.VIDEO_MIRROR_MODE_DISABLED
                    }, it
                )
            }

        } else {
            if (audienceStatus == AudienceStatus.RTC_Audience) {  // rtc 观众
                val remoteTextureA = TextureView(act)
                binding.videoPKLayout.iBroadcasterAView.removeAllViews()
                binding.videoPKLayout.iBroadcasterAView.addView(remoteTextureA)

                uidMapping[ownerAccount]?.let { ownerUid ->
                    rtcEngine.setupRemoteVideo(
                        VideoCanvas(remoteTextureA, Constants.RENDER_MODE_FIT, ownerUid).apply {
                            mirrorMode = Constants.VIDEO_MIRROR_MODE_DISABLED
                        }
                    )
                }

                val remoteTextureB = TextureView(act)
                binding.videoPKLayout.iBroadcasterBView.removeAllViews()
                binding.videoPKLayout.iBroadcasterBView.addView(remoteTextureB)

                remoteRtcConnection?.let {
                    rtcEngine.setupRemoteVideoEx(
                        VideoCanvas(remoteTextureB, Constants.RENDER_MODE_FIT, remotePkUid).apply {
                            mirrorMode = Constants.VIDEO_MIRROR_MODE_DISABLED
                        }, it
                    )
                }

            }
        }
    }

    /**单主播模式*/
    private fun updateIdleMode() {
        binding.videoPKLayout.videoContainer.isVisible = false
        binding.layoutVideoContainer.isVisible = true
        binding.videoPKLayout.iBroadcasterAView.removeAllViews()
        binding.videoPKLayout.iBroadcasterBView.removeAllViews()
        if (isOwner) {
            uidMapping[userAccount]?.let { uid ->
                setupLocalVideo(uid)
            }
        } else {
            uidMapping[channelName]?.let { uid ->
                setupRemoteVideo(uid)
            }
        }
    }

    private val localTexture by lazy {
        TextureView(requireActivity())
    }

    private fun setupLocalVideo(localUid: Int) {
        activity ?: return
        binding.layoutVideoContainer.removeAllViews()
        binding.layoutVideoContainer.addView(localTexture)
        rtcEngine.setupLocalVideo(
            VideoCanvas(localTexture, Constants.RENDER_MODE_FIT, localUid)
        )
    }

    private fun setupRemoteVideo(remoteUid: Int) {
        val act = activity ?: return
        val remoteTexture = TextureView(act)
        binding.layoutVideoContainer.removeAllViews()
        binding.layoutVideoContainer.addView(remoteTexture)

        rtcEngine.setupRemoteVideo(
            VideoCanvas(remoteTexture, Constants.RENDER_MODE_FIT, remoteUid).apply {
                mirrorMode = Constants.VIDEO_MIRROR_MODE_DISABLED
            }
        )
    }

    private fun joinChannel(userAccount: String, uid: Int) {
        val channelMediaOptions = ChannelMediaOptions()
        channelMediaOptions.clientRoleType = role
        channelMediaOptions.autoSubscribeVideo = true
        channelMediaOptions.autoSubscribeAudio = true
        channelMediaOptions.publishCameraTrack = isOwner
        channelMediaOptions.publishMicrophoneTrack = isOwner
        if (isOwner) {
            rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
//            rtcEngine.enableInstantMediaRendering() // 加速出图(加入频道前调用)
//            rtcEngine.setCameraAutoFocusFaceModeEnabled(true) // 开启人脸自动对焦(加入频道前调用)
//            rtcEngine.setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT) // audio profile ：default
//            rtcEngine.setAudioScenario(Constants.AUDIO_SCENARIO_DEFAULT) // scenario ：default
            updateVideoEncoder()
        } else {
//            rtcEngine.setParameters("\"rtc.video.enable_sr\":{\"enabled\":true, \"mode\": 2}")
//            rtcEngine.setParameters("\"rtc.video.sr_type\":20")
        }
//        rtcEngine.setParameters("{\"engine.video.enable_hw_encoder\":\"true\"}") // 硬编 （加入频道前调用）
//        rtcEngine.setParameters("{\"rtc.enable_early_data_for_vos\":\"false\"}") // 针对弱网链接优化（加入频道前调用）
        val ret1 = rtcEngine.setParameters("{\"engine.video.enable_hw_decoder\":\"true\"}")
        val ret2 = rtcEngine.setParameters("{\"engine.video.decoder_out_byte_frame\":\"true\"}")

        val score = rtcEngine.queryDeviceScore()
        Log.d(TAG, "queryDeviceScore $score")
        // 265
        // TODO: 注释 264 1  265 2
        rtcEngine.setParameters("{\"che.video.videoCodecIndex\":2}")
        rtcEngine.setDefaultAudioRoutetoSpeakerphone(true)
        if (RtcSettings.mEnableUserAccount) {
            rtcEngine.joinChannelWithUserAccount(null, channelName, userAccount, channelMediaOptions)
        } else {
            rtcEngine.joinChannel(null, channelName, uid, channelMediaOptions)
        }
    }

    private fun updateVideoEncoder() {
        if (isInPk && RtcSettings.mVideoDimensions == VideoEncoderConfiguration.VD_1920x1080) {
            val videoEncoderConfiguration = VideoEncoderConfiguration().apply {
                mirrorMode = VideoEncoderConfiguration.MIRROR_MODE_TYPE.MIRROR_MODE_DISABLED
                dimensions = VideoEncoderConfiguration.VD_1280x720
                frameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_24.value
            }
            val ret =
                rtcEngine.setParameters("{\"che.video.auto_adjust_resolution\":{\"auto_adjust_resolution_flag\":0}}")
            Log.d(TAG, "auto_adjust_resolution close $ret")
            rtcEngine.setVideoEncoderConfiguration(videoEncoderConfiguration)
        } else {
            if (RtcSettings.mVideoDimensionsAuto) {
                val ret =
                    rtcEngine.setParameters(
                        "{\"che.video" +
                                ".auto_adjust_resolution\":{\"auto_adjust_resolution_flag\":1,\"resolution_list\":\"1920x1080, 1280x720\", \"resolution_score\":\"90, 1\"}}"
                    )
                rtcEngine.setVideoEncoderConfiguration(AgoraRtcEngineInstance.videoEncoderConfiguration)
                Log.d(TAG, "auto_adjust_resolution $ret")
            } else {
                val ret =
                    rtcEngine.setParameters("{\"che.video.auto_adjust_resolution\":{\"auto_adjust_resolution_flag\":0}}")
                Log.d(TAG, "auto_adjust_resolution close $ret")
                rtcEngine.setVideoEncoderConfiguration(AgoraRtcEngineInstance.videoEncoderConfiguration)
            }
        }
    }

    override fun onDestroy() {
        if (isOwner) {
            stopSendRemoteChannel()
            setRtmpStreamEnable(false)
            stopPushToZego()
        }
        rtcEngine.leaveChannel()
        AgoraRtcEngineInstance.destroy()
        super.onDestroy()
    }

    private var remoteRtcConnection: RtcConnection? = null
    private fun startPk(remoteChannel: String) {
        val channelMediaOptions = ChannelMediaOptions()
        channelMediaOptions.clientRoleType = Constants.CLIENT_ROLE_AUDIENCE
        channelMediaOptions.autoSubscribeVideo = true
        channelMediaOptions.autoSubscribeAudio = true
        channelMediaOptions.publishCameraTrack = false
        channelMediaOptions.publishMicrophoneTrack = false
        channelMediaOptions.isInteractiveAudience = true
        channelMediaOptions.audienceLatencyLevel = Constants.AUDIENCE_LATENCY_LEVEL_ULTRA_LOW_LATENCY

        updateVideoEncoder()
        if (RtcSettings.mEnableUserAccount) {
            rtcEngine.joinChannelWithUserAccountEx(
                null,
                remoteChannel,
                userAccount,
                channelMediaOptions,
                rtcEventHandlerEx
            )
        } else {
            val uid = uidMapping[userAccount] ?: return
            rtcEngine.joinChannelEx(null, RtcConnection(remoteChannel, uid), channelMediaOptions, rtcEventHandlerEx)
        }
    }

    private val rtcEventHandlerEx = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            super.onJoinChannelSuccess(channel, uid, elapsed)
            runOnMainThread {
                remoteRtcConnection = RtcConnection(channel, uid)
            }
            LogTool.d(TAG, "remoteChannel onJoinChannelSuccess channel:$channel,uid:$uid,")
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
            LogTool.d(TAG, "remoteChannel onRemoteVideoStateChanged uid:$uid,state:$state,reason:$reason")
        }

        override fun onFirstRemoteVideoFrame(uid: Int, width: Int, height: Int, elapsed: Int) {
            super.onFirstRemoteVideoFrame(uid, width, height, elapsed)
            LogTool.d(TAG, "remoteChannel onFirstRemoteVideoFrame uid:$uid,width:$width,height:$height")
        }

        override fun onUserJoined(remoteUid: Int, elapsed: Int) {
            super.onUserJoined(remoteUid, elapsed)
            LogTool.d(TAG, "remoteChannel remoteChannel uid:$remoteUid")
            runOnMainThread {
                updatePkMode(remoteUid)
                if (isOwner) {
                    uidMapping[userAccount]?.let { ownerUid ->
                        val channelUid = ChannelUid(channelName, ownerUid, userAccount)

                        var remoteAccount = ""
                        uidMapping.forEach { fAccount, fUid ->
                            if (fUid == remoteUid) {
                                remoteAccount = fAccount
                            }
                        }
                        val remoteChannelUid = ChannelUid(remotePkChannel, remoteUid, remoteAccount)
                        val channelUids = arrayOf(channelUid, remoteChannelUid)
                        updateRtmpStreamEnable(*channelUids)
                    }
                }
            }
        }

        override fun onUserInfoUpdated(uid: Int, userInfo: UserInfo) {
            super.onUserInfoUpdated(uid, userInfo)
            LogTool.d(
                TAG,
                "remoteChannel onUserInfoUpdated uid:$uid,userInfo:${userInfo.uid}-${userInfo.userAccount}"
            )
            runOnMainThread {
                uidMapping[userInfo.userAccount] = userInfo.uid
            }
        }
    }

    private fun stopPk() {
        updateVideoEncoder()
        remoteRtcConnection?.let {
            rtcEngine.leaveChannelEx(it)
            remoteRtcConnection = null
        }
        updateIdleMode()
    }

    // 开始发送 remoteChannel
    @Volatile
    private var mStopRemoteChannel = true

    private val remoteChannelTask = object : Utils.Task<Boolean>(Utils.Consumer {
        if (mStopRemoteChannel) return@Consumer
        if (!isOwner) return@Consumer
        sendRemoteChannel(remotePkChannel)
    }) {
        override fun doInBackground(): Boolean {
            return true
        }
    }

    private fun startSendRemoteChannel() {
        mStopRemoteChannel = false
        ThreadUtils.executeBySingleAtFixRate(remoteChannelTask, 0, 1, TimeUnit.SECONDS)
    }

    // 停止发送remote channel
    private fun stopSendRemoteChannel() {
        mStopRemoteChannel = true
        remoteChannelTask.cancel()
    }

    private val dataStreamId: Int by lazy {
        rtcEngine.createDataStream(DataStreamConfig())
    }

    private fun sendRemoteChannel(remoteChannel: String) {
        val msg: MutableMap<String, Any?> = HashMap()
        if (remoteChannel.isEmpty()) {
            msg["cmd"] = "StopPk"
        } else {
            msg["cmd"] = "StartPk"
            msg["channel"] = remoteChannel
        }
        val jsonMsg = JSONObject(msg)
        rtcEngine.sendStreamMessage(dataStreamId, jsonMsg.toString().toByteArray())
    }

    private fun startPushToZego() {
        uidMapping[userAccount]?.let { ownerUid ->
            val channelUid = ChannelUid(channelName, ownerUid, userAccount)
            AgoraRtcEngineInstance.agoraZegoTranscoder.startAgoraStreamWithTranscoding(
                AgoraTranscodeSetting.cloudAgoraTranscoding(
                    enableUserAccount = RtcSettings.mEnableUserAccount,
                    zegoAppId = BuildConfig.ZEGO_APP_ID.toLong(),
                    zegoAppSign = BuildConfig.ZEGO_APP_SIGN,
                    zegoRoomId = channelName,
                    zegoUser = ZegoUser(ZegoEngineInstance.userId),
                    zegoPublishStreamId = channelName,
                    channelUid = channelUid
                ),
                completion = { succeed, code, message ->
                    if (succeed) {
                        ToastUtils.showShort("push to zego success")
                    } else {
                        ToastUtils.showShort("push to zego error: $code, $message")
                    }
                }
            )
        }
    }

    private fun stopPushToZego() {
        AgoraRtcEngineInstance.agoraZegoTranscoder.stopRtmpStream(userAccount, null)
    }
}