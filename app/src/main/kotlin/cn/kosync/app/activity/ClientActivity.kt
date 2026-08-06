package cn.kosync.app.activity

import android.os.Bundle
import androidx.viewbinding.ViewBinding
import cn.kosync.app.core.BaseActivity
import cn.kosync.app.fragment.ClientFragment

class ClientActivity : BaseActivity<ViewBinding?>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPage(ClientFragment::class.java)
    }
}