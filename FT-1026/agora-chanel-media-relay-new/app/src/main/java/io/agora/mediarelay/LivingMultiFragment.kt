package io.agora.mediarelay

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import androidx.core.util.forEach
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import io.agora.mediaplayer.IMediaPlayer
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentLivingMultiBinding
import io.agora.mediarelay.databinding.ViewVideoItemBinding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.MPObserverAdapter
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.rtc.SeiHelper
import io.agora.mediarelay.rtc.transcoder.TranscodeSetting
import io.agora.mediarelay.tools.FileUtils
import io.agora.mediarelay.tools.GsonTools
import io.agora.mediarelay.tools.ThreadTool
import io.agora.mediarelay.tools.ToastTool
import io.agora.mediarelay.widget.DashboardFragment
import io.agora.mediarelay.widget.OnFastClickListener
import io.agora.mediarelay.tools.ViewTool
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IMetadataObserver
import io.agora.rtc2.video.ImageTrackOptions
import io.agora.rtc2.video.VideoCanvas
import java.nio.charset.Charset
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * @author create by zhangwei03
 */
class LivingMultiFragment : BaseUiFragment<FragmentLivingMultiBinding>() {
    companion object {
        private const val TAG = "LivingFragment"

        const val KEY_CHANNEL_ID: String = "key_channel_id"
        const val KEY_ROLE: String = "key_role"
    }

    // 推流状态
    @Volatile
    private var publishedRtmp = false

    private val channelName by lazy { arguments?.getString(KEY_CHANNEL_ID) ?: "" }

    private val channelMediaOptions by lazy {
        ChannelMediaOptions()
    }

    // 默认进来的角色，第一个进来的主播是房主
    private val role by lazy {
        arguments?.getInt(KEY_ROLE, Constants.CLIENT_ROLE_BROADCASTER) ?: Constants.CLIENT_ROLE_BROADCASTER
    }

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

    // 房主
    private val isOwner: Boolean
        get() = role == Constants.CLIENT_ROLE_BROADCASTER

    // 用户 account
    private val userAccount by lazy {
        KeyCenter.rtcAccount(isOwner, channelName)
    }

    //key account，value rtc-uid
    private val uidMapping = mutableMapOf<String, Int>()

    private var videoAdapter: VideoAdapter? = null

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentLivingMultiBinding {
        return FragmentLivingMultiBinding.inflate(inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        initRtcEngine()
    }

    private fun initView() {
        if (isOwner) {
            binding.btLinking.isVisible = false
            binding.btSwitchStream.isVisible = false
            binding.btSwitchCarma.isVisible = true
            binding.btMuteMic.isVisible = true
            binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
            binding.btMuteCarma.isVisible = true
            binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
            binding.recyclerVideo.isVisible = true
            binding.layoutCdnContainer.isVisible = false
            binding.btBitrate.isVisible = false
        } else {
            binding.btLinking.isVisible = true
            binding.btSwitchStream.isVisible = true
            binding.btSwitchCarma.isVisible = false
            binding.btMuteMic.isVisible = false
            binding.btMuteCarma.isVisible = false
            // 默认 cdn 观众
            binding.recyclerVideo.isVisible = false
            binding.layoutCdnContainer.isVisible = true
            binding.btBitrate.isVisible = true
            binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
        }
        binding.tvChannelId.text = "ChannelId:$channelName"
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        // 观众连麦
        binding.btLinking.setOnClickListener(object : OnFastClickListener() {
            override fun onClickJacking(view: View) {
                ToastTool.showToast(R.string.changing_roles)
                when (audienceStatus) {
                    AudienceStatus.CDN_Audience -> { // cdn 观众--> rtc 主播
                        audienceStatus = AudienceStatus.RTC_Broadcaster
                        switchRtc(Constants.CLIENT_ROLE_BROADCASTER)
                        binding.btSwitchCarma.isVisible = true
                        binding.btMuteMic.isVisible = true
                        binding.btMuteCarma.isVisible = true
                        binding.btSwitchStream.isVisible = false
                        binding.btLinking.text = getString(R.string.hang_up)
                        rtcEngine.setEnableSpeakerphone(false)
                        rtcEngine.setEnableSpeakerphone(true)
                    }

                    AudienceStatus.RTC_Audience -> { // rtc 观众--> rtc 主播
                        audienceStatus = AudienceStatus.RTC_Broadcaster
                        channelMediaOptions.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                        channelMediaOptions.publishCameraTrack = true
                        channelMediaOptions.publishMicrophoneTrack = true
                        val ret = rtcEngine.updateChannelMediaOptions(channelMediaOptions)
                        Log.d("alien", "rtc 观众--> rtc 主播 ret:$ret")
                        binding.btSwitchCarma.isVisible = true
                        binding.btMuteMic.isVisible = true
                        binding.btMuteCarma.isVisible = true
                        binding.btSwitchStream.isVisible = false
                        binding.btLinking.text = getString(R.string.hang_up)
                        rtcEngine.setEnableSpeakerphone(false)
                        rtcEngine.setEnableSpeakerphone(true)
                    }

                    AudienceStatus.RTC_Broadcaster -> { // rtc 主播--> rtc 观众
                        audienceStatus = AudienceStatus.RTC_Audience
                        channelMediaOptions.clientRoleType = Constants.CLIENT_ROLE_AUDIENCE
                        channelMediaOptions.publishCameraTrack = false
                        channelMediaOptions.publishMicrophoneTrack = false
                        val ret = rtcEngine.updateChannelMediaOptions(channelMediaOptions)
                        Log.d("alien", "rtc 主播--> rtc 观众 ret:$ret")
                        binding.btSwitchCarma.isVisible = false
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }
                }
            }
        })
        binding.btSwitchStream.setOnClickListener(object :
            OnFastClickListener(1000, getString(R.string.click_too_fast)) {
            override fun onClickJacking(view: View) {
                when (audienceStatus) {
                    AudienceStatus.CDN_Audience -> { // cdn 观众--> rtc 观众
                        audienceStatus = AudienceStatus.RTC_Audience
                        switchRtc(Constants.CLIENT_ROLE_AUDIENCE)
                        binding.btSwitchCarma.isVisible = false
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }

                    AudienceStatus.RTC_Audience -> { // rtc 观众--> cdn 观众
                        audienceStatus = AudienceStatus.CDN_Audience
                        switchCdnAudience(cdnPosition)
                        binding.btSwitchCarma.isVisible = false
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }

                    AudienceStatus.RTC_Broadcaster -> { // rtc 主播--> cdn 观众
                        audienceStatus = AudienceStatus.CDN_Audience
                        switchCdnAudience(cdnPosition)
                        binding.btSwitchCarma.isVisible = false
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }
                }
            }
        })
        binding.btSwitchCarma.setOnClickListener(object :
            OnFastClickListener(1000, getString(R.string.click_too_fast)) {
            override fun onClickJacking(view: View) {
                rtcEngine.switchCamera()
            }
        })
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
                mediaPlayer?.switchSrc(KeyCenter.getRtmpPullUrl(channelName, position), false)
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
                }

                else -> {}
            }
        }

        override fun onPlayerStateChanged(
            state: io.agora.mediaplayer.Constants.MediaPlayerState?,
            error: io.agora.mediaplayer.Constants.MediaPlayerReason?
        ) {
            super.onPlayerStateChanged(state, error)
            Log.d(TAG, "onPlayerStateChanged: $state，$error")
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
    }

    var mpkTextureView: TextureView? = null

    private fun switchCdnAudience(cdnPosition: Int) {
        val act = activity ?: return
        val rtmpPullUrl = KeyCenter.getRtmpPullUrl(channelName, cdnPosition)
        binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
        rtcEngine.leaveChannel()
        videoAdapter?.mVideoList?.forEach { key, value ->
            if (value == uidMapping[userAccount]) {
                rtcEngine.setupLocalVideo(null);
            } else {
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(null, Constants.RENDER_MODE_FIT, value)
                )
            }
        }
        binding.btSwitchStream.text = getString(R.string.rtc_audience)

        mpkTextureView = TextureView(act)
        binding.layoutCdnContainer.removeAllViews()
        binding.layoutCdnContainer.addView(mpkTextureView)
        binding.layoutCdnContainer.isVisible = true
        binding.recyclerVideo.isVisible = false
        binding.btBitrate.isVisible = true
        mediaPlayer = rtcEngine.createMediaPlayer()
        mediaPlayer?.apply {
            setPlayerOption("is_live_source", 1);
            setPlayerOption("play_speed_down_cache_duration", 0)
            setPlayerOption("open_timeout_until_success", 6000)
            registerPlayerObserver(mediaPlayerObserver)
            setView(mpkTextureView)
            open(rtmpPullUrl, 0)
        }
    }

    //切换到rtc 观众
    private fun switchRtc(role: Int) {
        mediaPlayer?.let {
            it.unRegisterPlayerObserver(mediaPlayerObserver)
            it.stop()
            it.setView(null)
            it.destroy()
            mediaPlayer = null
            mpkTextureView = null
        }
        binding.btSwitchStream.text = getString(R.string.cdn_audience)
        binding.layoutCdnContainer.removeAllViews()
        binding.layoutCdnContainer.isVisible = false
        binding.recyclerVideo.isVisible = true
        binding.btBitrate.isVisible = false
        registerAccount { uid, userAccount ->
            joinChannel(userAccount, uid, role)
        }
    }

    private val eventListener = IAgoraRtcClient.IChannelEventListener(
        onChannelJoined = { channel, uid ->
            if (audienceStatus == AudienceStatus.RTC_Broadcaster) { // 非房主加入空位置
                startSendSei()
                if (muteLocalAudio) {
                    binding.btMuteMic.setImageResource(R.drawable.ic_mic_off)
                    rtcEngine.muteLocalAudioStream(true)
                } else {
                    binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                    rtcEngine.muteLocalAudioStream(false)
                }
                if (muteLocalVideo) {
                    binding.btMuteCarma.setImageResource(R.drawable.ic_camera_off)
                    rtcEngine.muteLocalAudioStream(true)
                } else {
                    binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
                    rtcEngine.muteLocalAudioStream(false)
                }
                videoAdapter?.apply {
                    val existIndex = mVideoList.indexOfValue(uid)
                    if (existIndex != -1) return@IChannelEventListener
                    val emptyIndex = mVideoList.indexOfValue(-1)
                    if (emptyIndex == -1) return@IChannelEventListener
                    mVideoList.put(emptyIndex, uid)
                    notifyItemChanged(emptyIndex)
                }
            }
            if (isOwner) {
                setRtmpStreamEnable(true)
                startSendSei()
            } else {
                //超级画质
                if (audienceStatus == AudienceStatus.RTC_Audience) {
                    val ret1 = rtcEngine.setParameters("{\"rtc.video.enable_sr\":{\"enabled\":true, \"mode\": 2}}")
                    val ret2 = rtcEngine.setParameters("{\"rtc.video.sr_type\":20}")
                    Log.d(TAG, "enable_sr ret：$ret1, sr_type ret：$ret2")
                }
            }
        },
        onLeaveChannel = {
            if (!isOwner) { // 非房主加入空位置
                videoAdapter?.apply {
                    mVideoList.forEach(action = { key, value ->
                        mVideoList.put(key, -1)
                    })
                    notifyDataSetChanged()
                }
            }
        },
        onUserInfoUpdated = { uid, userInfo ->
            uidMapping[userInfo.userAccount] = userInfo.uid
        },
        onUserJoined = { uid ->
            videoAdapter?.apply {
                val existIndex = mVideoList.indexOfValue(uid)
                if (existIndex != -1) return@IChannelEventListener
                val emptyIndex = mVideoList.indexOfValue(-1)
                if (emptyIndex == -1) return@IChannelEventListener
                mVideoList.put(emptyIndex, uid)
                notifyItemChanged(emptyIndex)
                if (isOwner) {
                    updateRtmpStreamEnable()
                }
            }
        },
        onUserOffline = {
            videoAdapter?.apply {
                val index = mVideoList.indexOfValue(it)
                if (index == -1) return@IChannelEventListener
                mVideoList.put(index, -1)
                notifyItemChanged(index)
                if (isOwner) {
                    updateRtmpStreamEnable()
                }
            }
        },

        onClientRoleChanged = { oldRole, newRole, newRoleOptions ->
            // 忽略房主
            if (isOwner) return@IChannelEventListener
            if (audienceStatus == AudienceStatus.RTC_Broadcaster) { // 非房主加入空位置
                startSendSei()
                if (muteLocalAudio) {
                    binding.btMuteMic.setImageResource(R.drawable.ic_mic_off)
                    rtcEngine.muteLocalAudioStream(true)
                } else {
                    binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                    rtcEngine.muteLocalAudioStream(false)
                }
                if (muteLocalVideo) {
                    binding.btMuteCarma.setImageResource(R.drawable.ic_camera_off)
                    rtcEngine.stopPreview()
                } else {
                    binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
                    rtcEngine.startPreview()
                }
                videoAdapter?.apply {
                    val curUid = uidMapping[userAccount] ?: return@IChannelEventListener
                    val existIndex = mVideoList.indexOfValue(curUid)
                    if (existIndex != -1) return@IChannelEventListener
                    val emptyIndex = mVideoList.indexOfValue(-1)
                    if (emptyIndex == -1) return@IChannelEventListener
                    mVideoList.put(emptyIndex, curUid)
                    notifyItemChanged(emptyIndex)
                }
            } else {
                videoAdapter?.apply {
                    val curUid = uidMapping[userAccount] ?: return@IChannelEventListener
                    // 是否已经在麦位上
                    val existIndex = mVideoList.indexOfValue(curUid)
                    if (existIndex == -1) return@IChannelEventListener
                    mVideoList.put(existIndex, -1)
                    notifyItemChanged(existIndex)
                }
                stopSendSei()
            }
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
    )

    private fun initRtcEngine() {
        checkRequirePerms {
            AgoraRtcEngineInstance.eventListener = eventListener
            val act = activity ?: return@checkRequirePerms
            val videoItem = SparseIntArray()
            for (i in 0 until 16) {
                videoItem.put(i, -1)
            }
            if (isOwner) {
                registerAccount { uid, userCount ->
                    videoItem.put(0, uid)
                    videoAdapter = VideoAdapter(act, videoItem)
                    binding.recyclerVideo.adapter = videoAdapter
                    joinChannel(userCount, uid, Constants.CLIENT_ROLE_BROADCASTER)
                }
            } else {
                // 默认 cdn 观众
                switchCdnAudience(cdnPosition)
                videoAdapter = VideoAdapter(act, videoItem)
                binding.recyclerVideo.adapter = videoAdapter
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
        if (enable) {
            // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
            val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
            if (publishedRtmp) {
                AgoraRtcEngineInstance.transcoder.stopRtmpStream(null)
                publishedRtmp = false
            }
            val adapter = videoAdapter ?: return
            AgoraRtcEngineInstance.transcoder.startRtmpStreamWithTranscoding(
                TranscodeSetting.liveTranscodingMulti(
                    channelName,
                    pushUrl,
                    adapter.mVideoList
                )
            ) { succeed, code, message ->
                Log.d(TAG, "startRtmpStreamWithTranscoding ret = $succeed")
                if (succeed) {
                    publishedRtmp = true
                    ToastTool.showToast("start rtmp stream success！")
                } else {
                    ToastTool.showToast("start rtmp stream error, $code, $message")
                }
            }
        } else {
            // 删除一个推流地址。
            AgoraRtcEngineInstance.transcoder.stopRtmpStream { succeed, code, message ->
                if (succeed) {
                    publishedRtmp = false
                } else {
                    ToastTool.showToast("stop rtmp stream error, $code, $message")
                }
            }
        }
    }

    /**新主播进来，更新CDN推流*/
    private fun updateRtmpStreamEnable() {
        // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
        val videoUids = videoAdapter?.mVideoList ?: return
        val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
        AgoraRtcEngineInstance.transcoder.updateRtmpTranscoding(
            TranscodeSetting.liveTranscodingMulti(
                channelName,
                pushUrl,
                videoUids
            )
        ) { succeed, code, message ->
            Log.d(TAG, "startRtmpStreamWithTranscoding ret = $succeed")
            if (succeed) {
                publishedRtmp = true
                ToastTool.showToast("update rtmp stream success！")
            } else {
                ToastTool.showToast("update rtmp stream error, $code, $message")
            }
        }
    }

    private fun joinChannel(userAccount: String, uid: Int, role: Int) {
        channelMediaOptions.clientRoleType = role
        channelMediaOptions.autoSubscribeVideo = true
        channelMediaOptions.autoSubscribeAudio = true
        channelMediaOptions.publishCameraTrack = role == Constants.CLIENT_ROLE_BROADCASTER
        channelMediaOptions.publishMicrophoneTrack = role == Constants.CLIENT_ROLE_BROADCASTER
        rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)

        updateVideoEncoder()
//        val score = rtcEngine.queryDeviceScore()
//        Log.d(TAG, "queryDeviceScore $score")
        // TODO: 注释 264 1  265 2
        rtcEngine.setParameters("{\"che.video.videoCodecIndex\":2}")
        rtcEngine.setDefaultAudioRoutetoSpeakerphone(true)
        val code: Int = rtcEngine.registerMediaMetadataObserver(iMetadataObserver, IMetadataObserver.VIDEO_METADATA)
        Log.d(TAG, "registerMediaMetadataObserver code:$code")
        if (RtcSettings.mEnableUserAccount) {
            rtcEngine.joinChannelWithUserAccount(null, channelName, userAccount, channelMediaOptions)
        } else {
            rtcEngine.joinChannel(null, channelName, uid, channelMediaOptions)
        }
    }

    private fun updateVideoEncoder() {
        rtcEngine.setVideoEncoderConfiguration(AgoraRtcEngineInstance.videoEncoderConfiguration)
    }

    override fun onDestroy() {
        val code: Int = rtcEngine.unregisterMediaMetadataObserver(iMetadataObserver, IMetadataObserver.VIDEO_METADATA)
        Log.d(TAG, "unregisterMediaMetadataObserver code:$code")
        if (isOwner || audienceStatus == AudienceStatus.RTC_Broadcaster) {
            stopSendSei()
        }
        if (isOwner) {
            setRtmpStreamEnable(false)
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


    inner class VideoAdapter constructor(
        private val mContext: Context,
        val mVideoList: SparseIntArray
    ) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

        private val mTextureVideos = mutableMapOf<Int, TextureView>()

        inner class VideoViewHolder(val binding: ViewVideoItemBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
            return VideoViewHolder(ViewVideoItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int {
            return mVideoList.size()
        }

        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
            val uid = mVideoList[position]
            if (uid == -1) {
                mTextureVideos.remove(position)
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(null, Constants.RENDER_MODE_FIT, uid)
                )
                if (holder.binding.flVideo.childCount > 0) holder.binding.flVideo.removeAllViews()
            } else {
                var textureView = mTextureVideos[position]
                if (textureView == null) {
                    textureView = TextureView(mContext).apply {
                        mTextureVideos[position] = this
                    }
                } else {
                    ((textureView.parent) as ViewGroup).removeAllViews()
                }
                if (uid == uidMapping[userAccount]) {
                    rtcEngine.setupLocalVideo(
                        VideoCanvas(textureView, Constants.RENDER_MODE_FIT, uid).apply {
                            mirrorMode = Constants.VIDEO_MIRROR_MODE_ENABLED
                        }
                    )
                } else {
                    rtcEngine.setupRemoteVideo(
                        VideoCanvas(textureView, Constants.RENDER_MODE_FIT, uid).apply {
                            mirrorMode = Constants.VIDEO_MIRROR_MODE_DISABLED
                        }
                    )
                }
                if (holder.binding.flVideo.childCount > 0) holder.binding.flVideo.removeAllViews()
                holder.binding.flVideo.addView(textureView)
            }
        }
    }

    private fun sendMetaSei() {
        val curUid = uidMapping[userAccount] ?: return
        val map = SeiHelper.buildSei(channelName, curUid)
        val jsonString = GsonTools.beanToString(map) ?: return
        metadata = jsonString.toByteArray()
    }

    // 开始发送 sei
    private var mStopSei = true
    private var seiFuture: ScheduledFuture<*>? = null
    private val seiTask = object : Runnable {
        override fun run() {
            if (mStopSei) return
            if (isOwner || audienceStatus == AudienceStatus.RTC_Broadcaster) sendMetaSei()
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
            Log.i(TAG, "There is metadata to send!")
            /**Recycle metadata objects. */
            val toBeSend: ByteArray = metadata!!
            metadata = null
            if (toBeSend.size > KeyCenter.MAX_META_SIZE) {
                Log.e(TAG, String.format("Metadata exceeding max length %d!", KeyCenter.MAX_META_SIZE))
                return null
            }
            val data = String(toBeSend, Charset.forName("UTF-8"))
            Log.i(TAG, String.format("Metadata sent successfully! The content is %s", data))
            return toBeSend
        }

        /**Occurs when the local user receives the metadata.
         * @param buffer The received metadata.
         * @param uid The ID of the user who sent the metadata.
         * @param timeStampMs The timestamp (ms) of the received metadata.
         */
        override fun onMetadataReceived(buffer: ByteArray, uid: Int, timeStampMs: Long) {
            val data = String(buffer, Charset.forName("UTF-8"))
            Log.i(TAG, "onMetadataReceived:$data")
        }
    }
}