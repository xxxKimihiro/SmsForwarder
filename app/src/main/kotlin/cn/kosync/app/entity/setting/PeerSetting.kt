package cn.kosync.app.entity.setting

import cn.kosync.app.utils.PeerSyncLogic
import java.io.Serializable

data class PeerSetting(
    var address: String = "",
    var port: Int = PeerSyncLogic.DEFAULT_PORT,
    var secret: String = "",
) : Serializable
