package com.arashivision.sdk.demo.data.media

import com.arashivision.sdk.demo.domain.repository.MediaRepository

class Insta360MediaRepository : MediaRepository {
    override fun loadWorks(): List<String> = emptyList()

    override fun deleteWorks(ids: List<String>): Result<Unit> =
        Result.failure(UnsupportedOperationException("Delete works not yet implemented"))

    override fun downloadWork(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("Download work not yet implemented"))
}
