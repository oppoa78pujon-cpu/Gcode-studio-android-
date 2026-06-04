package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "router_bits")
data class RouterBit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "Straight Flute", "Upcut Spiral", "Downcut Spiral", "V-Bit", "Ball Nose", "Compression", etc.
    val diameter: Float, // tool diameter in mm
    val flutes: Int = 2,
    val isCustom: Boolean = true
) : Serializable
