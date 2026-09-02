
# Standalone ProGuard, unlike R8, ignores the META-INF/proguard rules that dependencies embed in
# their jars. Compose Desktop's own defaults cover coroutines, serialization and datetime, so what
# follows is the remainder that nothing else supplies.

# Ktor -- from ktor-utils' META-INF/proguard/ktor.pro. Precautionary: nothing here instantiates an
# engine yet, but `HttpClient { }` resolves one through ServiceLoader, so the container is never
# referenced statically and shrinking takes the engine package with it.
-keep class io.ktor.client.engine.** implements io.ktor.client.HttpClientEngineContainer
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}
-keepclassmembernames class io.ktor.** {
    volatile <fields>;
}

# DataStore -- from datastore-preferences-core's META-INF/proguard. Precautionary too; protobuf-lite
# resolves fields reflectively, so they can look unused.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# OkHttp, which Ktor's engine brings in, ships classes for alternative TLS providers and for
# GraalVM native-image builds. Nothing loads them here -- this is a plain JVM app and OkHttp probes
# for the providers reflectively -- but ProGuard resolves every reference eagerly and treats the
# dangling ones as fatal. OkHttp 5 no longer embeds rules of its own, so name them here.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.oracle.svm.core.annotate.**

# OkHttp derives a resource path from this class's package. Inert while the release build keeps
# ProGuard's default -dontobfuscate, but required the moment obfuscation is turned on.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
