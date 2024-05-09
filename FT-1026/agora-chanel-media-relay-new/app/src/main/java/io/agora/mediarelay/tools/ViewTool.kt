package io.agora.mediarelay.tools

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import io.agora.mediarelay.databinding.PopSwitchVideoLayoutBinding
import io.agora.mediarelay.widget.CommonPopupWindow
import io.agora.mediarelay.widget.PopAdapter
import io.agora.mediarelay.widget.PopAdapter.OnItemClickListener

object ViewTool {

    fun showPop(context: Context, view: View, data: Array<out String?>, selectIndex: Int, listener:
    OnItemClickListener) {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val width = view.width
        val height = view.height
        CommonPopupWindow.ViewDataBindingBuilder<PopSwitchVideoLayoutBinding>()
            .viewDataBinding(PopSwitchVideoLayoutBinding.inflate(LayoutInflater.from(context)))
            .width(width)
            .height((height * (data.size + 0.5)).toInt())
            .outsideTouchable(true)
            .focusable(true)
            .clippingEnabled(false)
            .alpha(0.618f)
            .intercept { popupWindow, view ->
                val adapter = PopAdapter(context, 1, data, selectIndex)
                view.listView.setAdapter(adapter)
                adapter.setOnItemClickListener(listener)
                adapter.setOnItemClickListener { position, text ->
                    popupWindow.dismiss()
                    listener.OnItemClick(position, text)
                }
            }
            .build<View>(context)
            .showAtLocation(
                view, Gravity.NO_GRAVITY,
                location[0],
                location[1] + height * 1.2.toInt()
            )
    }
}