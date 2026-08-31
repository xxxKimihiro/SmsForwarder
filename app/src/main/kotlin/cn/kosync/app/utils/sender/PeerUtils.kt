package cn.kosync.app.utils.sender

import cn.kosync.app.database.entity.Rule
import cn.kosync.app.entity.MsgInfo
import cn.kosync.app.entity.setting.PeerSetting
import cn.kosync.app.server.model.BaseResponse
import cn.kosync.app.server.model.PeerIngestResult
import cn.kosync.app.server.model.PeerMessageData
import cn.kosync.app.server.model.PeerSyncData
import cn.kosync.app.utils.HTTP_SUCCESS_CODE
import cn.kosync.app.utils.HttpServerUtils
import cn.kosync.app.utils.Log
import cn.kosync.app.utils.PeerSyncLogic
import cn.kosync.app.utils.TsnetEngine
import cn.kosync.app.utils.SendUtils
import cn.kosync.app.utils.SettingUtils
import cn.kosync.app.utils.interceptor.LoggingInterceptor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xuexiang.xhttp2.XHttp
import com.xuexiang.xhttp2.callback.SimpleCallBack
import com.xuexiang.xhttp2.exception.ApiException
import java.net.InetSocketAddress
import java.net.Socket

class PeerUtils {
    companion object {

        private val TAG: String = PeerUtils::class.java.simpleName

        fun sendMsg(
            setting: PeerSetting,
            msgInfo: MsgInfo,
            rule: Rule? = null,
            senderIndex: Int = 0,
            logId: Long = 0L,
            msgId: Long = 0L
        ) {
            val timestamp = System.currentTimeMillis()
            val peerId = PeerSyncLogic.buildPeerId(
                SettingUtils.extraDeviceMark,
                if (msgId > 0) msgId else timestamp
            )
            val data = PeerMessageData(
                peerId = peerId,
                type = msgInfo.type,
                from = msgInfo.from,
                content = msgInfo.content,
                simSlot = msgInfo.simSlot,
                simInfo = msgInfo.simInfo,
                subId = msgInfo.subId,
                callType = msgInfo.callType,
                time = msgInfo.date.time,
                deviceMark = SettingUtils.extraDeviceMark
            )
            val body = buildRequestJson(data, setting.secret, timestamp)
            val requestUrl = PeerSyncLogic.buildBaseUrl(setting.address, setting.port) + "/peer/ingest"
            Log.i(TAG, "ingest $requestUrl peerId=$peerId via=${tsnetVia()}")

            val request = XHttp.post(requestUrl)
                .upJson(body)
                .keepJson(true)
                .ignoreHttpsCert()
            TsnetEngine.applySocksProxy(request)
            request
                .retryCount(SettingUtils.requestRetryTimes)
                .retryDelay(SettingUtils.requestDelayTime * 1000)
                .retryIncreaseDelay(SettingUtils.requestDelayTime * 1000)
                .timeStamp(true)
                .addInterceptor(LoggingInterceptor(logId))
                .execute(object : SimpleCallBack<String>() {
                    override fun onError(e: ApiException) {
                        Log.e(TAG, e.detailMessage)
                        SendUtils.updateLogs(logId, 0, e.displayMessage)
                        SendUtils.senderLogic(0, msgInfo, rule, senderIndex, msgId)
                    }

                    override fun onSuccess(response: String) {
                        Log.i(TAG, response)
                        val status = if (isIngestSuccess(response)) 2 else 0
                        SendUtils.updateLogs(logId, status, response)
                        SendUtils.senderLogic(status, msgInfo, rule, senderIndex, msgId)
                    }
                })
        }

        fun requestSync(
            address: String,
            port: Int,
            secret: String,
            sinceTime: Long,
            sinceMsgId: Long,
            limit: Int,
            onError: (String) -> Unit,
            onSuccess: (List<PeerMessageData>) -> Unit
        ) {
            val data = PeerSyncData(sinceTime, sinceMsgId, limit)
            val body = buildRequestJson(data, secret, System.currentTimeMillis())
            val requestUrl = PeerSyncLogic.buildBaseUrl(address, port) + "/peer/sync"
            Log.i(TAG, "sync $requestUrl sinceTime=$sinceTime sinceMsgId=$sinceMsgId via=${tsnetVia()}")

            val request = XHttp.post(requestUrl)
                .upJson(body)
                .keepJson(true)
                .ignoreHttpsCert()
            TsnetEngine.applySocksProxy(request)
            request
                .timeStamp(true)
                .execute(object : SimpleCallBack<String>() {
                    override fun onError(e: ApiException) {
                        Log.e(TAG, e.detailMessage)
                        onError(e.displayMessage)
                    }

                    override fun onSuccess(response: String) {
                        Log.i(TAG, response)
                        try {
                            val resp: BaseResponse<List<PeerMessageData>> = Gson().fromJson(
                                response,
                                object : TypeToken<BaseResponse<List<PeerMessageData>>>() {}.type
                            )
                            if (resp.code == HTTP_SUCCESS_CODE) {
                                onSuccess(resp.data ?: emptyList())
                            } else {
                                onError(resp.msg)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, e.message.toString())
                            onError(e.message ?: response)
                        }
                    }
                })
        }

        private fun tsnetVia(): String {
            return if (HttpServerUtils.enableTsnet && TsnetEngine.isRunning()) {
                "tsnet socks=${TsnetEngine.socksPort()} self=${TsnetEngine.selfIP()}"
            } else {
                "direct enableTsnet=${HttpServerUtils.enableTsnet} running=${TsnetEngine.isRunning()}"
            }
        }

        fun isReachable(address: String, port: Int, timeoutMs: Int = PeerSyncLogic.PROBE_TIMEOUT_MS): Boolean {
            if (HttpServerUtils.enableTsnet && TsnetEngine.isRunning()) {
                val ok = TsnetEngine.probe(address, port, timeoutMs)
                Log.d(TAG, "probe $address:$port via=tsnet ok=$ok")
                return ok
            }
            return try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    true
                }
            } catch (e: Exception) {
                Log.d(TAG, "peer unreachable $address:$port ${e.message}")
                false
            }
        }

        fun isIngestSuccess(response: String): Boolean {
            return try {
                val resp: BaseResponse<PeerIngestResult> = Gson().fromJson(
                    response,
                    object : TypeToken<BaseResponse<PeerIngestResult>>() {}.type
                )
                resp.code == HTTP_SUCCESS_CODE
            } catch (_: Exception) {
                false
            }
        }

        fun buildRequestJson(data: Any, secret: String, timestamp: Long = System.currentTimeMillis()): String {
            val msgMap: MutableMap<String, Any> = mutableMapOf()
            msgMap["timestamp"] = timestamp
            if (secret.isNotEmpty()) {
                msgMap["sign"] = HttpServerUtils.calcSign(timestamp.toString(), secret)
            }
            msgMap["data"] = data
            return Gson().toJson(msgMap)
        }
    }
}
