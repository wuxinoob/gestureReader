package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ActionType
import com.example.model.TriggerGesture

@Entity(tableName = "gesture_bindings")
data class GestureBindingEntity(
    @PrimaryKey
    val gestureId: String,
    val actionId: String,
    val isEnabled: Boolean = true,
    val customSensitivity: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toGesture(): TriggerGesture? = TriggerGesture.entries.find { it.id == gestureId }
    fun toAction(): ActionType = ActionType.entries.find { it.id == actionId } ?: ActionType.NONE
}
