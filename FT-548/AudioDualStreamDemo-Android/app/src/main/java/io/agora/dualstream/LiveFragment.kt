package io.agora.dualstream

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import io.agora.dualstream.databinding.FragmentLiveBinding
import io.agora.dualstream.databinding.PopLayoutBinding
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.RtcConnection

class LiveFragment : Fragment() {

    private var _binding: FragmentLiveBinding? = null

    private val binding get() = _binding!!

    companion object {
        const val KEY_CHANNEL = "key_channel"
        const val KEY_ROLE = "key_role"
    }

    private val channelId: String by lazy {
        arguments?.getString(KEY_CHANNEL, "") ?: ""
    }
    private val role: Int by lazy {
        arguments?.getInt(KEY_ROLE, Constants.CLIENT_ROLE_AUDIENCE)
            ?: Constants.CLIENT_ROLE_AUDIENCE
    }

    private val mainConnection: RtcConnection by lazy {
        RtcConnection(channelId, role)
    }

    private val lowStreamConnection: RtcConnection by lazy {
        RtcConnection(channelId + "_low", role)
    }
    private lateinit var userAdapter: RecyclerView.Adapter<InnerItemViewHolder>

    private val userList = mutableListOf<UserModel>()

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
            binding.layoutLowStream.isVisible = false
        } else {
            binding.layoutPublishAudio.isVisible = false
            binding.layoutPublishDualStream.isVisible = false
            binding.layoutLowStream.isVisible = true
        }
        binding.titleBar.setOnBackClickListener {
            RtcEngineInstance.leaveChannelEx(mainConnection)
            RtcEngineInstance.leaveChannelEx(lowStreamConnection)
            findNavController().popBackStack()
        }
        binding.btnLeaveChannel.setOnClickListener {
            RtcEngineInstance.leaveChannelEx(mainConnection)
            RtcEngineInstance.leaveChannelEx(lowStreamConnection)
            findNavController().popBackStack()
        }

        binding.checkPublishAudio.setOnCheckedChangeListener { buttonView, isChecked ->
            val mainChannelMediaOptions = ChannelMediaOptions().apply {
                publishMicrophoneTrack = isChecked
            }
            RtcEngineInstance.updateChannelMediaOptionsEx(
                mainChannelMediaOptions,
                mainConnection
            )
        }
        binding.checkPublishDualStream.setOnCheckedChangeListener { buttonView, isChecked ->
            val lowStreamChannelMediaOptions = ChannelMediaOptions().apply {
                publishMicrophoneTrack = isChecked
            }
            RtcEngineInstance.updateChannelMediaOptionsEx(
                lowStreamChannelMediaOptions,
                lowStreamConnection
            )
        }
        binding.checkLowStream.setOnCheckedChangeListener { buttonView, isChecked ->
            val lowStreamChannelMediaOptions = ChannelMediaOptions().apply {
                autoSubscribeAudio = isChecked
            }
            RtcEngineInstance.updateChannelMediaOptionsEx(
                lowStreamChannelMediaOptions,
                lowStreamConnection
            )
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
                    if (userModel.lowStream) resources.getText(R.string.low_stream) else resources.getText(
                        R.string.high_stream
                    )
                holder.itemView.setOnClickListener {
                    showPop(it, userModel.userId)
                }
            }
        }
        binding.recyclerAvatar.adapter = userAdapter
    }

    private fun showPop(itemView: View, uid: Int) {
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
                    if (position == 0) {
                        RtcEngineInstance.muteRemoteAudioStreamEx(uid, false, mainConnection)
                        RtcEngineInstance.muteRemoteAudioStreamEx(uid, true, lowStreamConnection)
                    } else {
                        RtcEngineInstance.muteRemoteAudioStreamEx(uid, true, mainConnection)
                        RtcEngineInstance.muteRemoteAudioStreamEx(uid, false, lowStreamConnection)
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
        val mainChannelMediaOptions = ChannelMediaOptions().apply {
            autoSubscribeAudio = true
            publishMicrophoneTrack = role == Constants.CLIENT_ROLE_BROADCASTER
        }
        RtcEngineInstance.joinChannelEx(mainConnection, mainChannelMediaOptions,
            IChannelEventListener(
                onChannelJoined = {
                    if (role == Constants.CLIENT_ROLE_BROADCASTER) {
                        userList.add(0, UserModel(it))
                        userList.add(UserModel(it))
                        userList.add(UserModel(it))
                        userList.add(UserModel(it))
                        userList.add(UserModel(it))
                        userAdapter.notifyDataSetChanged()
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
        val lowStreamChannelMediaOptions = ChannelMediaOptions().apply {
            autoSubscribeAudio = role == Constants.CLIENT_ROLE_BROADCASTER
            publishMicrophoneTrack = role == Constants.CLIENT_ROLE_BROADCASTER
        }
        RtcEngineInstance.joinChannelEx(lowStreamConnection, lowStreamChannelMediaOptions,
            IChannelEventListener(
                onChannelJoined = {

                },
                onUserJoined = {

                },
                onUserOffline = {

                }
            ))
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