package io.agora.mediarelay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
            val channelId = binding.etChannel.text.toString()
            if (checkChannelId(channelId)) return@setOnClickListener
            val isBroadcaster = binding.groupRole.checkedRadioButtonId == R.id.role_broadcaster
            val args = Bundle().apply {
                putString(LivingFragment.KEY_CHANNEL_ID, channelId)
                putInt(
                    LivingFragment.KEY_ROLE,
                    if (isBroadcaster) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
                )
            }
            findNavController().navigate(R.id.action_mainFragment_to_livingFragment, args)
        }
        // role
        binding.groupRole.setOnCheckedChangeListener { radioGroup, checkedId ->
            when (checkedId) {
                R.id.role_broadcaster -> binding.groupVideoSettings.isVisible = true
                else -> binding.groupVideoSettings.isVisible = false
            }
        }

        // video dimensions
        when (RtcSettings.mVideoDimensions) {
            VideoEncoderConfiguration.VD_1920x1080 -> binding.groupResolution.check(R.id.resolution_1080p)
            VideoEncoderConfiguration.VD_1280x720 -> binding.groupResolution.check(R.id.resolution_720p)
            else -> binding.groupResolution.check(R.id.resolution_auto)
        }
        binding.groupResolution.setOnCheckedChangeListener { radioGroup, checkedId ->
            when (checkedId) {
                R.id.resolution_1080p -> {
                    RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_1920x1080
                    RtcSettings.mVideoDimensionsAuto = false
                }

                R.id.resolution_720p -> {
                    RtcSettings.mVideoDimensions = VideoEncoderConfiguration.VD_1280x720
                    RtcSettings.mVideoDimensionsAuto = false
                }

                else -> RtcSettings.mVideoDimensionsAuto = true
            }
        }

        // frame rate
        when (RtcSettings.mFrameRate) {
            VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30 ->
                binding.groupFrameRate.check(R.id.frame_rate_30fps)
            VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15 ->
                binding.groupFrameRate.check(R.id.frame_rate_15fps)
            else ->  binding.groupFrameRate.check(R.id.frame_rate_24fps)
        }
        binding.groupFrameRate.setOnCheckedChangeListener { radioGroup, checkedId ->
            when (checkedId) {
                R.id.frame_rate_30fps -> RtcSettings.mFrameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30
                R.id.frame_rate_15fps -> RtcSettings.mFrameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15
                else -> RtcSettings.mFrameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_24
            }
        }

        // push/pull url
        binding.etPushUrl.doAfterTextChanged { editable ->
            editable?.trim()?.let {
                if (it.startsWith("rtmp")) {
                    KeyCenter.pushUrl = it.toString()
                }
            }
        }
        binding.etPullUrl.doAfterTextChanged { editable ->
            editable?.trim()?.let {
                if (it.startsWith("http")) {
                    KeyCenter.pullUrl = it.toString()
                }
            }
        }
        binding.etPushUrl.setText(KeyCenter.pushUrl)
        binding.etPullUrl.setText(KeyCenter.pullUrl)

        // bitrate
        binding.etBitrate.setText("${RtcSettings.mBitRate}")
    }

    private fun checkChannelId(channelId: String): Boolean {
        if (channelId.isEmpty()) {
            ToastTool.showToast("Please enter channel id")
            return true
        }
        return false
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