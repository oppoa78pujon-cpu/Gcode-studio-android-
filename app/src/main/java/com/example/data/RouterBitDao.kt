package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RouterBitDao {
    @Query("SELECT COUNT(*) FROM router_bits")
    suspend fun getCount(): Int

    @Query("SELECT * FROM router_bits ORDER BY id ASC")
    fun getAllRouterBits(): Flow<List<RouterBit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouterBit(bit: RouterBit): Long

    @Update
    suspend fun updateRouterBit(bit: RouterBit)

    @Delete
    suspend fun deleteRouterBit(bit: RouterBit)

    @Query("DELETE FROM router_bits")
    suspend fun deleteAllRouterBits()
}
