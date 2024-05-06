package io.agora.mediarelay.baseui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import io.agora.mediarelay.MainActivity
import io.agora.mediarelay.tools.LogTool
import io.agora.mediarelay.tools.PermissionHelp
import io.agora.rtc2.Constants

abstract class BaseUiFragment<B : ViewBinding> : Fragment() {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    protected fun runOnMainThread(runnable: Runnable) {
        if (Thread.currentThread() === mainHandler.looper.thread) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    private var permissionHelp: PermissionHelp? = null

    lateinit var binding: B

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivity) {
            permissionHelp = context.permissionHelp
        }
    }

    protected fun checkRequirePerms(
        force: Boolean = false,
        denied: (() -> Unit)? = null,
        granted: () -> Unit
    ) {
        permissionHelp?.checkCameraAndMicPerms(
            granted = { granted.invoke() },
            unGranted = { denied?.invoke() },
            force = force
        )
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = getViewBinding(inflater, container) ?: return null
        this.binding = binding
        return this.binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemInset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            LogTool.d("systemInset l:${systemInset.left},t:${systemInset.top},r:${systemInset.right},b:${systemInset.bottom}")
            binding.root.setPaddingRelative(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onResume() {
        activity?.let {
            val insetsController = WindowCompat.getInsetsController(it.window, it.window.decorView)
            insetsController.isAppearanceLightStatusBars = false
        }
        super.onResume()
    }

    protected fun parentAct(): BaseUiActivity<*>? {
        if (activity is BaseUiActivity<*>?) return activity as BaseUiActivity<*>?
        return null
    }

    protected abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): B?

    fun hideKeyboard() {
        activity?.apply {
            val imm = getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
            if (window.attributes.softInputMode != WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN) {
                if (currentFocus != null) {
                    imm.hideSoftInputFromWindow(currentFocus?.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                }
            }
        }
    }

    open fun showKeyboard(editText: EditText) {
        val imm = editText.context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, 0)
    }
}