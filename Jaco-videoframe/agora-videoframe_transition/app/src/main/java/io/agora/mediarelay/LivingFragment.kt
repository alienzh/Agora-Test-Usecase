package io.agora.mediarelay

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ObjectAnimator
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.*
import androidx.annotation.Size
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.Utils
import com.blankj.utilcode.util.Utils.Task
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentLivingBinding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.rtc.service.ChannelUid
import io.agora.mediarelay.rtc.service.CloudTranscoderApi
import io.agora.mediarelay.rtc.service.TranscodeSetting
import io.agora.mediarelay.widget.DashboardFragment
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.DataStreamConfig
import io.agora.rtc2.IRtcEngineEventHandler.VideoLayoutInfo
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

    private val cloudTranscoderApi by lazy { CloudTranscoderApi() }

    private val channelName by lazy { arguments?.getString(KEY_CHANNEL_ID) ?: "" }

    // 2: host, 1：broadcaster 0: audience
    private val role by lazy { arguments?.getInt(KEY_ROLE, 0) ?: 0 }

    private val rtcEngine by lazy { AgoraRtcEngineInstance.rtcEngine }

    // pk 用户 uid
    private var remoteRtcUid = 0

    private var isInTranscoder: Boolean = false

    // rtc uid
    private val curRtcUid by lazy {
        if (role == HOST) {
            channelName.toIntOrNull() ?: -1
        } else {
            KeyCenter.rtcUid
        }
    }

    // 云端推流机器人
    private val transcodeUid: Int by lazy {
        channelName.toInt() + 100000000
    }

    // 房主缩放动画
    private val scaleConstraintSet by lazy {
        ConstraintSet()
    }

    private var isAnimatorLarger: Boolean? = null

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentLivingBinding {
        return FragmentLivingBinding.inflate(inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initRtcEngine()
    }

    private fun initView() {
        scaleConstraintSet.clone(binding.rootContainer)
        when (role) {
            HOST -> {
                binding.layoutChannel.isVisible = true
                binding.btSubmitPk.isVisible = true
                binding.btSwitchCarma.isVisible = true
                binding.btMuteMic.isVisible = true
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                binding.btMuteCarma.isVisible = true
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
            }

            BROADCASTER -> {
                binding.layoutChannel.isVisible = false
                binding.btSubmitPk.isVisible = false
                binding.btSwitchCarma.isVisible = true
                binding.btMuteMic.isVisible = true
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                binding.btMuteCarma.isVisible = true
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
            }

            else -> {
                binding.layoutChannel.isVisible = false
                binding.btSubmitPk.isVisible = false
                binding.btSwitchCarma.isVisible = false
                binding.btMuteMic.isVisible = false
                binding.btMuteCarma.isVisible = false
            }
        }
        val roleName = when (role) {
            HOST -> "host"
            BROADCASTER -> "broadcaster"
            else -> "audience"
        }
        binding.tvChannelId.text = "$roleName:$channelName-$curRtcUid"
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btSubmitPk.setOnClickListener {
            if (remoteRtcUid > 0) { // pk 中停止pk
                stopPk()
                val channelUid = ChannelUid(channelName, curRtcUid)
                updateCloudTranscode(channelUid)
            } else {
                val pkRtcUid = binding.etPkRtcUid.text.toString()
                remoteRtcUid = pkRtcUid.toIntOrNull() ?: 0
                if (remoteRtcUid <= 0) {
                    ToastUtils.showShort("Please enter pk rtc id")
                    return@setOnClickListener
                }
                startPk(remoteRtcUid)
            }
        }
        binding.btSwitchCarma.setOnClickListener {
            rtcEngine.switchCamera()
        }
        binding.btMuteMic.setOnClickListener {
            muteLocalAudio = !muteLocalAudio
            rtcEngine.muteLocalAudioStream(muteLocalAudio)
            binding.btMuteMic.setImageResource(if (muteLocalAudio) R.drawable.ic_mic_off else R.drawable.ic_mic_on)
        }
        binding.btMuteCarma.setOnClickListener {
            muteLocalVideo = !muteLocalVideo
            if (muteLocalVideo) {
                rtcEngine.stopPreview()
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_off)
            } else {
                rtcEngine.startPreview()
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
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
    }

    private var muteLocalAudio = false
    private var muteLocalVideo = false

    private val eventListener = IAgoraRtcClient.IChannelEventListener(
        onChannelJoined = { channel, uid ->
            if (role == HOST) {
                // 主播加入频道成功后开始云端转码
                startSendRemotePK()
                createCloudTranscode()
            } else if (role == AUDIENCE) {
                //超级画质
                val ret1 = rtcEngine.setParameters("{\"rtc.video.enable_sr\":{\"enabled\":true, \"mode\": 2}}")
                val ret2 = rtcEngine.setParameters("{\"rtc.video.sr_type\":20}")
                Log.d(TAG, "enable_sr ret：$ret1, sr_type ret：$ret2")
            }
        },
        onUserJoined = { uid ->
        },
        onUserOffline = { offlineUid ->
        },
        onStreamMessage = { uid, streamId, data ->
            // 上麦用户专用
            if (role != BROADCASTER) return@IChannelEventListener
            try {
                val strMsg = String(data)
                val jsonMsg = JSONObject(strMsg)
                if (jsonMsg.getString("cmd") == "StartPk") { //同步远端 remotePk
                    val targetRtcUid = jsonMsg.getString("targetRtcUid").toInt()
                    // 房主和自己 PK
                    if (targetRtcUid != curRtcUid) return@IChannelEventListener
                    val fromRtcUid = jsonMsg.getString("fromRtcUid").toInt()
                    if (remoteRtcUid > 0) return@IChannelEventListener
                    remoteRtcUid = fromRtcUid
                    updatePkMode(remoteRtcUid)
                    updateVideoEncoder()
                } else if (jsonMsg.getString("cmd") == "StopPk") {
                    if (remoteRtcUid <= 0) return@IChannelEventListener
                    remoteRtcUid = 0
                    stopPk()
                    updateVideoEncoder()
                }
            } catch (e: Exception) {
                Log.e(TAG, "onStreamMessage parse error:${e.message}")
            }
        },
        onTranscodedStreamLayoutInfo = { uid, layoutInfo ->
            if (role != AUDIENCE) return@IChannelEventListener
            layoutInfo ?: return@IChannelEventListener
            setupRemoteSubviewVideo(uid, layoutInfo)
        }
    )

    private fun initRtcEngine() {
        checkRequirePerms {
            AgoraRtcEngineInstance.eventListener = eventListener
            if (role == HOST || role == BROADCASTER) {
                setupLocalView()
            } else {
                // nothing
            }
            joinChannel(curRtcUid)
        }
    }

    private val localTexture by lazy {
        TextureView(requireActivity())
    }

    private fun setupLocalView() {
        if (binding.iBroadcasterAView.childCount > 0) {
            binding.iBroadcasterAView.removeAllViews()
        }
        binding.iBroadcasterAView.addView(localTexture)
        rtcEngine.setupLocalVideo(
            VideoCanvas(localTexture, Constants.RENDER_MODE_FIT, 0)
        )
    }

    private fun setupRemoteView(remoteUid: Int) {
        if (binding.iBroadcasterBView.childCount > 0) {
            binding.iBroadcasterBView.removeAllViews()
        }
        val remoteTexture = TextureView(requireActivity())
        rtcEngine.setupRemoteVideo(
            VideoCanvas(remoteTexture, Constants.RENDER_MODE_FIT, remoteUid)
        )
        binding.iBroadcasterBView.addView(remoteTexture)
    }

    /**pk 模式,*/
    private fun updatePkMode(remotePkUid: Int) {
        val act = activity ?: return
        if (role == HOST || role == BROADCASTER) { // 主播
            binding.btSubmitPk.text = getString(R.string.stop_pk)
            setupRemoteView(remotePkUid)

            scaleConstraintSet.constrainPercentWidth(R.id.iBroadcasterAView, 0.5f)
            val transition = AutoTransition()
            transition.duration = RtcSettings.mAnimatorDuration
            TransitionManager.beginDelayedTransition(binding.rootContainer, transition)
            scaleConstraintSet.applyTo(binding.rootContainer)

            val animatorAlpha = ObjectAnimator.ofFloat(binding.iBroadcasterBView, "alpha", 0.3f, 1f)
            animatorAlpha.duration = RtcSettings.mAnimatorDuration
            animatorAlpha.start()

            binding.iPKTimeText.isVisible = true
        }
    }

    /**单主播模式*/
    private fun updateIdleMode() {
        binding.iPKTimeText.isVisible = false
        binding.btSubmitPk.text = getString(R.string.start_pk)
        remoteRtcUid = 0
        if (role == HOST || role == BROADCASTER) {
            scaleConstraintSet.constrainPercentWidth(R.id.iBroadcasterAView, 1f)
            val transition = AutoTransition()
            transition.duration = RtcSettings.mAnimatorDuration
            TransitionManager.beginDelayedTransition(binding.rootContainer, transition)
            scaleConstraintSet.applyTo(binding.rootContainer)


            val animatorAlpha = ObjectAnimator.ofFloat(binding.iBroadcasterBView, "alpha", 1f, 0.3f)
            animatorAlpha.duration = RtcSettings.mAnimatorDuration
            animatorAlpha.addListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator) {

                }

                override fun onAnimationEnd(animation: Animator) {
                    if (binding.iBroadcasterBView.childCount > 0) {
                        val firstView = binding.iBroadcasterBView.getChildAt(0)
                        if (firstView is TextureView) {
                            val canvas = VideoCanvas(firstView)
                            canvas.setupMode = VideoCanvas.VIEW_SETUP_MODE_REMOVE
                            rtcEngine.setupRemoteVideo(canvas)
                        }
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                }

                override fun onAnimationRepeat(animation: Animator) {
                }

            })
            animatorAlpha.start()
        }
    }

    private fun joinChannel(rtcUid: Int) {
        val isBroadcaster = role == HOST || role == BROADCASTER
        val channelMediaOptions = ChannelMediaOptions()
        channelMediaOptions.channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
        channelMediaOptions.clientRoleType =
            if (isBroadcaster) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
        channelMediaOptions.autoSubscribeVideo = true
        channelMediaOptions.autoSubscribeAudio = true
        channelMediaOptions.publishCameraTrack = isBroadcaster
        channelMediaOptions.publishMicrophoneTrack = isBroadcaster
        if (isBroadcaster) {
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
        rtcEngine.joinChannel(null, channelName, rtcUid, channelMediaOptions)
    }

    private fun updateVideoEncoder() {
        if (remoteRtcUid > 0 && RtcSettings.mVideoDimensions == VideoEncoderConfiguration.VD_1920x1080) {
            val videoEncoderConfiguration = VideoEncoderConfiguration().apply {
                mirrorMode = VideoEncoderConfiguration.MIRROR_MODE_TYPE.MIRROR_MODE_DISABLED
                dimensions = VideoEncoderConfiguration.VD_960x540
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
        stopSendRemotePK()
        deleteCloudTranscode()
        rtcEngine.leaveChannel()
        AgoraRtcEngineInstance.destroy()
        super.onDestroy()
    }

    // 主播渲染对方视频
    private fun startPk(remoteRtcUid: Int) {
        updatePkMode(remoteRtcUid)
        val channelUid = ChannelUid(channelName, curRtcUid)
        val remoteChannelUid = ChannelUid(channelName, remoteRtcUid)
        val channelUids = arrayOf(channelUid, remoteChannelUid)
        updateCloudTranscode(*channelUids)
        updateVideoEncoder()
    }

    // 主播退出对方频道
    private fun stopPk() {
        updateIdleMode()
        updateVideoEncoder()
    }

    private fun animateToScaleFrame(scaleLarger: Boolean) {
        if (scaleLarger) {
            scaleConstraintSet.constrainPercentWidth(R.id.iBroadcasterAView, 1f)
        } else {
            scaleConstraintSet.constrainPercentWidth(R.id.iBroadcasterAView, 0.5f)
        }

        val transition = AutoTransition()
        transition.duration = RtcSettings.mAnimatorDuration

        TransitionManager.beginDelayedTransition(binding.rootContainer, transition)
        scaleConstraintSet.applyTo(binding.rootContainer)

        isAnimatorLarger = scaleLarger
        val animatorAlpha: ObjectAnimator = if (scaleLarger) {
            ObjectAnimator.ofFloat(binding.iBroadcasterBView, "alpha", 1f, 0.3f)
        } else {
            ObjectAnimator.ofFloat(binding.iBroadcasterBView, "alpha", 0.3f, 1f)
        }
        animatorAlpha.duration = RtcSettings.mAnimatorDuration
        animatorAlpha.addListener(object : AnimatorListener {
            override fun onAnimationStart(animation: Animator) {

            }

            override fun onAnimationEnd(animation: Animator) {
                if (scaleLarger && binding.iBroadcasterBView.childCount > 0) {
                    val firstView = binding.iBroadcasterBView.getChildAt(0)
                    if (firstView is TextureView) {
                        val canvas = VideoCanvas(firstView)
                        canvas.setupMode = VideoCanvas.VIEW_SETUP_MODE_REMOVE
                        rtcEngine.setupRemoteVideo(canvas)
                    }
                }
            }

            override fun onAnimationCancel(animation: Animator) {
            }

            override fun onAnimationRepeat(animation: Animator) {
            }

        })
        animatorAlpha.start()
    }

    private val mUserViews = mutableMapOf<Int, TextureView>()

    // only for audience
    private fun setupRemoteSubviewVideo(uid: Int, layoutInfo: VideoLayoutInfo) {
        val act = activity ?: return
        val blackVideoList = layoutInfo.layoutList.filter { it.videoState == 2 }
        // 有一路子视频流黑帧
        if (blackVideoList.size > 1) return
        val layoutList = layoutInfo.layoutList

        if (layoutList.isEmpty() || layoutList.size > 2) {
            return
        }

        for ((index, videoLayout) in layoutList.withIndex()) {
            val subviewUid = videoLayout.uid
            if (mUserViews.containsKey(subviewUid)) {
                continue;
            }
            if (index == 0) {
                val remoteHostTexture = TextureView(act)
                mUserViews[videoLayout.uid] = remoteHostTexture
                if (binding.iBroadcasterAView.childCount > 0) {
                    binding.iBroadcasterAView.removeAllViews()
                }
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(remoteHostTexture, Constants.RENDER_MODE_FIT, uid, subviewUid)
                )
                binding.iBroadcasterAView.addView(remoteHostTexture)
            } else if (index == 1) {
                val remotePkTexture = TextureView(act)
                mUserViews[videoLayout.uid] = remotePkTexture
                if (binding.iBroadcasterBView.childCount > 0) {
                    binding.iBroadcasterBView.removeAllViews()
                }
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(remotePkTexture, Constants.RENDER_MODE_FIT, uid, subviewUid)
                )
                binding.iBroadcasterBView.addView(remotePkTexture)
            }
        }
//        val newUids = layoutList.map { it.uid }
//        val removeLayoutList = oldLayoutList.filter { it.uid !in newUids }
//
//        oldLayoutList.clear()
//        oldLayoutList.addAll(layoutList)
//
//        if (removeLayoutList.isNotEmpty()) { // 有用户退出
//            for ((index, videoLayout) in removeLayoutList.withIndex()) {
//                // 如果要删除某一路子视频流，则添加以下代码
//                val textureView = mUserViews[videoLayout.uid] ?: continue
//                // 此处的 textureview 设置为需要删除的 view
//                val canvas = VideoCanvas(textureView)
//                canvas.uid = uid
//                canvas.subviewUid = videoLayout.uid
//                canvas.setupMode = VideoCanvas.VIEW_SETUP_MODE_REMOVE
//                rtcEngine.setupRemoteVideo(canvas)
//                mUserViews.remove(videoLayout.uid)
//            }
//        }


        if (layoutList.size == 1 && (isAnimatorLarger == null || isAnimatorLarger == false)) {
            animateToScaleFrame(true)
        } else if (layoutList.size == 2 && (isAnimatorLarger == true)) {
            animateToScaleFrame(false)
        }
    }

    // 开始发送PK
    @Volatile
    private var mStopRemotePk = true

    private val dataStreamId: Int by lazy {
        rtcEngine.createDataStream(DataStreamConfig())
    }

    private fun sendRemotePk() {
        val msg = mutableMapOf<String, Any?>()
        if (remoteRtcUid <= 0) {
            msg["cmd"] = "StopPk"
            msg["fromRtcUid"] = curRtcUid.toString()
        } else {
            msg["cmd"] = "StartPk"
            // PK 目标用户
            msg["targetRtcUid"] = remoteRtcUid.toString()
            msg["fromRtcUid"] = curRtcUid.toString()
        }
        val jsonMsg = JSONObject(msg)
        rtcEngine.sendStreamMessage(dataStreamId, jsonMsg.toString().toByteArray())
    }

    private val remotePkTask = object : Task<Boolean>(Utils.Consumer {
        sendRemotePk()
    }) {
        override fun doInBackground(): Boolean {
            return true
        }
    }

    private fun startSendRemotePK() {
        mStopRemotePk = false

        ThreadUtils.executeBySingleAtFixRate(remotePkTask, 5, 1000, TimeUnit.MILLISECONDS)
    }

    // 停止发送PK
    private fun stopSendRemotePK() {
        mStopRemotePk = true
        remotePkTask.cancel()
    }

    // 创建云端转码
    private fun createCloudTranscode() {
        if (role != HOST) return
        if (isInTranscoder) return
        cloudTranscoderApi.createCloudTranscoder(
            TranscodeSetting.cloudTranscoding(
                curRtcUid,
                channelName,
                transcodeUid,
                ChannelUid(channelName, curRtcUid)
            ),
            completion = { taskId, error ->
                if (error == null) {
                    isInTranscoder = true
                    ToastUtils.showShort("创建云端转码成功")
                } else {
                    ToastUtils.showShort("创建云端转码失败：${error.message}")
                }
            }
        )
    }

    // 更新云端转码
    private fun updateCloudTranscode(@Size(min = 1) vararg channelUids: ChannelUid) {
        if (role != HOST) return
        if (!isInTranscoder) return

        cloudTranscoderApi.updateCloudTranscoder(
            TranscodeSetting.cloudTranscoding(
                curRtcUid,
                channelName,
                transcodeUid,
                *channelUids
            ),
            completion = { taskId, error ->
                if (error == null) {
                    ToastUtils.showShort("更新云端转码成功")
                } else {
                    ToastUtils.showShort("更新云端转码失败：${error.message}")
                }
            }
        )
    }

    // 删除云端转码
    private fun deleteCloudTranscode() {
        if (role != HOST) return
        if (!isInTranscoder) return
        cloudTranscoderApi.reqDeleteCloudTranscoder { taskId, error ->
            if (error == null) {
                isInTranscoder = false
                ToastUtils.showShort("销毁云端转码成功")
            } else {
                ToastUtils.showShort("销毁云端转码失败：${error.message}")
            }
        }
    }
}