package io.agora.mediarelay

import android.view.LayoutInflater
import io.agora.mediarelay.baseui.BaseUiActivity
import io.agora.mediarelay.databinding.ActivityMainBinding

/**
 * @author create by zhangwei03
 */
class MainActivity : BaseUiActivity<ActivityMainBinding>() {
    override fun getViewBinding(inflater: LayoutInflater): ActivityMainBinding {
        return ActivityMainBinding.inflate(inflater)
    }
}