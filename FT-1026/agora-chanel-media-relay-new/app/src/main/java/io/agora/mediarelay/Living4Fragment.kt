package io.agora.mediarelay

import android.os.Bundle
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import androidx.core.util.forEach
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import io.agora.mediaplayer.IMediaPlayer
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentLiving4Binding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.MPObserverAdapter
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.rtc.SeiHelper
import io.agora.mediarelay.rtc.transcoder.ChannelUid
import io.agora.mediarelay.rtc.transcoder.TranscodeSetting
import io.agora.mediarelay.tools.FileUtils
import io.agora.mediarelay.tools.GsonTools
import io.agora.mediarelay.tools.ThreadTool
import io.agora.mediarelay.tools.TimeUtils
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
class Living4Fragment : BaseUiFragment<FragmentLiving4Binding>() {
    companion object {
        private const val TAG = "Living3Fragment"

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

    // 房主 account
    private val ownerAccount get() = channelName

    //key account，value rtc-uid
    private val uidMapping = mutableMapOf<String, Int>()

    private val mVideoList: SparseIntArray = SparseIntArray()

    private var frontCamera = true

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentLiving4Binding {
        return FragmentLiving4Binding.inflate(inflater)
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
            binding.btMuteMic.isVisible = true
            binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
            binding.btMuteCarma.isVisible = true
            binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
            binding.videosLayout.videoContainer.isVisible = true
            binding.layoutCdnContainer.isVisible = false
            binding.btBitrate.isVisible = false
            binding.btAlphaGift.isVisible = true
        } else {
            binding.btLinking.isVisible = true
            binding.btSwitchStream.isVisible = true
            binding.btMuteMic.isVisible = false
            binding.btMuteCarma.isVisible = false
            // 默认 cdn 观众
            binding.videosLayout.videoContainer.isVisible = false
            binding.layoutCdnContainer.isVisible = true
            binding.btBitrate.isVisible = true
            binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
            binding.btAlphaGift.isVisible = false
        }
        binding.tvChannelId.text = "$channelName(${KeyCenter.cdnMakes})"
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
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }

                    AudienceStatus.RTC_Audience -> { // rtc 观众--> cdn 观众
                        audienceStatus = AudienceStatus.CDN_Audience
                        switchCdnAudience(cdnPosition)
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }

                    AudienceStatus.RTC_Broadcaster -> { // rtc 主播--> cdn 观众
                        audienceStatus = AudienceStatus.CDN_Audience
                        switchCdnAudience(cdnPosition)
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }
                }
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
                mediaPlayer?.switchSrc(KeyCenter.getRtmpPullUrl(channelName, position), true)
            }
        }
        binding.btAlphaGift.setOnClickListener {
            val cxt = context ?: return@setOnClickListener
            val alphaGiftList = KeyCenter.alphaGiftList
            val data: Array<String?> = arrayOfNulls<String>(alphaGiftList.size)

            for (i in alphaGiftList.indices) {
                data[i] = alphaGiftList[i].name
            }
            ViewTool.showPop(cxt, binding.btAlphaGift, data, giftPosition) { position, text ->
                tempGiftPosition = position
                showGiftTexture()
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

        override fun onMetaData(type: io.agora.mediaplayer.Constants.MediaPlayerMetadataType?, data: ByteArray?) {
            super.onMetaData(type, data)
            data ?: return
            val seiMap = GsonTools.strToMap(String(data))
            runOnMainThread {
                seiMap["ts"]?.let { ts ->
                    if (ts is Long) {
                        binding.cdnDiffTime.text = "diff:${TimeUtils.currentTimeMillis() - ts}ms"
                    } else if (ts is Int) {
                        binding.cdnDiffTime.text = "diff:${TimeUtils.currentTimeMillis() - ts}ms"
                    }
                }
            }
        }
    }

    private fun switchCdnAudience(cdnPosition: Int) {
        val act = activity ?: return
        val rtmpPullUrl = KeyCenter.getRtmpPullUrl(channelName, cdnPosition)
        binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
        rtcEngine.leaveChannel()
        mVideoList.forEach { key, value ->
            if (value == uidMapping[userAccount]) {
                rtcEngine.setupLocalVideo(null);
            } else {
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(null, Constants.RENDER_MODE_FIT, value)
                )
            }
        }

        binding.btSwitchStream.text = getString(R.string.rtc_audience)

        val textureView = TextureView(act)
        binding.layoutCdnContainer.removeAllViews()
        binding.layoutCdnContainer.addView(textureView)
        binding.layoutCdnContainer.isVisible = true
        binding.videosLayout.videoContainer.isVisible = false
        binding.btBitrate.isVisible = true
        mediaPlayer = rtcEngine.createMediaPlayer()
        mediaPlayer?.apply {
            setPlayerOption("is_live_source", 1);
            setPlayerOption("play_speed_down_cache_duration", 0)
            setPlayerOption("open_timeout_until_success", 6000)
            setPlayerOption("enable_search_metadata", 1)
            if (RtcSettings.mEnableQuic) {
                setPlayerOption("enable_quic", 1)
            }
            registerPlayerObserver(mediaPlayerObserver)
            setView(textureView)
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
        }
        binding.cdnDiffTime.text = ""
        binding.btSwitchStream.text = getString(R.string.cdn_audience)
        binding.layoutCdnContainer.removeAllViews()
        binding.layoutCdnContainer.isVisible = false
        binding.videosLayout.videoContainer.isVisible = true
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
                val curUid = uidMapping[userAccount] ?: return@IChannelEventListener
                val existIndex = mVideoList.indexOfValue(curUid)
                if (existIndex != -1) return@IChannelEventListener
                val emptyIndex = fetchValidIndex(curUid)
                if (emptyIndex == -1) return@IChannelEventListener
                mVideoList.put(emptyIndex, curUid)
                notifyItemChanged(emptyIndex)
            }
            if (isOwner) {
                startSendSei()
                setRtmpStreamEnable(true)
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
                mVideoList.forEach(action = { key, value ->
                    mVideoList.put(key, -1)
                })
                notifyDataSetChanged()
            }
        },
        onUserInfoUpdated = { uid, userInfo ->
            uidMapping[userInfo.userAccount] = userInfo.uid
        },
        onUserJoined = { uid ->
            val existIndex = mVideoList.indexOfValue(uid)
            if (existIndex != -1) return@IChannelEventListener
            val emptyIndex = fetchValidIndex(uid)
            if (emptyIndex == -1) return@IChannelEventListener
            if (!RtcSettings.mEnableUserAccount){
                uidMapping[uid.toString()]= uid
            }
            mVideoList.put(emptyIndex, uid)
            notifyItemChanged(emptyIndex)
            if (isOwner) {
                updateRtmpStreamEnable()
            }
        },
        onUserOffline = {
            val index = mVideoList.indexOfValue(it)
            if (index == -1) return@IChannelEventListener
            mVideoList.put(index, -1)
            notifyItemChanged(index)
            if (isOwner) {
                updateRtmpStreamEnable()
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
                val curUid = uidMapping[userAccount] ?: return@IChannelEventListener
                val existIndex = mVideoList.indexOfValue(curUid)
                if (existIndex != -1) return@IChannelEventListener
                val emptyIndex = fetchValidIndex(curUid)
                if (emptyIndex == -1) return@IChannelEventListener
                mVideoList.put(emptyIndex, curUid)
                notifyItemChanged(emptyIndex)
            } else {
                // 是否已经在麦位上
                val curUid = uidMapping[userAccount] ?: return@IChannelEventListener
                val existIndex = mVideoList.indexOfValue(curUid)
                if (existIndex == -1) return@IChannelEventListener
                mVideoList.put(existIndex, -1)
                notifyItemChanged(existIndex)
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
            for (i in 0 until 4) {
                mVideoList.put(i, -1)
            }
            if (isOwner) {
                registerAccount { uid, userAccount ->
                    mVideoList.put(0, uid)
                    notifyItemChanged(0)
                    joinChannel(userAccount, uid, Constants.CLIENT_ROLE_BROADCASTER)
                }
            } else {
                // 默认 cdn 观众
                switchCdnAudience(cdnPosition)
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
        val userId = uidMapping[userAccount] ?: return
        if (enable) {
            // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
            val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
            if (publishedRtmp) {
                AgoraRtcEngineInstance.transcoder.stopRtmpStream(userId, null)
                publishedRtmp = false
            }
            val channelUids = mutableListOf<ChannelUid>()
            mVideoList.forEach { key, uid ->
                getKey(uidMapping, uid)?.let {
                    channelUids.add(ChannelUid(channelName, uid, it))
                }
            }
            AgoraRtcEngineInstance.transcoder.startRtmpStreamWithTranscoding(
                TranscodeSetting.liveTranscoding4(
                    RtcSettings.mEnableUserAccount,
                    userId,
                    channelName,
                    pushUrl,
                    channelUids
                )
            ) { succeed, code, message ->
                if (succeed) {
                    publishedRtmp = true
                    ToastTool.showToast("start rtmp stream success！")
                } else {
                    ToastTool.showToast("start rtmp stream error, $code, $message")
                }
            }
        } else {
            // 删除一个推流地址。
            AgoraRtcEngineInstance.transcoder.stopRtmpStream(userId) { succeed, code, message ->
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
        val userId = uidMapping[userAccount] ?: return
        // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
        val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
        val channelUids = mutableListOf<ChannelUid>()
        mVideoList.forEach { key, uid ->
            getKey(uidMapping, uid)?.let {
                channelUids.add(ChannelUid(channelName, uid, it))
            }
        }
        AgoraRtcEngineInstance.transcoder.updateRtmpTranscoding(
            TranscodeSetting.liveTranscoding4(
                RtcSettings.mEnableUserAccount,
                userId,
                channelName,
                pushUrl,
                channelUids
            )
        ) { succeed, code, message ->
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
        // 265
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

    private fun fetchValidIndex(uid: Int): Int {
        if (uid == uidMapping[ownerAccount]) {
            return 0
        } else {
            for (i in 1 until mVideoList.size()) {
                if (mVideoList.valueAt(i) == -1) {
                    return i
                }
            }
        }
        return -1
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

    private fun notifyDataSetChanged() {
        mVideoList.forEach { key, value ->
            notifyItemChanged(key)
        }
    }

    private val mTextureVideos = mutableMapOf<Int, TextureView>()
    private fun notifyItemChanged(position: Int) {
        val videoContainer = when (position) {
            0 -> binding.videosLayout.iBroadcasterAView
            1 -> binding.videosLayout.iBroadcasterBView
            2 -> binding.videosLayout.iBroadcasterCView
            3 -> binding.videosLayout.iBroadcasterDView
            else -> binding.videosLayout.iBroadcasterAView
        }
        val uid = mVideoList[position]
        if (uid == -1) {
            mTextureVideos.remove(position)
            rtcEngine.setupRemoteVideo(VideoCanvas(null, Constants.RENDER_MODE_HIDDEN, uid))
            if (videoContainer.childCount > 0) videoContainer.removeAllViews()
        } else {
            var textureView = mTextureVideos[position]
            if (textureView == null) {
                val act = activity ?: return
                textureView = TextureView(act).apply {
                    mTextureVideos[position] = this
                }
            } else {
                ((textureView.parent) as ViewGroup).removeAllViews()
            }
            if (uid == uidMapping[userAccount]) {
                rtcEngine.setupLocalVideo(
                    VideoCanvas(textureView, Constants.RENDER_MODE_HIDDEN, uid)
                )
            } else {
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(textureView, Constants.RENDER_MODE_HIDDEN, uid).apply {
                        mirrorMode = Constants.VIDEO_MIRROR_MODE_DISABLED
                    }
                )
            }
            if (videoContainer.childCount > 0) videoContainer.removeAllViews()
            videoContainer.addView(textureView)
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

    private var giftPosition: Int = -1

    // 临时变量，切换成功则修改
    private var tempGiftPosition = -1

    private fun switchGiftSuccess(ret: Boolean) {
        runOnMainThread {
            if (ret) {
                giftPosition = tempGiftPosition
                tempGiftPosition = -1
                if (giftPosition >= 0 && giftPosition < KeyCenter.alphaGiftList.size) {
                    binding.btAlphaGift.text = KeyCenter.alphaGiftList[giftPosition].name
                }
                ToastTool.showToast(R.string.play_gift_success)
            } else {
                ToastTool.showToast(R.string.play_gift_failed)
            }
        }
    }

    private val localGiftTexture by lazy {
        TextureView(requireActivity()).apply {
            isOpaque = false
        }
    }

    private var giftMediaPlayer: IMediaPlayer? = null

    private fun showGiftTexture() {
//        val localTexture = mTextureVideos[0] ?: return
//        val localContainter = (localTexture.parent as? ViewGroup) ?: return
        val localContainter = binding.root
        val giftUrl = KeyCenter.alphaGiftList[tempGiftPosition].url
        localContainter.removeView(localGiftTexture)
        val childCount = localContainter.childCount
        localContainter.addView(localGiftTexture, childCount)
        giftMediaPlayer = rtcEngine.createMediaPlayer()
        giftMediaPlayer?.apply {
            val mode = KeyCenter.alphaGiftList[tempGiftPosition].mode
            setPlayerOption("alpha_stitch_mode", mode)
            registerPlayerObserver(giftMediaPlayerObserver)
            setView(localGiftTexture)
            open(giftUrl, 0)
        }
    }

    private val giftMediaPlayerObserver = object : MPObserverAdapter() {

        override fun onPlayerStateChanged(
            state: io.agora.mediaplayer.Constants.MediaPlayerState?,
            error: io.agora.mediaplayer.Constants.MediaPlayerReason?
        ) {
            super.onPlayerStateChanged(state, error)
            Log.d(TAG, "gift onPlayerStateChanged: $state，$error")
            when (state) {
                io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED -> {
                    giftMediaPlayer?.play()
                }

                io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYING -> {
                    switchGiftSuccess(true)
                }

                io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED -> {
                    runOnMainThread {
                        binding.btAlphaGift.text = "send gift"
                        giftPosition = -1
//                        val localTexture = mTextureVideos[0] ?: return@runOnMainThread
//                        val localContainter = (localTexture.parent as? ViewGroup) ?: return@runOnMainThread
                        val localContainter = binding.root
                        localContainter.removeView(localGiftTexture)
                    }
                }

                else -> {}
            }
            if (error != io.agora.mediaplayer.Constants.MediaPlayerReason.PLAYER_REASON_NONE) {
                switchGiftSuccess(false)
            }
        }
    }
}