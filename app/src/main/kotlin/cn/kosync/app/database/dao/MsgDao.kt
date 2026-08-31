package cn.kosync.app.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import cn.kosync.app.database.entity.Msg
import cn.kosync.app.database.entity.MsgAndLogs
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface MsgDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(msg: Msg): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSync(msg: Msg): Long

    @Query("SELECT id FROM Msg WHERE peer_id = :peerId LIMIT 1")
    fun findIdByPeerId(peerId: String): Long?

    @Query("SELECT * FROM Msg WHERE time > :sinceTime OR (time = :sinceTime AND id > :sinceMsgId) ORDER BY time ASC, id ASC LIMIT :limit")
    fun listSince(sinceTime: Long, sinceMsgId: Long, limit: Int): List<Msg>

    @Delete
    fun delete(msg: Msg): Completable

    @Query("DELETE FROM Msg where id=:id")
    fun delete(id: Long)

    @RawQuery
    fun deleteAll(sql: SupportSQLiteQuery): Int

    @Query("DELETE FROM Msg")
    fun deleteAll()

    @Query("DELETE FROM Msg where time<:time")
    fun deleteTimeAgo(time: Long)

    @Update
    fun update(msg: Msg): Completable

    @Query("SELECT * FROM Msg where id=:id")
    fun get(id: Long): Single<Msg>

    @Query("SELECT count(*) FROM Msg where type=:type")
    fun count(type: String): Single<Int>

    @Transaction
    @Query("SELECT * FROM Msg WHERE type = :type ORDER BY id DESC")
    fun pagingSource(type: String): PagingSource<Int, MsgAndLogs>

    @Transaction
    @RawQuery(observedEntities = [MsgAndLogs::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, MsgAndLogs>

}