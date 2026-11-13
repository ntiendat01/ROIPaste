package com.roipaste.app.domain

class FetchClip(
    private val repository: TransferRepository,
) {
    suspend operator fun invoke(): Result<FetchedClip?> = repository.fetchClip()
}
