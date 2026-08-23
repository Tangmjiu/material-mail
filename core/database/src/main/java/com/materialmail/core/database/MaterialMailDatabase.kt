package com.materialmail.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.materialmail.core.database.dao.AccountDao
import com.materialmail.core.database.dao.AttachmentDao
import com.materialmail.core.database.dao.DraftDao
import com.materialmail.core.database.dao.FolderDao
import com.materialmail.core.database.dao.LabelDao
import com.materialmail.core.database.dao.MessageDao
import com.materialmail.core.database.dao.ThreadDao
import com.materialmail.core.database.entity.AccountEntity
import com.materialmail.core.database.entity.AttachmentEntity
import com.materialmail.core.database.entity.DraftEntity
import com.materialmail.core.database.entity.FolderEntity
import com.materialmail.core.database.entity.LabelEntity
import com.materialmail.core.database.entity.MessageEntity
import com.materialmail.core.database.entity.ThreadEntity

@Database(
    entities = [
        AccountEntity::class,
        FolderEntity::class,
        ThreadEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        DraftEntity::class,
        LabelEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MaterialMailDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun threadDao(): ThreadDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun draftDao(): DraftDao
    abstract fun labelDao(): LabelDao

    companion object {
        const val NAME = "material-mail.db"
    }
}