package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GestureDao {
    @Query("SELECT * FROM gesture_bindings ORDER BY gestureId ASC")
    fun getAllBindings(): Flow<List<GestureBindingEntity>>

    @Query("SELECT * FROM gesture_bindings WHERE gestureId = :gestureId LIMIT 1")
    suspend fun getBindingById(gestureId: String): GestureBindingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBindings(bindings: List<GestureBindingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBinding(binding: GestureBindingEntity)

    @Update
    suspend fun updateBinding(binding: GestureBindingEntity)

    @Query("UPDATE gesture_bindings SET isEnabled = :isEnabled WHERE gestureId = :gestureId")
    suspend fun toggleBinding(gestureId: String, isEnabled: Boolean)

    @Query("UPDATE gesture_bindings SET actionId = :actionId WHERE gestureId = :gestureId")
    suspend fun updateAction(gestureId: String, actionId: String)
}
