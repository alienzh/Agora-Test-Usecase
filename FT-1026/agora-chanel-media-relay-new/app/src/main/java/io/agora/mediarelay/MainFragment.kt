package io.agora.mediarelay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentMainBinding
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.tools.ToastTool
import io.agora.rtc2.Constants
import io.agora.rtc2.video.VideoEncoderConfiguration

/**
 * @author create by zhangwei03
 */
class MainFragment : BaseUiFragment<FragmentMainBinding>() {
    companion object {
        private const val TAG = "MainFragment"
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentMainBinding {
        return FragmentMainBinding.inflate(inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.enterRoom.setOnClickListener {
            checkGoLivePage()
        }
        binding.etPushUrl.setText(KeyCenter.pushUrl)
        binding.etPullUrl.setText(KeyCenter.pullUrl)
        if (KeyCenter.isAgoraCdn()) {
            binding.groupCdn.check(R.id.cdn_agora)
        } else {
            binding.groupCdn.check(R.id.cdn_custom)
        }
        binding.groupCdn.setOnCheckedChangeListener { radioGroup, checkedId ->
            when (checkedId) {
                R.id.cdn_agora -> {
                    KeyCenter.setupAgoraCdn(true)
                    binding.etPushUrl.setText(KeyCenter.pushUrl)
                    binding.etPullUrl.setText(KeyCenter.pullUrl)
                }

                R.id.cdn_custom -> {
                    KeyCenter.setupAgoraCdn(false)
                    binding.etPushUrl.setText(KeyCenter.pushUrl)
                    binding.etPullUrl.setText(KeyCenter.pullUrl)
                }
            }
        }
        checkVideoSettingsVisible()
        // mode
        binding.groupAnchor.setOnCheckedChangeListener { _, _ ->
            checkVideoSettingsVisible()
        }
        // role
        binding.groupRole.setOnCheckedChangeListener { _, _ ->
            checkVideoSettingsVisible()
        }

        // video dimensions
        if (RtcSettings.mVideoDimensionsAuto) {
            RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_1920x1080
            binding.groupResolution.check(R.id.resolution_auto)
        } else if (RtcSettings.mVideoDimensions == VideoEncoderConfiguration.VD_1920x1080) {
            binding.groupResolution.check(R.id.resolution_1080p)
        } else if (RtcSettings.mVideoDimensions == VideoEncoderConfiguration.VD_1280x720) {
            binding.groupResolution.check(R.id.resolution_720p)
        }
        binding.groupResolution.setOnCheckedChangeListener { _, _ ->
        }

        // frame rate
        when (RtcSettings.mFrameRate) {
            VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30 ->
                binding.groupFrameRate.check(R.id.frame_rate_30fps)

            VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15 ->
                binding.groupFrameRate.check(R.id.frame_rate_15fps)

            else -> binding.groupFrameRate.check(R.id.frame_rate_24fps)
        }
        binding.groupFrameRate.setOnCheckedChangeListener { radioGroup, checkedId ->
        }
        // bitrate
        binding.etBitrate.setText("${RtcSettings.mBitRate}")
    }

    private fun checkVideoSettingsVisible() {
        val single = binding.groupAnchor.checkedRadioButtonId == R.id.scene_single
        val isBroadcaster = binding.groupRole.checkedRadioButtonId == R.id.role_broadcaster
        binding.groupVideoSettings.isVisible = single && isBroadcaster
    }

    private fun setupVideoSettings() {
        val isSingle = binding.groupAnchor.checkedRadioButtonId == R.id.scene_single
        if (isSingle){
            when (binding.groupResolution.checkedRadioButtonId) {
                R.id.resolution_1080p -> {
                    RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_1920x1080
                    RtcSettings.mVideoDimensionsAuto = false
                }

                R.id.resolution_720p -> {
                    RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_1280x720
                    RtcSettings.mVideoDimensionsAuto = false
                }

                else -> {
                    RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_1920x1080
                    RtcSettings.mVideoDimensionsAuto = true
                }
            }
            when (binding.groupFrameRate.checkedRadioButtonId) {
                R.id.frame_rate_30fps -> RtcSettings.mFrameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30
                R.id.frame_rate_15fps -> RtcSettings.mFrameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15
                else -> RtcSettings.mFrameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_24
            }
        }else{
            // multiple anchor setup default config
            RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VideoDimensions(270, 270)
            RtcSettings.mVideoDimensionsAuto = false
            RtcSettings.mFrameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15
        }
    }

    private fun checkGoLivePage() {
        val inputPushUrl = binding.etPushUrl.text?.trim().toString()
        if (inputPushUrl.startsWith("rtmp")) {
            KeyCenter.pushUrl = inputPushUrl
        }
        val inputPullUrl = binding.etPullUrl.text?.trim().toString()
        if (inputPullUrl.startsWith("http")) {
            KeyCenter.pullUrl = inputPullUrl
        }
        val channelId = binding.etChannel.text.toString()
        if (channelId.isEmpty()) {
            ToastTool.showToast("Please enter channel id")
            return
        }
        val isBroadcaster = binding.groupRole.checkedRadioButtonId == R.id.role_broadcaster
        val args = Bundle().apply {
            putString(LivingFragment.KEY_CHANNEL_ID, channelId)
            putInt(
                LivingFragment.KEY_ROLE,
                if (isBroadcaster) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
            )
        }
        setupVideoSettings()
        // scene
        val isSingle = binding.groupAnchor.checkedRadioButtonId == R.id.scene_single
        if (isSingle){
            findNavController().navigate(R.id.action_mainFragment_to_livingFragment, args)
        }else{
            findNavController().navigate(R.id.action_mainFragment_to_livingMultiFragment, args)
        }
    }

    override fun onResume() {
        activity?.let {
            val insetsController = WindowCompat.getInsetsController(it.window, it.window.decorView)
            insetsController.isAppearanceLightStatusBars = false
        }

        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}