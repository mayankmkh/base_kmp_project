package dev.mayankmkh.basekmpproject.shared.app.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object ListRoute : NavKey

@Serializable data class DetailsRoute(val itemId: String) : NavKey
