package com.example.data

import kotlinx.coroutines.flow.Flow

class RouterBitRepository(private val routerBitDao: RouterBitDao) {
    val allRouterBits: Flow<List<RouterBit>> = routerBitDao.getAllRouterBits()

    suspend fun getCount(): Int {
        return routerBitDao.getCount()
    }

    suspend fun insertRouterBit(bit: RouterBit): Long {
        return routerBitDao.insertRouterBit(bit)
    }

    suspend fun updateRouterBit(bit: RouterBit) {
        routerBitDao.updateRouterBit(bit)
    }

    suspend fun deleteRouterBit(bit: RouterBit) {
        routerBitDao.deleteRouterBit(bit)
    }
}
