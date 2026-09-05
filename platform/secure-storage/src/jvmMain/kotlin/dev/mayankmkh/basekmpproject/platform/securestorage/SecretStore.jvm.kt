package dev.mayankmkh.basekmpproject.platform.securestorage

import androidx.datastore.core.Serializer
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import dev.mayankmkh.basekmpproject.foundation.runtime.applicationDataDirectory
import java.security.GeneralSecurityException

internal actual fun PlatformSecretStores.storeOpener(): (String) -> SecretStore = { name ->
    dataStoreSecretStore(
        name = name,
        logger = logger,
        produceSerializer = {
            val directory = applicationDataDirectory(context.applicationId)
            encryptedSerializer(
                name,
                applicationKeysetVault(context.applicationId, directory, logger),
            )
        },
    ) {
        applicationDataDirectory(context.applicationId)
            .also(::createOwnerOnly)
            .resolve("$name.secrets")
    }
}

internal fun encryptedSerializer(
    name: String,
    vault: KeysetVault,
): Serializer<Map<String, String>> {
    try {
        AeadConfig.register()
        return AeadSerializer(keysetAead(vault), MapStringSerializer, name.encodeToByteArray())
    } catch (failure: SecretStoreException) {
        throw failure
    } catch (failure: GeneralSecurityException) {
        throw SecretStoreException("Desktop secret keyset initialisation failed.", failure)
    }
}

private fun keysetAead(vault: KeysetVault): Aead =
    synchronized(vault) {
        val access = InsecureSecretKeyAccess.get()
        val stored = vault.read()
        val handle =
            if (stored == null) {
                KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).also {
                    vault.write(TinkJsonProtoKeysetFormat.serializeKeyset(it, access))
                }
            } else {
                TinkJsonProtoKeysetFormat.parseKeyset(stored, access)
            }
        handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }
