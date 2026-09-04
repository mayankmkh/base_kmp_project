package dev.mayankmkh.basekmpproject.platform.securestorage

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

@Suppress("SpreadOperator")
internal class LibSecretKeysetVault
private constructor(
    private val applicationId: String,
    private val secret: LibSecret,
    private val glib: Glib,
) : KeysetVault {
    private val schema = SecretSchema("$applicationId.secure-storage")

    internal constructor(
        applicationId: String
    ) : this(
        applicationId,
        Native.load("secret-1", LibSecret::class.java),
        Native.load("glib-2.0", Glib::class.java),
    )

    override fun read(): String? =
        libsecretOperation("lookup") {
            val error = PointerByReference()
            val password =
                secret.secret_password_lookup_sync(
                    schema,
                    null,
                    error,
                    *attributes(),
                )
            takeError(error)?.raise("lookup")
            if (password == null) return@libsecretOperation null
            try {
                password.getString(0, Charsets.UTF_8.name())
            } finally {
                secret.secret_password_free(password)
            }
        }

    override fun write(json: String) {
        libsecretOperation("store") {
            val error = PointerByReference()
            val stored =
                secret.secret_password_store_sync(
                    schema,
                    SecretCollectionDefault,
                    "$applicationId secure storage keyset",
                    json,
                    null,
                    error,
                    *attributes(),
                )
            val nativeError = takeError(error)
            if (nativeError != null) nativeError.raise("store")
            if (stored == 0) {
                val cause = IllegalStateException("libsecret returned false without a GError.")
                throw SecretStoreException("Linux libsecret keyset store failed.", cause)
            }
        }
    }

    private fun attributes(): Array<Any?> =
        arrayOf(
            "application",
            applicationId,
            "purpose",
            KeysetPurpose,
            null,
        )

    private fun takeError(reference: PointerByReference): SecretServiceError? {
        val pointer = reference.value ?: return null
        return try {
            val error = GError(pointer)
            SecretServiceError(
                domain = error.domain,
                code = error.code,
                message = error.message?.getString(0, Charsets.UTF_8.name()).orEmpty(),
            )
        } finally {
            glib.g_error_free(pointer)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> libsecretOperation(operation: String, block: () -> T): T =
        try {
            block()
        } catch (failure: KeysetVaultUnavailableException) {
            throw failure
        } catch (failure: SecretStoreException) {
            throw failure
        } catch (failure: LinkageError) {
            throw KeysetVaultUnavailableException(
                "libsecret-1 became unavailable during $operation: ${failure.message}",
                failure,
            )
        } catch (failure: Exception) {
            throw SecretStoreException("Linux libsecret keyset $operation failed.", failure)
        }
}

@Suppress("FunctionNaming", "LongParameterList")
private interface LibSecret : Library {
    fun secret_password_lookup_sync(
        schema: SecretSchema,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?,
    ): Pointer?

    fun secret_password_store_sync(
        schema: SecretSchema,
        collection: String,
        label: String,
        password: String,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?,
    ): Int

    fun secret_password_free(password: Pointer)
}

@Suppress("FunctionNaming")
private interface Glib : Library {
    fun g_error_free(error: Pointer)
}

@Structure.FieldOrder("name", "type")
private class SecretSchemaAttribute : Structure() {
    @JvmField var name: String? = null
    @JvmField var type: Int = SecretSchemaAttributeString
}

@Structure.FieldOrder(
    "name",
    "flags",
    "attributes",
    "reserved",
    "reserved1",
    "reserved2",
    "reserved3",
    "reserved4",
    "reserved5",
    "reserved6",
    "reserved7",
)
private class SecretSchema(schemaName: String) : Structure() {
    @JvmField var name: String = schemaName
    @JvmField var flags: Int = 0
    @JvmField
    var attributes: Array<SecretSchemaAttribute> =
        Array(SecretSchemaMaxAttribute) { SecretSchemaAttribute() }
    @JvmField var reserved: Int = 0
    @JvmField var reserved1: Pointer? = null
    @JvmField var reserved2: Pointer? = null
    @JvmField var reserved3: Pointer? = null
    @JvmField var reserved4: Pointer? = null
    @JvmField var reserved5: Pointer? = null
    @JvmField var reserved6: Pointer? = null
    @JvmField var reserved7: Pointer? = null

    init {
        attributes[0].name = "application"
        attributes[1].name = "purpose"
    }
}

@Structure.FieldOrder("domain", "code", "message")
private class GError(pointer: Pointer) : Structure(pointer) {
    @JvmField var domain: Int = 0
    @JvmField var code: Int = 0
    @JvmField var message: Pointer? = null

    init {
        read()
    }
}

private data class SecretServiceError(val domain: Int, val code: Int, val message: String) {
    fun raise(operation: String): Nothing {
        val detail = message.ifBlank { "unknown libsecret error" }
        val cause = IllegalStateException("libsecret GError $domain:$code: $detail")
        if (detail.isUnavailableSecretServiceMessage()) {
            throw KeysetVaultUnavailableException(
                "libsecret-1 is unavailable during $operation: $detail",
                cause,
            )
        }
        throw SecretStoreException(
            "Linux libsecret keyset $operation failed with GError $domain:$code: $detail",
            cause,
        )
    }
}

private fun String.isUnavailableSecretServiceMessage(): Boolean {
    val normalised = lowercase()
    return UnavailableMessages.any(normalised::contains) ||
        (normalised.contains("no such file or directory") &&
            (normalised.contains("bus") || normalised.contains("connect")))
}

private const val SecretSchemaMaxAttribute = 32
private const val SecretSchemaAttributeString = 0
private const val SecretCollectionDefault = "default"
private const val KeysetPurpose = "secure-storage"
private val UnavailableMessages =
    listOf(
        "cannot autolaunch",
        "connection refused",
        "d-bus",
        "dbus",
        "no session bus",
        "not provided by any .service",
        "service unknown",
    )
