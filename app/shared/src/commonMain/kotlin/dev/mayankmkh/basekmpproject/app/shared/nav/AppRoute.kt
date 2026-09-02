package dev.mayankmkh.basekmpproject.app.shared.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object PostFeedRoute : NavKey

@Serializable data class PostDetailRoute(val id: Long) : NavKey
