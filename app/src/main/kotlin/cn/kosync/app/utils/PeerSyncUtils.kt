package cn.kosync.app.utils

import cn.kosync.app.core.Core
import cn.kosync.app.database.entity.Msg
import cn.kosync.app.server.model.PeerIngestResult
import cn.kosync.app.server.model.PeerMessageData
import cn.kosync.app.server.model.PeerSyncData
import com.yanzhenjie.andserver.error.HttpException
import java.util.Date

object PeerSyncUtils {
    private const val TAG = "PeerSyncUtils"

    fun ingest(data: PeerMessageData): PeerIngestResult {
        val peerId = data.peerId.trim()
        if (PeerSyncLogic.isBlankPeerId(peerId)) {
            throw HttpException(500, "peer_id required")
        }
        val existing = Core.msg.findIdByPeerId(peerId)
        if (PeerSyncLogic.isDuplicate(existing)) {
            Log.d(TAG, "skip duplicate peerId=$peerId existingId=$existing")
            return PeerIngestResult(ingested = false, duplicate = true, msgId = existing ?: 0)
        }
        val time = if (data.time > 0) Date(data.time) else Date()
        val msg = Msg(
            0,
            data.type.ifBlank { "sms" },
            data.from,
            data.content,
            data.simSlot,
            data.simInfo,
            data.subId,
            data.callType,
            time,
            peerId
        )
        val msgId = Core.msg.insertSync(msg)
        Log.d(TAG, "ingested peerId=$peerId msgId=$msgId")
        return PeerIngestResult(ingested = true, duplicate = false, msgId = msgId)
    }

    fun listForSync(req: PeerSyncData): List<PeerMessageData> {
        val limit = PeerSyncLogic.normalizeLimit(req.limit)
        val msgs = Core.msg.listSince(req.sinceTime, req.sinceMsgId, limit)
        val deviceMark = SettingUtils.extraDeviceMark
        return msgs.map { it.toPeerMessage(deviceMark) }
    }

    fun Msg.toPeerMessage(deviceMark: String): PeerMessageData {
        val peerId = if (this.peerId.isNotBlank()) this.peerId else PeerSyncLogic.buildPeerId(deviceMark, this.id)
        return PeerMessageData(
            peerId = peerId,
            msgId = this.id,
            type = this.type,
            from = this.from,
            content = this.content,
            simSlot = this.simSlot,
            simInfo = this.simInfo,
            subId = this.subId,
            callType = this.callType,
            time = this.time.time,
            deviceMark = deviceMark
        )
    }
}
