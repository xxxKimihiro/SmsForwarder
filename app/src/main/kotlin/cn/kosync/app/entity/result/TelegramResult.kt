package cn.kosync.app.entity.result

data class TelegramResult(
    var ok: Boolean?,
    var message: String,
    var timestamp: Long?,
)