package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [GestureBindingEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gestureDao(): GestureDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gesture_reader_db"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDefaultBindings(database.gestureDao())
                    }
                }
            }
        }

        suspend fun populateDefaultBindings(dao: GestureDao) {
            val defaults = listOf(
                GestureBindingEntity("head_pitch_up", "swipe_down", true),
                GestureBindingEntity("head_pitch_down", "swipe_up", true),
                GestureBindingEntity("head_yaw_right", "swipe_left", true),
                GestureBindingEntity("head_yaw_left", "swipe_right", true),
                GestureBindingEntity("head_roll_right", "scroll_down_slow", true),
                GestureBindingEntity("head_roll_left", "scroll_up_slow", true),
                GestureBindingEntity("blink_both", "click_center", true),
                GestureBindingEntity("blink_left", "swipe_right", false),
                GestureBindingEntity("blink_right", "swipe_left", false),
                GestureBindingEntity("mouth_open", "click_center", false),
                GestureBindingEntity("smile", "click_center", false),
                GestureBindingEntity("gaze_look_right", "swipe_left", true),
                GestureBindingEntity("gaze_look_left", "swipe_right", true),
                GestureBindingEntity("gaze_look_up", "swipe_down", true),
                GestureBindingEntity("gaze_look_down", "swipe_up", true),
                GestureBindingEntity("gaze_dwell_corner", "swipe_left", true),
                GestureBindingEntity("hand_point_up", "swipe_up", true),
                GestureBindingEntity("hand_point_down", "swipe_down", true),
                GestureBindingEntity("hand_swipe_left", "swipe_left", true),
                GestureBindingEntity("hand_swipe_right", "swipe_right", true),
                GestureBindingEntity("hand_palm", "scroll_down_slow", false),
                GestureBindingEntity("hand_fist", "click_center", false),
                GestureBindingEntity("hand_thumbs_up", "click_center", false),
                GestureBindingEntity("hand_v_sign", "click_center", false)
            )
            dao.insertBindings(defaults)
        }
    }
}
