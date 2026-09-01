package dev.mayankmkh.basekmpproject.shared.app.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable sealed interface AppRoute : NavKey

@Serializable data object ListRoute : AppRoute

@Serializable data class DetailsRoute(val itemId: String) : AppRoute
