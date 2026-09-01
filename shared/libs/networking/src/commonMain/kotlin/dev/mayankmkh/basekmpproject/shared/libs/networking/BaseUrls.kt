package dev.mayankmkh.basekmpproject.shared.libs.networking

data class BaseUrls
internal constructor(val main: String, val subscription: String, val auth: String)

// `main` points at JSONPlaceholder so the sample screens have a real endpoint to talk to; the
// other two are placeholders waiting for a real backend. Both environments share the one host
// because the sample has no staging deployment.

val stagingBaseUrls =
    BaseUrls(
        main = "https://jsonplaceholder.typicode.com",
        subscription = "https://subscription-staging.example.com",
        auth = "https://auth-staging.example.com",
    )

val prodBaseUrls =
    BaseUrls(
        main = "https://jsonplaceholder.typicode.com",
        subscription = "https://subscription.example.com",
        auth = "https://auth.example.com",
    )
