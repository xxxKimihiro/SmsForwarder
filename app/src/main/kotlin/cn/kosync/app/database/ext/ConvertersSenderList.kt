package cn.kosync.app.database.ext

import androidx.room.TypeConverter
import cn.kosync.app.core.Core
import cn.kosync.app.database.entity.Sender

class ConvertersSenderList {

    @TypeConverter
    fun stringToObject(value: String): List<Sender> {
        return Core.sender.getByIds(value.split(",").map { it.trim().toLong() }, value)
    }

    @TypeConverter
    fun objectToString(list: List<Sender>): String {
        return list.joinToString(",") { it.id.toString() }
    }
}