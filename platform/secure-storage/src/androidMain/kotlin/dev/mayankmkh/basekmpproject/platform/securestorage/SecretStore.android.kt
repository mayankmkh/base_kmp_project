package dev.mayankmkh.basekmpproject.platform.securestorage

import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

internal actual fun createSecretStore(context: PlatformContext, name: String): SecretStore =
    dataStoreSecretStore(
        produceSerializer = {
            AeadSerializer(keystoreAead(context), MapStringSerializer, name.encodeToByteArray())
        },
        produceFile = { context.appContext.noBackupFilesDir.resolve("datastore/$name.secrets") },
    )

// One AES256_GCM keyset per app, stored in SharedPreferences and wrapped by an Android Keystore
// master key; the store name is the associated data, so files cannot be swapped between stores.
// `AeadConfig.register()` is idempotent, so every store may call it.

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
