package cn.kosync.app.server.controller

import cn.kosync.app.utils.Log
import cn.kosync.app.entity.CallInfo
import cn.kosync.app.server.model.BaseRequest
import cn.kosync.app.server.model.CallQueryData
import cn.kosync.app.utils.PhoneUtils
import com.yanzhenjie.andserver.annotation.*

@Suppress("PrivatePropertyName")
@RestController
@RequestMapping(path = ["/call"])
class CallController {

    private val TAG: String = CallController::class.java.simpleName

    //远程查通话
    @CrossOrigin(methods = [RequestMethod.POST])
    @PostMapping("/query")
    fun query(@RequestBody bean: BaseRequest<CallQueryData>): List<CallInfo> {
        val callQueryData = bean.data
        Log.d(TAG, callQueryData.toString())

        val limit = callQueryData.pageSize
        val offset = (callQueryData.pageNum - 1) * limit
        return PhoneUtils.getCallInfoList(callQueryData.type, limit, offset, callQueryData.phoneNumber)
    }

}