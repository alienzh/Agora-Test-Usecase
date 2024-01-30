package io.agora.dualstream.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import io.agora.dualstream.rtc.IChannelEventListener
import io.agora.dualstream.utils.KeyCenter
import io.agora.dualstream.R
import io.agora.dualstream.rtc.RtcEngineInstance
import io.agora.dualstream.model.UserModel
import io.agora.dualstream.databinding.FragmentLiveBinding
import io.agora.dualstream.databinding.PopLayoutBinding
import io.agora.dualstream.utils.PermissionHelp
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.RtcConnection

class LiveFragment : Fragment() {

    private var _binding: FragmentLiveBinding? = null

    private val binding get() = _binding!!

    companion object {
        const val KEY_CHANNEL = "key_channel"
        const val KEY_ROLE = "key_role"

        const val AUDIO_PATH = "/assets/audio.m4a"
    }

    private val channelId: String by lazy {
        arguments?.getString(KEY_CHANNEL, "") ?: ""
    }
    private val role: Int by lazy {
        arguments?.getInt(KEY_ROLE, Constants.CLIENT_ROLE_AUDIENCE)
            ?: Constants.CLIENT_ROLE_AUDIENCE
    }

    private val lowStreamConnection: RtcConnection by lazy {
        RtcConnection(channelId + "_low", KeyCenter.rtcUid)
    }

    private val mainChannelMediaOptions: ChannelMediaOptions by lazy {
        ChannelMediaOptions().apply {
            autoSubscribeAudio = true
            publishMicrophoneTrack = role == Constants.CLIENT_ROLE_BROADCASTER
            clientRoleType = role
        }
    }

    private val lowStreamChannelMediaOptions: ChannelMediaOptions by lazy {
        ChannelMediaOptions().apply {
            autoSubscribeAudio = true
            publishMicrophoneTrack = role == Constants.CLIENT_ROLE_BROADCASTER
            clientRoleType = role
        }
    }

    private lateinit var userAdapter: RecyclerView.Adapter<InnerItemViewHolder>

    private val userList = mutableListOf<UserModel>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity?.onBackPressedDispatcher?.addCallback {
            handleOnBackPressed()
            leaveChannel()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        joinChannel()
    }

    private fun initView() {
        if (role == Constants.CLIENT_ROLE_BROADCASTER) {
            binding.layoutPublishAudio.isVisible = true
            binding.layoutPublishDualStream.isVisible = true
            binding.layoutPlayingMusic.isVisible = true
            binding.layoutLowStream.isVisible = false
        } else {
            binding.layoutPublishAudio.isVisible = false
            binding.layoutPublishDualStream.isVisible = false
            binding.layoutPlayingMusic.isVisible = false
            binding.layoutLowStream.isVisible = true
        }
        binding.tvChannelId.text = channelId
        binding.titleBar.setOnBackClickListener {
            leaveChannel()
            findNavController().popBackStack()
        }
        binding.btnLeaveChannel.setOnClickListener {
            leaveChannel()
            findNavController().popBackStack()
        }

        binding.checkPublishAudio.setOnCheckedChangeListener { buttonView, isChecked ->
            RtcEngineInstance.muteLocalAudioStream(!isChecked)
        }
        binding.checkPublishDualStream.setOnCheckedChangeListener { buttonView, isChecked ->
            RtcEngineInstance.muteLocalAudioStreamEx(!isChecked, lowStreamConnection)
        }
        binding.checkPlayingMusic.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                RtcEngineInstance.startAudioMixing(AUDIO_PATH)
            } else {
                RtcEngineInstance.stopAudioMixing()
            }
        }
        binding.checkLowStream.setOnCheckedChangeListener { buttonView, isChecked ->
            RtcEngineInstance.muteAllRemoteAudioStreamsEx(!isChecked, lowStreamConnection)
            RtcEngineInstance.muteAllRemoteAudioStreams(isChecked)
            userList.forEach {
                it.lowStream = isChecked
            }
            userAdapter.notifyDataSetChanged()
        }

        userAdapter = object : RecyclerView.Adapter<InnerItemViewHolder>() {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
            ): InnerItemViewHolder {
                return InnerItemViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_live_view, parent, false)
                )
            }

            //最多6个
            override fun getItemCount(): Int {
                return if (userList.size >= 6) 6 else userList.size
            }

            override fun onBindViewHolder(holder: InnerItemViewHolder, position: Int) {
                val userModel = userList[position]
                val avatarDrawableId = getDrawableId("avatar" + (position + 1))
                holder.ivAvatar.setImageResource(avatarDrawableId)
                holder.tvUserName.text = "${userModel.userId}"
                holder.tvStream.isVisible = role != Constants.CLIENT_ROLE_BROADCASTER
                holder.tvStream.text =
                    if (userModel.lowStream) resources.getText(R.string.low_stream_pre) else resources.getText(
                        R.string.high_stream_pre
                    )
                holder.itemView.setOnClickListener {
                    showPop(it, userModel.userId)
                }
            }
        }
        binding.recyclerAvatar.adapter = userAdapter
    }

    private fun showPop(itemView: View, uid: Int) {
        if (role != Constants.CLIENT_ROLE_AUDIENCE) return
        //Gets the coordinates attached to the view
        val location = IntArray(2)
        itemView.getLocationInWindow(location)
        val cxt = context ?: return
        CommonPopupWindow.ViewDataBindingBuilder<PopLayoutBinding>()
            .viewDataBinding(PopLayoutBinding.inflate(LayoutInflater.from(cxt)))
            .width(dp2px(requireContext(), 80f))
            .height(dp2px(requireContext(), 100f))
            .outsideTouchable(true)
            .focusable(true)
            .clippingEnabled(false)
            .alpha(0.618f)
            .intercept { popupWindow, view ->
                val adapter =
                    ArrayAdapter(cxt, android.R.layout.simple_list_item_1, arrayOf("High", "Low"))
                view.listView.adapter = adapter
                view.listView.setOnItemClickListener { parent, view, position, id ->
                    if (position == 0) { // 切换单主播大流
                        RtcEngineInstance.muteRemoteAudioStream(uid, false)
                        RtcEngineInstance.muteRemoteAudioStreamEx(uid, true, lowStreamConnection)
                        userList.forEach {
                            if (it.userId == uid) {
                                it.lowStream = false
                            }
                            return@forEach
                        }
                        userAdapter.notifyDataSetChanged()
                    } else { // 切换单主播小流
                        RtcEngineInstance.muteRemoteAudioStream(uid, true)
                        RtcEngineInstance.muteRemoteAudioStreamEx(uid, false, lowStreamConnection)
                        userList.forEach {
                            if (it.userId == uid) {
                                it.lowStream = true
                            }
                            return@forEach
                        }
                        userAdapter.notifyDataSetChanged()
                    }
                    popupWindow.dismiss()
                }
            }
            .onDismissListener {

            }
            .build<View>(cxt)
            .showAtLocation(
                itemView, Gravity.NO_GRAVITY, location[0], location[1] - itemView.height / 2
            )
    }

    private fun joinChannel() {
        RtcEngineInstance.joinChannel(channelId,
            KeyCenter.rtcUid,
            mainChannelMediaOptions,
            IChannelEventListener(
                onChannelJoined = {
                    if (role == Constants.CLIENT_ROLE_BROADCASTER) {
                        userList.add(0, UserModel(it))
                        userAdapter.notifyDataSetChanged()
                        RtcEngineInstance.startAudioMixing(AUDIO_PATH)
                    }
                },
                onUserJoined = {
                    userList.add(UserModel(it))
                    userAdapter.notifyDataSetChanged()
                },
                onUserOffline = {
                    userList.remove(UserModel(it))
                    userAdapter.notifyDataSetChanged()
                }
            ))

        RtcEngineInstance.joinChannelEx(lowStreamConnection, lowStreamChannelMediaOptions,
            IChannelEventListener(
                onChannelJoined = {
                    if (role == Constants.CLIENT_ROLE_AUDIENCE) {
                        RtcEngineInstance.muteAllRemoteAudioStreamsEx(true, lowStreamConnection)
                    }
                },
                onUserJoined = {

                },
                onUserOffline = {

                }
            ))
    }

    private fun leaveChannel() {
        if (role == Constants.CLIENT_ROLE_BROADCASTER) {
            RtcEngineInstance.stopAudioMixing()
        }
        RtcEngineInstance.leaveChannel()
        RtcEngineInstance.leaveChannelEx(lowStreamConnection)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("DiscouragedApi")
    private fun getDrawableId(name: String): Int {
        var drawableId = 0
        try {
            drawableId = resources.getIdentifier(name, "drawable", requireContext().packageName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (drawableId == 0) {
            drawableId = R.drawable.avatar1
        }
        return drawableId
    }

    private class InnerItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView
        val tvUserName: TextView
        val tvStream: TextView

        init {
            ivAvatar = itemView.findViewById(R.id.ivUserAvatar)
            tvUserName = itemView.findViewById(R.id.tvUserName)
            tvStream = itemView.findViewById(R.id.tvStream)
        }
    }

    fun dp2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }


}