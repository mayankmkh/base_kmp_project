package dev.mayankmkh.basekmpproject.shared.features.details.domain

internal interface DetailRepository {
    suspend fun getItem(id: String): Item
}
