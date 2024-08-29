package io.agora.mediarelay

import android.os.Bundle
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.blankj.utilcode.util.ToastUtils
import im.zego.zegoexpress.constants.ZegoVideoConfigPreset
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentMainBinding
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.zego.ZegoLivingFragment
import io.agora.mediarelay.zego.ZegoSettings
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
//        binding.etPushUrl.setText(KeyCenter.mPushUrl)
//        binding.etPullUrl.setText(KeyCenter.mPullUrl)
//        binding.etPushUrl.isEnabled = KeyCenter.cdnMakes == CdnMakes.Custom
//        binding.etPullUrl.isEnabled = KeyCenter.cdnMakes == CdnMakes.Custom

//        when (KeyCenter.cdnMakes) {
//            CdnMakes.Huawei -> binding.groupCdn.check(R.id.cdn_huawei)
//            CdnMakes.Tecent -> binding.groupCdn.check(R.id.cdn_tecent)
//            CdnMakes.Ali -> binding.groupCdn.check(R.id.cdn_ali)
//            CdnMakes.Custom -> binding.groupCdn.check(R.id.cdn_custom)
//            else -> binding.groupCdn.check(R.id.cdn_agora)
//        }

//        binding.groupCdn.setOnCheckedChangeListener { radioGroup, checkedId ->
//            when (checkedId) {
//                R.id.cdn_huawei -> KeyCenter.cdnMakes = CdnMakes.Huawei
//                R.id.cdn_tecent -> KeyCenter.cdnMakes = CdnMakes.Tecent
//                R.id.cdn_ali -> KeyCenter.cdnMakes = CdnMakes.Ali
//                R.id.cdn_custom -> KeyCenter.cdnMakes = CdnMakes.Custom
//                else -> KeyCenter.cdnMakes = CdnMakes.Agora
//            }
//            binding.etPushUrl.setText(KeyCenter.mPushUrl)
//            binding.etPullUrl.setText(KeyCenter.mPullUrl)
//            binding.etPushUrl.isEnabled = KeyCenter.cdnMakes == CdnMakes.Custom
//            binding.etPullUrl.isEnabled = KeyCenter.cdnMakes == CdnMakes.Custom
//        }
        binding.etTestIp.setInputType(InputType.TYPE_CLASS_NUMBER)
        val digits = "0123456789.:"
        binding.etTestIp.keyListener = DigitsKeyListener.getInstance(digits)

        binding.etTestIp.setText(KeyCenter.testIp)
        // enable user account
        binding.cbUserAccount.setChecked(RtcSettings.mEnableUserAccount)
        binding.cbUserAccount.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                RtcSettings.mEnableUserAccount = isChecked
            }
        }

        checkVideoSettingsVisible()
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
        } else if (RtcSettings.mVideoDimensions == VideoEncoderConfiguration.VD_960x540) {
            binding.groupResolution.check(R.id.resolution_540p)
        }
        binding.groupResolution.setOnCheckedChangeListener { _, checkedId ->
        }

        // frame rate
        binding.etFps.setText("${RtcSettings.mFrameRate}")
        // bitrate
        binding.etBitrate.setText("${RtcSettings.mBitRate}")
    }

    private val isBroadcaster: Boolean
        get() = binding.groupRole.checkedRadioButtonId == R.id.role_broadcaster

    private fun checkVideoSettingsVisible() {
        if (isBroadcaster) {
            binding.layoutResolution.isVisible = true
            binding.layoutFps.isVisible = true
            binding.layoutBitrate.isVisible = true
        } else {
            binding.layoutResolution.isVisible = false
            binding.layoutFps.isVisible = false
            binding.layoutBitrate.isVisible = false
        }
    }

    private fun setupVideoSettings() {
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
    }

    private fun setupZegoSettings() {
        when (binding.groupResolution.checkedRadioButtonId) {
            R.id.resolution_1080p -> {
                ZegoSettings.mVideoConfigpreset = ZegoVideoConfigPreset.PRESET_1080P
            }

            R.id.resolution_720p -> {
                ZegoSettings.mVideoConfigpreset = ZegoVideoConfigPreset.PRESET_720P
            }

            R.id.resolution_540p -> {
                ZegoSettings.mVideoConfigpreset = ZegoVideoConfigPreset.PRESET_540P
            }

            else -> {
                ZegoSettings.mVideoConfigpreset = ZegoVideoConfigPreset.PRESET_1080P
            }
        }
        ZegoSettings.mFrameRate = binding.etFps.text.toString().toIntOrNull() ?: 24
        ZegoSettings.mBitRate = binding.etBitrate.text.toString().toIntOrNull() ?: 0
    }

    private fun checkGoLivePage() {
//        if (KeyCenter.cdnMakes == CdnMakes.Custom) {
//            val inputPushUrl = binding.etPushUrl.text?.trim().toString()
//            KeyCenter.setCustomPushUrl(inputPushUrl)
//            val inputPullUrl = binding.etPullUrl.text?.trim().toString()
//            KeyCenter.setCustomPullUrl(inputPullUrl)
//        }

        val testIp = binding.etTestIp.text?.trim().toString()
        if (testIp.isEmpty()) {
            ToastUtils.showShort("Please input testIp")
            return
        }
        KeyCenter.testIp = testIp

        val channelId = binding.etChannel.text.toString()
        if (channelId.isEmpty()) {
            ToastUtils.showShort("Please input channel id")
            return
        }
        val args = Bundle().apply {
            putString(LivingFragment.KEY_CHANNEL_ID, channelId)
        }
        if (binding.groupAppId.checkedRadioButtonId == R.id.app_id_agora) {
            KeyCenter.vendor = Vendor.Agora
            args.putInt(
                LivingFragment.KEY_ROLE,
                if (isBroadcaster) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
            )
            setupVideoSettings()
        } else {
            KeyCenter.vendor = Vendor.Zego
            args.putBoolean(ZegoLivingFragment.KEY_IS_BROADCASTER, isBroadcaster)
            setupZegoSettings()
        }
        // vendor
        if (KeyCenter.vendor == Vendor.Agora) {
            findNavController().navigate(R.id.action_mainFragment_to_livingFragment, args)
        } else {
            findNavController().navigate(R.id.action_mainFragment_to_livingZegoFragment, args)
        }
    }

    override fun onResume() {
        activity?.let {
            val insetsController = WindowCompat.getInsetsController(it.window, it.window.decorView)
            insetsController.isAppearanceLightStatusBars = false
        }

        super.onResume()
    }
}