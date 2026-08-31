package cn.kosync.app.server.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PeerSyncData(
    @SerializedName("since_time")
    var sinceTime: Long = 0L,
    @SerializedName("since_msg_id")
    var sinceMsgId: Long = 0L,
    @SerializedName("limit")
    var limit: Int = 0,
) : Serializable
