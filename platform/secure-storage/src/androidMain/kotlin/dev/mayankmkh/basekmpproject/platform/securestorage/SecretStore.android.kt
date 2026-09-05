package dev.mayankmkh.basekmpproject.platform.securestorage

import androidx.datastore.tink.AeadSerializer
import co.touchlab.kermit.Logger
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

internal actual fun secretStoreOpener(
    context: PlatformContext,
    logger: Logger,
): (String) -> SecretStore {
    // One keyset per app, unwrapped on the first read of the first store and shared by every store.
    val aead by lazy { keystoreAead(context) }
    return { name ->
        dataStoreSecretStore(
            name = name,
            logger = logger,
            produceSerializer = {
                AeadSerializer(aead, MapStringSerializer, name.encodeToByteArray())
            },
            produceFile = {
                context.appContext.noBackupFilesDir.resolve("datastore/$name.secrets")
            },
        )
    }
}

// One AES256_GCM keyset per app, stored in SharedPreferences and wrapped by an Android Keystore
// master key; the store name is the associated data, so files cannot be swapped between stores.

private fun keystoreAead(context: PlatformContext): Aead {
    AeadConfig.register()
    val keysetName = "${context.applicationId}.secure-storage.keyset"
    val preferencesName = "${context.applicationId}.secure-storage"
    val masterKeyUri = "android-keystore://${context.applicationId}.secure-storage"
    return AndroidKeysetManager.Builder()
        .withSharedPref(context.appContext, keysetName, preferencesName)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(masterKeyUri)
        .build()
        .keysetHandle
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}
