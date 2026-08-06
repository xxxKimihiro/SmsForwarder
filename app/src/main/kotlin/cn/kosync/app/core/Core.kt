package cn.kosync.app.core

import android.app.Application
import androidx.work.Configuration
import cn.kosync.app.App
import cn.kosync.app.BuildConfig
import cn.kosync.app.database.repository.FrpcRepository
import cn.kosync.app.database.repository.LogsRepository
import cn.kosync.app.database.repository.MsgRepository
import cn.kosync.app.database.repository.RuleRepository
import cn.kosync.app.database.repository.SenderRepository
import cn.kosync.app.database.repository.TaskRepository
import cn.kosync.app.utils.Log
import kotlinx.coroutines.launch

object Core : Configuration.Provider {
    lateinit var app: Application
    val frpc: FrpcRepository by lazy { (app as App).frpcRepository }
    val msg: MsgRepository by lazy { (app as App).msgRepository }
    val logs: LogsRepository by lazy { (app as App).logsRepository }
    val rule: RuleRepository by lazy { (app as App).ruleRepository }
    val sender: SenderRepository by lazy { (app as App).senderRepository }
    val task: TaskRepository by lazy { (app as App).taskRepository }

    fun init(app: Application) {
        this.app = app
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder().apply {
            setDefaultProcessName(app.packageName + ":bg")
            setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.VERBOSE else Log.INFO)
            setExecutor { (app as App).applicationScope.launch { it.run() } }
            setTaskExecutor { (app as App).applicationScope.launch { it.run() } }
        }.build()
    }
}
