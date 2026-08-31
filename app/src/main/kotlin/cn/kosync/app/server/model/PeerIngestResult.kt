package cn.kosync.app.server.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PeerIngestResult(
    @SerializedName("ingested")
    var ingested: Boolean = false,
    @SerializedName("duplicate")
    var duplicate: Boolean = false,
    @SerializedName("msg_id")
    var msgId: Long = 0L,
) : Serializable
