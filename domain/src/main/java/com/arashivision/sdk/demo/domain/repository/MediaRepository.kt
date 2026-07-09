package com.arashivision.sdk.demo.domain.repository

interface MediaRepository {
    fun loadWorks(): List<String>

    fun deleteWorks(ids: List<String>): Result<Unit>

    fun downloadWork(id: String): Result<Unit>
}
