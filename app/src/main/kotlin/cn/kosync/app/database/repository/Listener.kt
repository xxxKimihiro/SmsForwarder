package cn.kosync.app.database.repository

interface Listener {
    fun onDelete(id: Long)
}