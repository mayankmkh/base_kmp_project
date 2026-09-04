package dev.mayankmkh.basekmpproject.app.shared.config

import io.ktor.http.Url

// JSONPlaceholder gives the sample screens a real endpoint to talk to.
internal val apiBaseUrl = Url("https://jsonplaceholder.typicode.com")

// This names the app's own storage and must never change after a build ships.
internal const val ApplicationId = "dev.mayankmkh.basekmpproject"
