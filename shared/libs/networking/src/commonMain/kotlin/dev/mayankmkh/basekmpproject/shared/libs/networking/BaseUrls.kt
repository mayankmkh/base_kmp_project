package dev.mayankmkh.basekmpproject.shared.libs.networking

@ConsistentCopyVisibility
data class BaseUrls
internal constructor(val main: String, val subscription: String, val auth: String)

val stagingBaseUrls =
    BaseUrls(
        main = "https://main-staging.example.com",
        subscription = "https://subscription-staging.example.com",
        auth = "https://auth-staging.example.com",
    )

val prodBaseUrls =
    BaseUrls(
        main = "https://main.example.com",
        subscription = "https://subscription.example.com",
        auth = "https://auth.example.com",
    )
