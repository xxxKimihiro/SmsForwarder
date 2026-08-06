package cn.kosync.app.database.repository

import androidx.annotation.WorkerThread
import cn.kosync.app.database.dao.MsgDao
import cn.kosync.app.database.entity.Msg

class MsgRepository(private val msgDao: MsgDao) {

    @WorkerThread
    suspend fun insert(msg: Msg): Long = msgDao.insert(msg)

    @WorkerThread
    fun delete(id: Long) = msgDao.delete(id)

    fun deleteAll() = msgDao.deleteAll()

    @WorkerThread
    fun deleteTimeAgo(time: Long) = msgDao.deleteTimeAgo(time)

}