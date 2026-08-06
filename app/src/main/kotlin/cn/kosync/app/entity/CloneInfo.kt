package cn.kosync.app.entity

import com.google.gson.annotations.SerializedName
import cn.kosync.app.database.entity.Frpc
import cn.kosync.app.database.entity.Rule
import cn.kosync.app.database.entity.Sender
import cn.kosync.app.database.entity.Task
import java.io.Serializable

data class CloneInfo(
    @SerializedName("version_code")
    var versionCode: Int = 0,

    @SerializedName("version_name")
    var versionName: String? = null,

    @SerializedName("settings")
    var settings: String = "",

    @SerializedName("sender_list")
    var senderList: List<Sender>? = null,

    @SerializedName("rule_list")
    var ruleList: List<Rule>? = null,

    @SerializedName("frpc_list")
    var frpcList: List<Frpc>? = null,

    @SerializedName("task_list")
    var taskList: List<Task>? = null,
) : Serializable