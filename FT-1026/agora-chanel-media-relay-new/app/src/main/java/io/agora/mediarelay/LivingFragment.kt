package io.agora.mediarelay

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.annotation.Size
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import io.agora.base.VideoFrame
import io.agora.mediaplayer.IMediaPlayer
import io.agora.mediaplayer.IMediaPlayerVideoFrameObserver
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentLivingBinding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.MPObserverAdapter
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.rtc.SeiHelper
import io.agora.mediarelay.rtc.transcoder.ChannelUid
import io.agora.mediarelay.rtc.transcoder.TranscodeSetting
import io.agora.mediarelay.tools.FileUtils
import io.agora.mediarelay.tools.GsonTools
import io.agora.mediarelay.tools.LogTool
import io.agora.mediarelay.tools.ThreadTool
import io.agora.mediarelay.tools.ToastTool
import io.agora.mediarelay.widget.DashboardFragment
import io.agora.mediarelay.tools.ViewTool
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.DataStreamConfig
import io.agora.rtc2.IMetadataObserver
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcConnection
import io.agora.rtc2.UserInfo
import io.agora.rtc2.video.ImageTrackOptions
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
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

    private var mediaPlayer: IMediaPlayer? = null

    /**
     * cdn 链接 码率 index
     * 0 720p, 1 1080p
     * [KeyCenter.mBitrateList]
     */
    private var cdnPosition = 0

    @Volatile
    private var audienceStatus: AudienceStatus = AudienceStatus.CDN_Audience

    private val isInPk
        get() = remotePkChannel.isNotEmpty()

    // pk 频道
    private var remotePkChannel = ""

    // 用户 account
    private val userAccount by lazy {
        KeyCenter.rtcAccount(isOwner, channelName)
    }

    // 房主 account
    private val ownerAccount
        get() = channelName

    //key account，value rtc-uid
    private val uidMapping = mutableMapOf<String, Int>()

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
            binding.layoutChannel.isVisible = true
            binding.btSubmitPk.isVisible = true
            binding.btSwitchStream.isVisible = false
            binding.btSwitchCarma.isVisible = true
            binding.btMuteMic.isVisible = true
            binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
            binding.btMuteCarma.isVisible = true
            binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
            binding.btBitrate.isVisible = false
        } else {
            binding.layoutChannel.isVisible = false
            binding.btSubmitPk.isVisible = false
            binding.btSwitchStream.isVisible = true
            binding.btSwitchCarma.isVisible = false
            binding.btMuteMic.isVisible = false
            binding.btMuteCarma.isVisible = false
            binding.btBitrate.isVisible = true
            binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
        }
        binding.tvChannelId.text = "Channel:$channelName"
        binding.btnBack.setOnClickListener {
            goBack()
        }
        binding.btSubmitPk.setOnClickListener {
            if (remotePkChannel.isNotEmpty()) { // pk 中停止pk
                binding.btSubmitPk.text = getString(R.string.start_pk)
                binding.etPkChannel.setText("")
                remotePkChannel = ""
                stopPk()
                uidMapping[userAccount]?.let { ownerUid ->
                    val channelUid = ChannelUid(channelName, ownerUid)
                    updateRtmpStreamEnable(channelUid)
                }
            } else {
                val channelId = binding.etPkChannel.text.toString()
                if (checkChannelId(channelId)) return@setOnClickListener
                remotePkChannel = channelId
                startPk(channelId)
            }
        }
        binding.btSwitchStream.setOnClickListener {
            when (audienceStatus) {
                AudienceStatus.CDN_Audience -> {// cdn 观众--> rtc 观众
                    audienceStatus = AudienceStatus.RTC_Audience
                    switchRtcAudience()
                }

                AudienceStatus.RTC_Audience -> { // rtc 观众 --> cdn 观众
                    audienceStatus = AudienceStatus.CDN_Audience
                    switchCdnAudience(cdnPosition)
                }

                AudienceStatus.RTC_Broadcaster -> {
                    // nothing
                }
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
        binding.btBitrate.setOnClickListener {
            val cxt = context ?: return@setOnClickListener
            val data = KeyCenter.mBitrateList
            ViewTool.showPop(cxt, binding.btBitrate, data, cdnPosition) { position, text ->
                tempCdnPosition = position
                val pullUrl = KeyCenter.getRtmpPullUrl(channelName, position)
                Log.d(TAG, "switchSrc $pullUrl")
                mediaPlayer?.switchSrc(pullUrl, false)
            }
        }
    }

    // 临时变量，切换成功则修改
    private var tempCdnPosition = -1

    private fun switchSrcSuccess(ret: Boolean) {
        runOnMainThread {
            if (ret) {
                cdnPosition = tempCdnPosition
                tempCdnPosition = -1
                if (cdnPosition >= 0 && cdnPosition < KeyCenter.mBitrateList.size) {
                    binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
                }
                ToastTool.showToast(R.string.switch_src_success)
            } else {
                ToastTool.showToast(R.string.switch_src_failed)
            }
        }
    }

    private var muteLocalAudio = false
    private var muteLocalVideo = false

    private val mediaPlayerObserver = object : MPObserverAdapter() {
        override fun onPlayerEvent(
            eventCode: io.agora.mediaplayer.Constants.MediaPlayerEvent?,
            elapsedTime: Long,
            message: String?
        ) {
            super.onPlayerEvent(eventCode, elapsedTime, message)
            Log.d(TAG, "onPlayerEvent: $eventCode，$elapsedTime,$message")
            when (eventCode) {
                io.agora.mediaplayer.Constants.MediaPlayerEvent.PLAYER_EVENT_SWITCH_COMPLETE -> {
                    switchSrcSuccess(true)
                }

                io.agora.mediaplayer.Constants.MediaPlayerEvent.PLAYER_EVENT_SWITCH_ERROR -> {
                    switchSrcSuccess(false)
                    Log.d(TAG, "getPlaySrc:${mediaPlayer?.playSrc}")
                }

                else -> {}
            }
        }

        override fun onPlayerStateChanged(
            state: io.agora.mediaplayer.Constants.MediaPlayerState?,
            error: io.agora.mediaplayer.Constants.MediaPlayerReason?
        ) {
            super.onPlayerStateChanged(state, error)
            if (state == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED) {
                mediaPlayer?.play()
            }
            if (error != io.agora.mediaplayer.Constants.MediaPlayerReason.PLAYER_REASON_NONE) {
                runOnMainThread {
                    ToastTool.showToast("$error")
                }
            }
            if (state == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYING) {
                mediaPlayer?.let {
                    val streamCount = it.streamCount
                    Log.d(TAG, "streamInfo: $streamCount")
                    for (i in 0 until streamCount) {
                        Log.d(TAG, "streamInfo: ${it.getStreamInfo(i)}")
                    }
                }
            }
        }

        override fun onMetaData(type: io.agora.mediaplayer.Constants.MediaPlayerMetadataType?, data: ByteArray?) {
            super.onMetaData(type, data)
            data?.let {
                Log.d(TAG, "onMetaData type:$type,data:${String(it)}")
            }
        }
    }

    private fun switchCdnAudience(cdnPosition: Int) {
        val act = activity ?: return
        val rtmpPullUrl = KeyCenter.getRtmpPullUrl(channelName, cdnPosition)
        binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]

        remoteRtcConnection?.let {
            rtcEngine.leaveChannelEx(it)
            remoteRtcConnection = null
        }
        remotePkChannel = ""
        rtcEngine.leaveChannel()

        binding.btSwitchStream.text = getString(R.string.rtc_audience)
        binding.layoutVideoContainer.isVisible = true
        binding.videoPKLayout.videoContainer.isVisible = false
        binding.btBitrate.isVisible = true

        val textureView = TextureView(act)
        binding.layoutVideoContainer.removeAllViews()
        binding.layoutVideoContainer.addView(textureView)
        mediaPlayer = rtcEngine.createMediaPlayer()
        mediaPlayer?.apply {
            setPlayerOption("is_live_source", 1);
            setPlayerOption("play_speed_down_cache_duration", 0)
            setPlayerOption("open_timeout_until_success", 6000)
            setPlayerOption("enable_search_metadata", 1)
            registerPlayerObserver(mediaPlayerObserver)
            setView(textureView)
            Log.d(TAG, "rtmpPullUrl $rtmpPullUrl")
            open(rtmpPullUrl, 0)
        }

    }

    //切换到rtc 观众
    private fun switchRtcAudience() {
        mediaPlayer?.let {
            it.unRegisterPlayerObserver(mediaPlayerObserver)
            it.stop()
            it.setView(null)
            it.destroy()
            mediaPlayer = null
        }

        binding.btSwitchStream.text = getString(R.string.cdn_audience)
        binding.layoutVideoContainer.isVisible = true
        binding.videoPKLayout.videoContainer.isVisible = false
        binding.btBitrate.isVisible = false

        registerAccount { uid, userAccount ->
            joinChannel(userAccount)
        }
    }

    private fun goBack() {
        findNavController().popBackStack()
    }

    private fun checkChannelId(channelId: String): Boolean {
        if (channelId.isEmpty()) {
            ToastTool.showToast("Please enter pk channel id")
            return true
        }
        return false
    }

    private val eventListener = IAgoraRtcClient.IChannelEventListener(
        onChannelJoined = { channel, uid ->
            if (isOwner) {
                startSendRemoteChannel()
                setRtmpStreamEnable(true, uid)
                startSendSei()
            } else {
                //超级画质
                val ret1 = rtcEngine.setParameters("{\"rtc.video.enable_sr\":{\"enabled\":true, \"mode\": 2}}")
                val ret2 = rtcEngine.setParameters("{\"rtc.video.sr_type\":20}")
                Log.d(TAG, "enable_sr ret：$ret1, sr_type ret：$ret2")
            }
        },
        onUserInfoUpdated = { uid, userInfo ->
            uidMapping[userInfo.userAccount] = userInfo.uid
            if (isOwner) {
                // nothing
            } else {
                if (channelName == userInfo.userAccount) {
                    // 当前房间主播
                    setupRemoteVideo(userInfo.uid)
                }
            }
        },
        onUserOffline = { offlineUid ->
        },

        onRtmpStreamingStateChanged = { url, state, code ->
            when (state) {
                Constants.RTMP_STREAM_PUBLISH_STATE_RUNNING -> {
                    if (code == Constants.RTMP_STREAM_PUBLISH_REASON_OK) {
                        ToastTool.showToast("rtmp stream publish state running")
                    }
                }

                Constants.RTMP_STREAM_PUBLISH_STATE_FAILURE -> {
                    ToastTool.showToast("rtmp stream publish state failure: $code")
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
                    joinChannel(userCount)
                }
            } else {
                switchCdnAudience(cdnPosition)
            }
        }
    }

    private var onLocalUserRegistered: ((uid: Int, userAccount: String) -> Unit)? = null

    private fun registerAccount(onLocalUserRegistered: ((uid: Int, userCount: String) -> Unit)) {
        val existUid = uidMapping[userAccount]
        if (existUid != null) {
            onLocalUserRegistered.invoke(existUid, userAccount)
        } else {
            this.onLocalUserRegistered = onLocalUserRegistered
            rtcEngine.registerLocalUserAccount(AgoraRtcEngineInstance.mAppId, userAccount)
        }
    }

    /**推流到CDN*/
    private fun setRtmpStreamEnable(enable: Boolean, uid: Int) {
        if (enable) {
            // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
            val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
            if (publishedRtmp) {
                AgoraRtcEngineInstance.transcoder.stopRtmpStream(null)
                publishedRtmp = false
            }
            AgoraRtcEngineInstance.transcoder.startRtmpStreamWithTranscoding(
                TranscodeSetting.liveTranscoding(
                    channelName,
                    pushUrl,
                    ChannelUid(channelName, uid)
                )
            ) { succeed ->
                if (succeed) {
                    publishedRtmp = true
                    ToastTool.showToast("rtmp stream publish state running")
                } else {
                    ToastTool.showToast("push rtmp stream error ！")
                }
            }
        } else {
            // 删除一个推流地址。
            AgoraRtcEngineInstance.transcoder.stopRtmpStream { succeed ->
                if (succeed) {
                    publishedRtmp = false
                } else {
                    ToastTool.showToast("stop rtmp stream error！")
                }
            }
        }
    }

    /**新主播进来，更新CDN推流*/
    private fun updateRtmpStreamEnable(@Size(min = 1) vararg channelUids: ChannelUid) {
        // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
        val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
        AgoraRtcEngineInstance.transcoder.updateRtmpTranscoding(
            TranscodeSetting.liveTranscoding(
                channelName,
                pushUrl,
                *channelUids
            )
        ) { succeed ->
            if (succeed) {
                publishedRtmp = true
            } else {
                ToastTool.showToast("update rtmp stream error ！")
            }
        }
    }

    /**pk 模式,*/
    private fun updatePkMode(remotePkUid: Int) {
        val act = activity ?: return
        binding.videoPKLayout.videoContainer.isVisible = true
        binding.layoutVideoContainer.isVisible = false
        binding.btSubmitPk.text = getString(R.string.stop_pk)
        if (isOwner) { // 主播
            val localTexture = TextureView(act)
            binding.videoPKLayout.iBroadcasterAView.removeAllViews()
            binding.videoPKLayout.iBroadcasterAView.addView(localTexture)

            uidMapping[userAccount]?.let { uid ->
                rtcEngine.setupLocalVideo(
                    VideoCanvas(localTexture, Constants.RENDER_MODE_ADAPTIVE, uid).apply {
                        mirrorMode = Constants.VIDEO_MIRROR_MODE_ENABLED
                    }
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
        binding.btSubmitPk.text = getString(R.string.start_pk)
        uidMapping[userAccount]?.let { uid ->
            setupLocalVideo(uid)
        }
    }

    private fun setupLocalVideo(localUid: Int) {
        val act = activity ?: return
        val localTexture = TextureView(act)
        binding.layoutVideoContainer.removeAllViews()
        binding.layoutVideoContainer.addView(localTexture)
        rtcEngine.setupLocalVideo(
            VideoCanvas(localTexture, Constants.RENDER_MODE_FIT, localUid).apply {
                mirrorMode = Constants.VIDEO_MIRROR_MODE_ENABLED
            }
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

    private fun joinChannel(userAccount: String) {
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
        val code: Int = rtcEngine.registerMediaMetadataObserver(iMetadataObserver, IMetadataObserver.VIDEO_METADATA)
        Log.d(TAG, "registerMediaMetadataObserver code:$code")
        rtcEngine.joinChannelWithUserAccount(null, channelName, userAccount, channelMediaOptions)
    }

    private fun updateVideoEncoder() {
        if (isInPk && RtcSettings.mVideoDimensions == VideoEncoderConfiguration.VD_1920x1080) {
            val videoEncoderConfiguration = VideoEncoderConfiguration().apply {
                dimensions = VideoEncoderConfiguration.VD_1280x720
                frameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_24.value
                mirrorMode = VideoEncoderConfiguration.MIRROR_MODE_TYPE.MIRROR_MODE_ENABLED
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
        val code: Int = rtcEngine.unregisterMediaMetadataObserver(iMetadataObserver, IMetadataObserver.VIDEO_METADATA)
        Log.d(TAG, "unregisterMediaMetadataObserver code:$code")
        if (isOwner) {
            stopSendSei()
            stopSendRemoteChannel()
            setRtmpStreamEnable(false, -1)
        } else {
            mediaPlayer?.apply {
                unRegisterPlayerObserver(mediaPlayerObserver)
                stop()
                destroy()
                mediaPlayer = null
            }
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

        updateVideoEncoder()
        rtcEngine.joinChannelWithUserAccountEx(null, remoteChannel, userAccount, channelMediaOptions, object :
            IRtcEngineEventHandler() {
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

            override fun onUserJoined(uid: Int, elapsed: Int) {
                super.onUserJoined(uid, elapsed)
                LogTool.d(TAG, "remoteChannel remoteChannel uid:$uid")
                runOnMainThread {
                    updatePkMode(uid)
                    if (isOwner) {
                        uidMapping[userAccount]?.let { ownerUid ->
                            val channelUid = ChannelUid(channelName, ownerUid)
                            val remoteChannelUid = ChannelUid(remoteChannel, uid)
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
        })
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
    private var remoteChannelFuture: ScheduledFuture<*>? = null
    private val remoteChannelTask = object : Runnable {
        override fun run() {
            if (mStopRemoteChannel) return
            if (!isOwner) return
            sendRemoteChannel(remotePkChannel)
        }
    }

    private fun startSendRemoteChannel() {
        mStopRemoteChannel = false
        remoteChannelFuture =
            ThreadTool.scheduledThreadPool.scheduleAtFixedRate(remoteChannelTask, 0, 1, TimeUnit.SECONDS)
    }

    // 停止发送remote channel
    private fun stopSendRemoteChannel() {
        mStopRemoteChannel = true
        remoteChannelFuture?.cancel(true)
        remoteChannelFuture = null
        if (ThreadTool.scheduledThreadPool is ScheduledThreadPoolExecutor) {
            ThreadTool.scheduledThreadPool.remove(remoteChannelTask)
        }
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

    private fun sendMetaSei() {
        val curUid = uidMapping[userAccount] ?: return
        val map = SeiHelper.buildSei(channelName, curUid)
        val jsonString = GsonTools.beanToString(map) ?: return
        metadata = jsonString.toByteArray()
    }

    // 开始发送 sei
    @Volatile
    private var mStopSei = true
    private var seiFuture: ScheduledFuture<*>? = null
    private val seiTask = object : Runnable {
        override fun run() {
            if (mStopSei) return
            if (isOwner) sendMetaSei()
        }
    }

    private fun startSendSei() {
        mStopSei = false
        seiFuture = ThreadTool.scheduledThreadPool.scheduleAtFixedRate(seiTask, 0, 1, TimeUnit.SECONDS)
    }

    // 停止发送 sei
    private fun stopSendSei() {
        mStopSei = true
        seiFuture?.cancel(true)
        seiFuture = null
        if (ThreadTool.scheduledThreadPool is ScheduledThreadPoolExecutor) {
            ThreadTool.scheduledThreadPool.remove(seiTask)
        }
    }

    private var metadata: ByteArray? = null

    private val iMetadataObserver: IMetadataObserver = object : IMetadataObserver {
        /**Returns the maximum data size of Metadata */
        override fun getMaxMetadataSize(): Int {
            return KeyCenter.MAX_META_SIZE
        }

        /**Occurs when the SDK is ready to receive and send metadata.
         * You need to specify the metadata in the return value of this callback.
         * @param timeStampMs The timestamp (ms) of the current metadata.
         * @return The metadata that you want to send in the format of byte[]. Ensure that you set the return value.
         * PS: Ensure that the size of the metadata does not exceed the value set in the getMaxMetadataSize callback.
         */
        override fun onReadyToSendMetadata(timeStampMs: Long, sourceType: Int): ByteArray? {
            /**Check if the metadata is empty. */

            if (metadata == null) {
                return null
            }
//            Log.i(TAG, "There is metadata to send!")
            /**Recycle metadata objects. */
            val toBeSend: ByteArray = metadata!!
            metadata = null
            if (toBeSend.size > KeyCenter.MAX_META_SIZE) {
                Log.e(TAG, String.format("Metadata exceeding max length %d!", KeyCenter.MAX_META_SIZE))
                return null
            }
            val data = String(toBeSend, Charset.forName("UTF-8"))
//            Log.i(TAG, String.format("Metadata sent successfully! The content is %s", data))
            return toBeSend
        }

        /**Occurs when the local user receives the metadata.
         * @param buffer The received metadata.
         * @param uid The ID of the user who sent the metadata.
         * @param timeStampMs The timestamp (ms) of the received metadata.
         */
        override fun onMetadataReceived(buffer: ByteArray, uid: Int, timeStampMs: Long) {
            val data = String(buffer, Charset.forName("UTF-8"))
//            Log.i(TAG, "onMetadataReceived:$data")
        }
    }
}