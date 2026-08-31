package cn.kosync.app.server.controller

import cn.kosync.app.server.model.BaseRequest
import cn.kosync.app.server.model.PeerIngestResult
import cn.kosync.app.server.model.PeerMessageData
import cn.kosync.app.server.model.PeerSyncData
import cn.kosync.app.utils.Log
import cn.kosync.app.utils.PeerSyncUtils
import com.yanzhenjie.andserver.annotation.CrossOrigin
import com.yanzhenjie.andserver.annotation.PostMapping
import com.yanzhenjie.andserver.annotation.RequestBody
import com.yanzhenjie.andserver.annotation.RequestMapping
import com.yanzhenjie.andserver.annotation.RequestMethod
import com.yanzhenjie.andserver.annotation.RestController

@Suppress("PrivatePropertyName")
@RestController
@RequestMapping(path = ["/peer"])
class PeerController {

    private val TAG: String = PeerController::class.java.simpleName

    @CrossOrigin(methods = [RequestMethod.POST])
    @PostMapping("/ingest")
    fun ingest(@RequestBody bean: BaseRequest<PeerMessageData>): PeerIngestResult {
        val data = bean.data
        Log.d(TAG, "ingest peerId=${data.peerId} type=${data.type}")
        return PeerSyncUtils.ingest(data)
    }

    @CrossOrigin(methods = [RequestMethod.POST])
    @PostMapping("/sync")
    fun sync(@RequestBody bean: BaseRequest<PeerSyncData>): List<PeerMessageData> {
        val data = bean.data
        Log.d(TAG, "sync sinceTime=${data.sinceTime} sinceMsgId=${data.sinceMsgId} limit=${data.limit}")
        return PeerSyncUtils.listForSync(data)
    }
}
