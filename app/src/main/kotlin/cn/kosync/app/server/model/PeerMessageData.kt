package cn.kosync.app.server.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PeerMessageData(
    @SerializedName("peer_id")
    var peerId: String = "",
    @SerializedName("msg_id")
    var msgId: Long = 0L,
    @SerializedName("type")
    var type: String = "sms",
    @SerializedName("from")
    var from: String = "",
    @SerializedName("content")
    var content: String = "",
    @SerializedName("sim_slot")
    var simSlot: Int = -1,
    @SerializedName("sim_info")
    var simInfo: String = "",
    @SerializedName("sub_id")
    var subId: Int = 0,
    @SerializedName("call_type")
    var callType: Int = 0,
    @SerializedName("time")
    var time: Long = 0L,
    @SerializedName("device_mark")
    var deviceMark: String = "",
) : Serializable
