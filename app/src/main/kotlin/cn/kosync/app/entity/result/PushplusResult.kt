package cn.kosync.app.entity.result

data class PushplusResult(
    var code: Long,
    var msg: String,
    var data: String?,
    var count: Long?,
)