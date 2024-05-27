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
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.tools.LogTool
import io.agora.mediarelay.tools.ThreadTool
import io.agora.mediarelay.tools.TimeUtils
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
//        binding.appIdCustom.text = "Empty"
        binding.etPushUrl.setText(KeyCenter.mPushUrl)
        binding.etPullUrl.setText(KeyCenter.mPullUrl)
        binding.etPushUrl.isEnabled = KeyCenter.cdnMakes == CdnMakes.Custom
        binding.etPullUrl.isEnabled = KeyCenter.cdnMakes == CdnMakes.Custom

        when (KeyCenter.cdnMakes) {
            CdnMakes.Huawei -> binding.groupCdn.check(R.id.cdn_huawei)
            CdnMakes.Tecent -> binding.groupCdn.check(R.id.cdn_tecent)
            CdnMakes.Ali -> binding.groupCdn.check(R.id.cdn_ali)
            CdnMakes.Custom -> binding.groupCdn.check(R.id.cdn_custom)
            else -> binding.groupCdn.check(R.id.cdn_agora)
        }

        binding.groupCdn.setOnCheckedChangeListener { radioGroup, checkedId ->
            when (checkedId) {
                R.id.cdn_huawei -> KeyCenter.cdnMakes = CdnMakes.Huawei
                R.id.cdn_tecent -> KeyCenter.cdnMakes = CdnMakes.Tecent
                R.id.cdn_ali -> KeyCenter.cdnMakes = CdnMakes.Ali
                R.id.cdn_custom -> KeyCenter.cdnMakes = CdnMakes.Custom
                else -> KeyCenter.cdnMakes = CdnMakes.Agora
            }
            binding.etPushUrl.setText(KeyCenter.mPushUrl)
            binding.etPullUrl.setText(KeyCenter.mPullUrl)
            binding.etPushUrl.isEnabled = KeyCenter.cdnMakes == CdnMakes.Custom
            binding.etPullUrl.isEnabled = KeyCenter.cdnMakes == CdnMakes.Custom
        }
        // enable user account
        binding.cbUserAccount.setChecked(RtcSettings.mEnableUserAccount)
        binding.cbUserAccount.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                RtcSettings.mEnableUserAccount = isChecked
            }
        }

        // enable quic, 至针对直播流，主播隐藏，观众打开
        binding.layoutQuic.isVisible = false // 暂时隐藏
        binding.layoutQuic.isVisible = !isBroadcaster
        binding.cbQuic.setChecked(RtcSettings.mEnableQuic)
        binding.cbQuic.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                RtcSettings.mEnableQuic = isChecked
            }
        }
        binding.etSwitchSrcTimeout.setText(RtcSettings.mSwitchSrcTimeout.toString())

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
        binding.etFps.setText("${RtcSettings.mFrameRate}")
        // bitrate
        binding.etBitrate.setText("${RtcSettings.mBitRate}")

        LogTool.d(TAG, "bingTime:${TimeUtils.currentTimeMillis()}")
    }

    private val isBroadcaster: Boolean
        get() = binding.groupRole.checkedRadioButtonId == R.id.role_broadcaster

    private fun checkVideoSettingsVisible() {
        val single = binding.groupAnchor.checkedRadioButtonId == R.id.scene_single
        if (single && isBroadcaster) {
            binding.layoutResolution.isVisible = true
            binding.layoutFps.isVisible = true
            binding.layoutBitrate.isVisible = true
        } else {
            binding.layoutResolution.isVisible = false
            binding.layoutFps.isVisible = false
            binding.layoutBitrate.isVisible = false
        }
        binding.layoutQuic.isVisible = !isBroadcaster
        binding.layoutSwitchStcTimeout.isVisible = !isBroadcaster
    }

    private fun setupVideoSettings() {
        if (binding.groupAnchor.checkedRadioButtonId == R.id.scene_single) {
            when (binding.groupResolution.checkedRadioButtonId) {
                R.id.resolution_1080p -> {
                    RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_1920x1080
                    RtcSettings.mVideoDimensionsAuto = false
                }

                R.id.resolution_720p -> {
                    RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_1280x720
                    RtcSettings.mVideoDimensionsAuto = false
                }

                R.id.resolution_540p -> {
                    RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_960x540
                    RtcSettings.mVideoDimensionsAuto = false
                }

                else -> {
                    RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_1920x1080
                    RtcSettings.mVideoDimensionsAuto = true
                }
            }
            RtcSettings.mFrameRate = binding.etFps.text.toString().toIntOrNull() ?: 24
            RtcSettings.mBitRate = binding.etBitrate.text.toString().toIntOrNull() ?: 0
        } else if (binding.groupAnchor.checkedRadioButtonId == R.id.scene_3) {
            // multiple anchor setup default config
            RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_960x540
            RtcSettings.mVideoDimensionsAuto = false
            RtcSettings.mFrameRate = 24
        } else if (binding.groupAnchor.checkedRadioButtonId == R.id.scene_4) {
            // multiple anchor setup default config
            RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VideoDimensions(540, 540)
            RtcSettings.mVideoDimensionsAuto = false
            RtcSettings.mFrameRate = 24
        } else if (binding.groupAnchor.checkedRadioButtonId == R.id.scene_multi) {
            // multiple anchor setup default config
            RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VideoDimensions(270, 270)
            RtcSettings.mVideoDimensionsAuto = false
            RtcSettings.mFrameRate = 15
        }
    }

    private fun checkGoLivePage() {
        if (KeyCenter.cdnMakes == CdnMakes.Custom) {
            val inputPushUrl = binding.etPushUrl.text?.trim().toString()
            KeyCenter.setCustomPushUrl(inputPushUrl)
            val inputPullUrl = binding.etPullUrl.text?.trim().toString()
            KeyCenter.setCustomPullUrl(inputPullUrl)
        }

        val channelId = binding.etChannel.text.toString()
        if (channelId.isEmpty()) {
            ToastTool.showToast("Please enter channel id")
            return
        }
        if (binding.groupAppId.checkedRadioButtonId == R.id.app_id_agora) {
            AgoraRtcEngineInstance.setAppKeys(
                BuildConfig.AGORA_APP_ID,
                BuildConfig.AGORA_CUSTOMER_KEY,
                BuildConfig.AGORA_CUSTOMER_SECRET
            )
        } /*else {
            AgoraRtcEngineInstance.setAppKeys(
                BuildConfig.CUSTOM_APP_ID,
                BuildConfig.CUSTOM_CUSTOMER_KEY,
                BuildConfig.CUSTOM_CUSTOMER_SECRET
            )
        }*/
        setupVideoSettings()
        val args = Bundle().apply {
            putString(LivingFragment.KEY_CHANNEL_ID, channelId)
            putInt(
                LivingFragment.KEY_ROLE,
                if (isBroadcaster) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
            )
        }

        val switchSrcTimeout = binding.etSwitchSrcTimeout.text.toString().toIntOrNull() ?: 2
        RtcSettings.mSwitchSrcTimeout = switchSrcTimeout
        // scene
        when (binding.groupAnchor.checkedRadioButtonId) {
            R.id.scene_single -> {
                findNavController().navigate(R.id.action_mainFragment_to_livingFragment, args)
            }

            R.id.scene_3 -> {
                findNavController().navigate(R.id.action_mainFragment_to_living3Fragment, args)
            }

            R.id.scene_4 -> {
                findNavController().navigate(R.id.action_mainFragment_to_living4Fragment, args)
            }

            R.id.scene_multi -> {
                findNavController().navigate(R.id.action_mainFragment_to_livingMultiFragment, args)
            }
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