package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gcode: String,
    val toolType: String, // "LASER" or "SPINDLE"
    val modeSettings: String, // Extra parameters stored as key-value pairs or JSON
    val width: Float = 150f,  // dimensions
    val height: Float = 150f,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
