package cn.kosync.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.kosync.app.core.Core
import cn.kosync.app.entity.setting.PeerSetting
import cn.kosync.app.utils.Log
import cn.kosync.app.utils.SendUtils
import cn.kosync.app.utils.TYPE_PEER
import cn.kosync.app.utils.task.TaskUtils
import cn.kosync.app.utils.sender.PeerUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PeerRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val TAG: String = PeerRetryWorker::class.java.simpleName

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (TaskUtils.networkState == 0) {
                Log.d(TAG, "skip retry: no network")
                return@withContext Result.success()
            }
            val senders = Core.sender.getAllNonCache().filter { it.type == TYPE_PEER && it.status == 1 }
            if (senders.isEmpty()) {
                return@withContext Result.success()
            }
            for (sender in senders) {
                val setting = try {
                    Gson().fromJson(sender.jsonSetting, PeerSetting::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "invalid peer setting sender=${sender.id}: ${e.message}")
                    null
                } ?: continue
                if (setting.address.isBlank() || !PeerUtils.isReachable(setting.address, setting.port)) {
                    Log.d(TAG, "peer not reachable sender=${sender.id} ${setting.address}:${setting.port}")
                    continue
                }
                val ids = Core.logs.getFailedIdsBySender(sender.id)
                Log.d(TAG, "retry ${ids.size} failed logs for sender=${sender.id}")
                for (id in ids) {
                    SendUtils.retrySendMsg(id)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PeerRetryWorker error: ${e.message}", e)
            Result.failure()
        }
    }
}
