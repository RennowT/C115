package com.spvg.appspvg

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class LeituraResponse(
    val gas: Float,
    val temperature: Float,
    val pressure: Float,
    val timestamp: String
)

data class LogResponse(
    val state: String,
    val timestamp: String
)

interface ApiService {
    @GET("leituras/{mac}")
    suspend fun getLeituras(
        @Path("mac") mac: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): List<LeituraResponse>

    @GET("logs/{mac}")
    suspend fun getLogs(
        @Path("mac") mac: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): List<LogResponse>
}
