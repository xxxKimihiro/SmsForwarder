package cn.kosync.app.server.controller

import android.content.Intent
import android.content.IntentFilter
import cn.kosync.app.utils.Log
import cn.kosync.app.App
import cn.kosync.app.entity.BatteryInfo
import cn.kosync.app.server.model.BaseRequest
import cn.kosync.app.server.model.EmptyData
import cn.kosync.app.utils.BatteryUtils
import com.yanzhenjie.andserver.annotation.*

@Suppress("PrivatePropertyName")
@RestController
@RequestMapping(path = ["/battery"])
class BatteryController {

    private val TAG: String = BatteryController::class.java.simpleName

    //远程查电量
    @CrossOrigin(methods = [RequestMethod.POST])
    @PostMapping("/query")
    fun query(@RequestBody bean: BaseRequest<EmptyData>): BatteryInfo {
        Log.d(TAG, bean.data.toString())

        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent: Intent? = App.context.registerReceiver(null, intentFilter)
        return BatteryUtils.getBatteryInfo(intent)
    }

}