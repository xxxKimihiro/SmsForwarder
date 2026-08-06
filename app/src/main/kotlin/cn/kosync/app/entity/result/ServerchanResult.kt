package cn.kosync.app.entity.result

data class ServerchanResult(
    var code: Long,
    var message: String,
    var data: Any?,
)