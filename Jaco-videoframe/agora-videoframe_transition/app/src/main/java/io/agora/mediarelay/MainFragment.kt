package io.agora.mediarelay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.blankj.utilcode.util.ToastUtils
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentMainBinding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.tools.LogTool
import io.agora.mediarelay.tools.TimeUtils
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

        checkVideoSettingsVisible()
        // role
        binding.groupRole.setOnCheckedChangeListener { _, _ ->
            checkVideoSettingsVisible()
        }

        binding.etAimatorDuation.setText(RtcSettings.mAnimatorDuration.toString())

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
        get() = binding.groupRole.checkedRadioButtonId == R.id.role_host || binding.groupRole.checkedRadioButtonId == R.id.role_broadcaster

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

    private fun checkGoLivePage() {

        val channelId = binding.etChannel.text.toString()
        if (channelId.isEmpty()) {
            ToastUtils.showShort("Please enter channel id")
            return
        }
        if (binding.groupAppId.checkedRadioButtonId == R.id.app_id_agora) {
            AgoraRtcEngineInstance.setAppKeys(
                BuildConfig.AGORA_APP_ID,
                BuildConfig.AGORA_ACCESS_KEY,
                BuildConfig.AGORA_SECRET_KEY
            )
        }
        setupVideoSettings()
        val animatorDuration = binding.etAimatorDuation.text.toString().toLongOrNull() ?: 3000L
        RtcSettings.mAnimatorDuration = animatorDuration

        val role = when (binding.groupRole.checkedRadioButtonId) {
            R.id.role_host -> 2
            R.id.role_broadcaster -> 1
            else -> 0
        }
        val args = Bundle().apply {
            putString(LivingFragment.KEY_CHANNEL_ID, channelId)
            putInt(LivingFragment.KEY_ROLE, role)
        }
        findNavController().navigate(R.id.action_mainFragment_to_livingFragment, args)
    }

    override fun onResume() {
        activity?.let {
            val insetsController = WindowCompat.getInsetsController(it.window, it.window.decorView)
            insetsController.isAppearanceLightStatusBars = false
        }
        super.onResume()
    }
}