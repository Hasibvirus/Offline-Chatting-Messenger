package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class RoomEnumConverters {
    @TypeConverter fun fromConversationType(v: ConversationType): String = v.name
    @TypeConverter fun toConversationType(v: String): ConversationType = ConversationType.valueOf(v)

    @TypeConverter fun fromMemberRole(v: MemberRole): String = v.name
    @TypeConverter fun toMemberRole(v: String): MemberRole = MemberRole.valueOf(v)

    @TypeConverter fun fromPeerStatus(v: PeerConnectionStatus): String = v.name
    @TypeConverter fun toPeerStatus(v: String): PeerConnectionStatus = PeerConnectionStatus.valueOf(v)

    @TypeConverter fun fromMessageType(v: MessageType): String = v.name
    @TypeConverter fun toMessageType(v: String): MessageType = MessageType.valueOf(v)

    @TypeConverter fun fromDeliveryStatus(v: DeliveryStatus): String = v.name
    @TypeConverter fun toDeliveryStatus(v: String): DeliveryStatus = DeliveryStatus.valueOf(v)

    @TypeConverter fun fromTransportKind(v: TransportKind): String = v.name
    @TypeConverter fun toTransportKind(v: String): TransportKind = TransportKind.valueOf(v)
}

@Database(
    entities = [
        ConversationEntity::class,
        MemberEntity::class,
        MessageEntity::class,
        PollEntity::class,
        PollVoteEntity::class,
        SosAlertEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(RoomEnumConverters::class)
abstract class OfflineMessengerDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memberDao(): MemberDao
    abstract fun messageDao(): MessageDao
    abstract fun pollDao(): PollDao
    abstract fun sosDao(): SosDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_messages_senderId ON messages(senderId)"
                )
            }
        }

        fun create(context: Context): OfflineMessengerDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                OfflineMessengerDatabase::class.java,
                "offline_messenger.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                .build()
        }
    }
}
