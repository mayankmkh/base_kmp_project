package dev.mayankmkh.basekmpproject.platform.securestorage

import androidx.datastore.core.Serializer
import androidx.datastore.tink.AeadSerializer
import co.touchlab.kermit.Logger
import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.applicationDataDirectory
import java.security.GeneralSecurityException

internal actual fun secretStoreOpener(
    context: PlatformContext,
    logger: Logger,
): (String) -> SecretStore {
    val directory = applicationDataDirectory(context.applicationId)
    // The OS vault is consulted on the first read of the first store; every store shares the
    // result.
    val aead by lazy {
        checkedKeysetAead(applicationKeysetVault(context.applicationId, directory, logger))
    }
    return { name ->
        dataStoreSecretStore(
            name = name,
            logger = logger,
            produceSerializer = {
                AeadSerializer(aead, MapStringSerializer, name.encodeToByteArray())
            },
        ) {
            directory.also(::createOwnerOnly).resolve("$name$SecretsFileSuffix")
        }
    }
}

internal fun encryptedSerializer(
    name: String,
    vault: KeysetVault,
): Serializer<Map<String, String>> =
    AeadSerializer(checkedKeysetAead(vault), MapStringSerializer, name.encodeToByteArray())

/**
 * The keyset's AEAD, with Tink's own failures reported as this module's exception; a vault's
 * [SecretStoreException] passes through as it is.
 */
private fun checkedKeysetAead(vault: KeysetVault): Aead {
    try {
        AeadConfig.register()
        return keysetAead(vault)
    } catch (failure: GeneralSecurityException) {
        throw SecretStoreException("Desktop secret keyset initialisation failed.", failure)
    } catch (failure: IllegalArgumentException) {
        // Tink's base-64 decoder rejects a damaged key value this way rather than as a security
        // failure; it is still a keyset that cannot be read.
        throw SecretStoreException("Desktop secret keyset initialisation failed.", failure)
    }
}

private fun keysetAead(vault: KeysetVault): Aead =
    synchronized(vault) {
        val access = InsecureSecretKeyAccess.get()
        val stored = vault.read()
        val handle =
            if (stored == null) {
                KeysetHandle.generateNew(
                        KeyTemplates.get("AES256_GCM").toParameters(),
                        RegistryConfiguration.get(),
                    )
                    .also {
                        vault.write(
                            TinkJsonProtoKeysetFormat.serializeKeyset(
                                it,
                                RegistryConfiguration.get(),
                                access,
                            )
                        )
                    }
            } else {
                TinkJsonProtoKeysetFormat.parseKeyset(stored, RegistryConfiguration.get(), access)
            }
        handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }
