package cn.kosync.app.utils

import android.annotation.SuppressLint
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import cn.kosync.app.R
import cn.kosync.app.core.Core
import cn.kosync.app.database.entity.MsgAndLogs
import cn.kosync.app.database.entity.Rule
import cn.kosync.app.entity.MsgInfo
import cn.kosync.app.entity.result.SendResponse
import cn.kosync.app.entity.setting.BarkSetting
import cn.kosync.app.entity.setting.DingtalkGroupRobotSetting
import cn.kosync.app.entity.setting.DingtalkInnerRobotSetting
import cn.kosync.app.entity.setting.EmailSetting
import cn.kosync.app.entity.setting.FeishuAppSetting
import cn.kosync.app.entity.setting.FeishuSetting
import cn.kosync.app.entity.setting.GotifySetting
import cn.kosync.app.entity.setting.PushplusSetting
import cn.kosync.app.entity.setting.ServerchanSetting
import cn.kosync.app.entity.setting.SmsSetting
import cn.kosync.app.entity.setting.PeerSetting
import cn.kosync.app.entity.setting.SocketSetting
import cn.kosync.app.entity.setting.TelegramSetting
import cn.kosync.app.entity.setting.UrlSchemeSetting
import cn.kosync.app.entity.setting.WebhookSetting
import cn.kosync.app.entity.setting.WeworkAgentSetting
import cn.kosync.app.entity.setting.WeworkRobotSetting
import cn.kosync.app.utils.sender.BarkUtils
import cn.kosync.app.utils.sender.DingtalkGroupRobotUtils
import cn.kosync.app.utils.sender.DingtalkInnerRobotUtils
import cn.kosync.app.utils.sender.EmailUtils
import cn.kosync.app.utils.sender.FeishuAppUtils
import cn.kosync.app.utils.sender.FeishuUtils
import cn.kosync.app.utils.sender.GotifyUtils
import cn.kosync.app.utils.sender.PushplusUtils
import cn.kosync.app.utils.sender.ServerchanUtils
import cn.kosync.app.utils.sender.SmsUtils
import cn.kosync.app.utils.sender.PeerUtils
import cn.kosync.app.utils.sender.SocketUtils
import cn.kosync.app.utils.sender.TelegramUtils
import cn.kosync.app.utils.sender.UrlSchemeUtils
import cn.kosync.app.utils.sender.WebhookUtils
import cn.kosync.app.utils.sender.WeworkAgentUtils
import cn.kosync.app.utils.sender.WeworkRobotUtils
import cn.kosync.app.workers.SendLogicWorker
import cn.kosync.app.workers.SendWorker
import cn.kosync.app.workers.UpdateLogsWorker
import com.google.gson.Gson
import com.jeremyliao.liveeventbus.LiveEventBus
import com.xuexiang.xutil.XUtil
import com.xuexiang.xutil.resource.ResUtils.getString
import java.util.Calendar

object SendUtils {
    private const val TAG = "SendUtils"

    //重新匹配规则并发送消息
    fun rematchSendMsg(item: MsgAndLogs) {
        val msgInfo = MsgInfo(item.msg.type, item.msg.from, item.msg.content, item.msg.time, item.msg.simInfo, item.msg.simSlot, item.msg.subId)
        Log.d(TAG, "msgInfo = $msgInfo")

        val request = OneTimeWorkRequestBuilder<SendWorker>().setInputData(
            workDataOf(
                Worker.SEND_MSG_INFO to Gson().toJson(msgInfo)
            )
        ).build()
        WorkManager.getInstance(XUtil.getContext()).enqueue(request)
    }

    //重试发送消息
    fun retrySendMsg(logId: Long) {
        val item = Core.logs.getOne(logId)
        val msgInfo = MsgInfo(item.msg.type, item.msg.from, item.msg.content, item.msg.time, item.msg.simInfo, item.msg.simSlot, item.msg.subId)
        Log.d(TAG, "msgInfo = $msgInfo")

        var senderIndex = 0
        for (sender in item.rule.senderList) {
            if (item.logs.senderId == sender.id) {
                Log.d(TAG, "sender = $sender")
                senderIndex = item.rule.senderList.indexOf(sender)
                break
            }
        }

        val rule = item.rule
        rule.senderLogic = SENDER_LOGIC_RETRY
        sendMsgSender(msgInfo, rule, senderIndex, logId, item.msg.id)
    }

    //匹配发送通道发送消息
    @SuppressLint("SimpleDateFormat")
    fun sendMsgSender(msgInfo: MsgInfo, rule: Rule, senderIndex: Int = 0, logId: Long = 0L, msgId: Long = 0L) {
        try {
            val sender = rule.senderList[senderIndex]
            if (sender.status != 1) {
                Log.d(TAG, "sender = $sender is disabled")
                updateLogs(logId, 0, getString(R.string.sender_disabled))
                senderLogic(0, msgInfo, rule, senderIndex, msgId)
                return
            }

            //免打扰(禁用转发)：日期段 与 时间段 为「与」关系，且至少配置一项才生效
            //未配置星期视为「不限星期」，未配置时段视为「不限时段」，二者都命中才拦截
            Log.d(TAG, "silentDayOfWeek = ${rule.silentDayOfWeek}, silentPeriodStart = ${rule.silentPeriodStart}, silentPeriodEnd = ${rule.silentPeriodEnd}")
            val silentDayOfWeek = rule.silentDayOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
            val dayConfigured = silentDayOfWeek.isNotEmpty()
            val periodConfigured = rule.silentPeriodStart != rule.silentPeriodEnd
            if (dayConfigured || periodConfigured) {
                val dayMatched = !dayConfigured || silentDayOfWeek.contains(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
                val periodMatched = !periodConfigured || DataProvider.isCurrentTimeInPeriod(rule.silentPeriodStart, rule.silentPeriodEnd)
                if (dayMatched && periodMatched) {
                    Log.d(TAG, "免打扰(禁用转发)时间段")
                    updateLogs(logId, 0, getString(R.string.silent_time_period))
                    senderLogic(0, msgInfo, rule, senderIndex, msgId)
                    return
                }
            }

            when (sender.type) {
                TYPE_DINGTALK_GROUP_ROBOT -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, DingtalkGroupRobotSetting::class.java)
                    DingtalkGroupRobotUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_EMAIL -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, EmailSetting::class.java)
                    EmailUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_BARK -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, BarkSetting::class.java)
                    BarkUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_WEBHOOK -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, WebhookSetting::class.java)
                    WebhookUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_WEWORK_ROBOT -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, WeworkRobotSetting::class.java)
                    WeworkRobotUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_WEWORK_AGENT -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, WeworkAgentSetting::class.java)
                    WeworkAgentUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_SERVERCHAN -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, ServerchanSetting::class.java)
                    ServerchanUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_TELEGRAM -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, TelegramSetting::class.java)
                    TelegramUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_SMS -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, SmsSetting::class.java)
                    SmsUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_FEISHU -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, FeishuSetting::class.java)
                    FeishuUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_PUSHPLUS -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, PushplusSetting::class.java)
                    PushplusUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_GOTIFY -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, GotifySetting::class.java)
                    GotifyUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_DINGTALK_INNER_ROBOT -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, DingtalkInnerRobotSetting::class.java)
                    DingtalkInnerRobotUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_FEISHU_APP -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, FeishuAppSetting::class.java)
                    FeishuAppUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_URL_SCHEME -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, UrlSchemeSetting::class.java)
                    UrlSchemeUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_SOCKET -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, SocketSetting::class.java)
                    SocketUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                TYPE_PEER -> {
                    val settingVo = Gson().fromJson(sender.jsonSetting, PeerSetting::class.java)
                    PeerUtils.sendMsg(settingVo, msgInfo, rule, senderIndex, logId, msgId)
                }

                else -> {
                    updateLogs(logId, 0, getString(R.string.unknown_sender))
                    senderLogic(0, msgInfo, rule, senderIndex, msgId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "sendMsgSender: ${e.message}")
            updateLogs(logId, 0, e.message.toString())
            senderLogic(0, msgInfo, rule, senderIndex, msgId)
        }
    }

    //发送通道执行逻辑：ALL=全部执行, UntilFail=失败即终止, UntilSuccess=成功即终止, Retry=重试发送
    fun senderLogic(status: Int, msgInfo: MsgInfo, rule: Rule?, senderIndex: Int = 0, msgId: Long = 0L) {
        if (rule == null || rule.senderLogic == SENDER_LOGIC_RETRY) return

        if (senderIndex < rule.senderList.count() - 1 && (rule.senderLogic == SENDER_LOGIC_ALL || (status == 2 && rule.senderLogic == SENDER_LOGIC_UNTIL_FAIL) || (status == 0 && rule.senderLogic == SENDER_LOGIC_UNTIL_SUCCESS))) {
            val request = OneTimeWorkRequestBuilder<SendLogicWorker>().setInputData(
                workDataOf(
                    Worker.SEND_MSG_INFO to Gson().toJson(msgInfo),
                    //Worker.ruleId to rule.id,
                    Worker.RULE to Gson().toJson(rule),
                    Worker.SENDER_INDEX to senderIndex + 1,
                    Worker.MSG_ID to msgId,
                )
            ).build()
            WorkManager.getInstance(XUtil.getContext()).enqueue(request)
        }
    }

    //更新转发日志状态
    fun updateLogs(logId: Long?, status: Int, response: String) {

        //自动任务的不需要吐司或者更新日志
        if (logId == -1L) return

        //测试的没有记录ID，这里取巧了
        if (logId == null || logId == 0L) {
            if (status == 2) {
                LiveEventBus.get(EVENT_TOAST_SUCCESS, String::class.java).post(getString(R.string.request_succeeded))
            } else if (status == 0) {
                LiveEventBus.get(EVENT_TOAST_ERROR, String::class.java).post(getString(R.string.request_failed) + response)
            }
            return
        }

        val sendResponse = SendResponse(logId, status, response)
        val request = OneTimeWorkRequestBuilder<UpdateLogsWorker>().setInputData(
            workDataOf(
                Worker.UPDATE_LOGS to Gson().toJson(sendResponse)
            )
        ).build()
        WorkManager.getInstance(XUtil.getContext()).enqueue(request)
    }

}