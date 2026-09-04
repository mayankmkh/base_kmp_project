@file:OptIn(ExperimentalForeignApi::class)

package dev.mayankmkh.basekmpproject.platform.securestorage

import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

// One Keychain service per store keeps `clear()` on one namespace from touching another.
internal actual fun createSecretStore(context: PlatformContext, name: String): SecretStore =
    KeychainSecretStore(service = "${context.applicationId}.$name")

/**
 * Writes go to the Keychain first and to the in-memory snapshot second, so the snapshot never holds
 * a value the device refused. The `SecItem*` calls block, so they run on `Dispatchers.IO` rather
 * than on the caller, which on iOS is normally the main dispatcher.
 */
@OptIn(ExperimentalAtomicApi::class)
private class KeychainSecretStore(private val service: String) : SecretStore {
    private val mutex = Mutex()
    private val snapshot = MemorySecretStore()
    private val loaded = AtomicBoolean(false)

    override suspend fun get(key: String): String? {
        ensureLoaded()
        return snapshot.get(key)
    }

    override fun observe(key: String): Flow<String?> = flow {
        ensureLoaded()
        emitAll(snapshot.observe(key))
    }

    override suspend fun set(key: String, value: String) {
        mutex.withLock {
            loadLocked()
            keychain { keychainSet(service, key, value) }
            snapshot.set(key, value)
        }
    }

    override suspend fun remove(key: String) {
        mutex.withLock {
            loadLocked()
            keychain { keychainDelete(service, account = key) }
            snapshot.remove(key)
        }
    }

    // Clearing does not need the current contents, so a store that is only ever cleared, as on
    // sign-out before any read, never pays for the initial Keychain query.
    override suspend fun clear() {
        mutex.withLock {
            keychain { keychainDelete(service) }
            snapshot.clear()
            loaded.store(true)
        }
    }

    // Reads after the first one skip the mutex; `loaded` only ever flips to true, under the lock.
    private suspend fun ensureLoaded() {
        if (loaded.load()) return
        mutex.withLock { loadLocked() }
    }

    private suspend fun loadLocked() {
        if (loaded.load()) return
        snapshot.replaceAll(keychain { keychainReadAll(service) })
        loaded.store(true)
    }

    private suspend fun <T> keychain(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

private fun keychainReadAll(service: String): Map<String, String> =
    withServiceQuery(service) { query ->
        query.set(kSecMatchLimit, kSecMatchLimitAll)
        query.set(kSecReturnAttributes, kCFBooleanTrue)
        query.set(kSecReturnData, kCFBooleanTrue)
        memScoped {
            val result = alloc<CPointerVarOf<COpaquePointer>>()
            when (val status = SecItemCopyMatching(query.dictionary, result.ptr)) {
                errSecItemNotFound -> emptyMap()
                errSecSuccess -> result.value?.let(::decodeItems).orEmpty()
                else -> keychainFailure("read", status)
            }
        }
    }

private fun keychainSet(service: String, account: String, value: String) {
    val bytes = value.encodeToByteArray()
    withServiceQuery(service) { query ->
        query.setString(kSecAttrAccount, account)
        withQuery { attributes ->
            attributes.setData(kSecValueData, bytes)
            when (val status = SecItemUpdate(query.dictionary, attributes.dictionary)) {
                errSecSuccess -> Unit
                errSecItemNotFound -> {
                    query.setData(kSecValueData, bytes)
                    query.set(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                    val addStatus = SecItemAdd(query.dictionary, null)
                    if (addStatus != errSecSuccess) keychainFailure("add", addStatus)
                }
                else -> keychainFailure("update", status)
            }
        }
    }
}

/** Deletes one item of the service, or with [account] null, every item of the service. */
private fun keychainDelete(service: String, account: String? = null) {
    withServiceQuery(service) { query ->
        if (account != null) query.setString(kSecAttrAccount, account)
        val status = SecItemDelete(query.dictionary)
        if (status != errSecSuccess && status != errSecItemNotFound) {
            keychainFailure("delete", status)
        }
    }
}

private inline fun <T> withServiceQuery(service: String, block: (KeychainQuery) -> T): T =
    withQuery { query ->
        query.set(kSecClass, kSecClassGenericPassword)
        query.setString(kSecAttrService, service)
        block(query)
    }

private inline fun <T> withQuery(block: (KeychainQuery) -> T): T {
    val dictionary =
        requireNotNull(
            CFDictionaryCreateMutable(
                allocator = null,
                capacity = 0,
                keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
                valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
            )
        )
    return try {
        block(KeychainQuery(dictionary))
    } finally {
        CFRelease(dictionary)
    }
}

private class KeychainQuery(val dictionary: CFMutableDictionaryRef) {
    fun set(key: CValuesRef<*>?, value: CValuesRef<*>?) {
        CFDictionarySetValue(dictionary, key, value)
    }

    fun setString(key: CValuesRef<*>?, value: String) {
        val string = requireNotNull(CFStringCreateWithCString(null, value, kCFStringEncodingUTF8))
        try {
            set(key, string)
        } finally {
            CFRelease(string)
        }
    }

    fun setData(key: CValuesRef<*>?, value: ByteArray) {
        val data = value.usePinned { bytes ->
            CFDataCreate(
                allocator = null,
                bytes = if (value.isEmpty()) null else bytes.addressOf(0).reinterpret(),
                length = value.size.toLong(),
            )
        }
        try {
            set(key, data)
        } finally {
            CFRelease(data)
        }
    }
}

private fun decodeItems(result: COpaquePointer): Map<String, String> {
    val array: CFArrayRef = result.reinterpret()
    return try {
        buildMap {
            repeat(CFArrayGetCount(array).toInt()) { index ->
                val dictionary: CFDictionaryRef =
                    CFArrayGetValueAtIndex(array, index.toLong())?.reinterpret() ?: return@repeat
                val account = CFDictionaryGetValue(dictionary, kSecAttrAccount)?.asString()
                val value = CFDictionaryGetValue(dictionary, kSecValueData)?.asDataString()
                if (account != null && value != null) put(account, value)
            }
        }
    } finally {
        CFRelease(result)
    }
}

private fun COpaquePointer.asString(): String? {
    val value: CFStringRef = reinterpret()
    val capacity =
        CFStringGetMaximumSizeForEncoding(CFStringGetLength(value), kCFStringEncodingUTF8) + 1
    val buffer = ByteArray(capacity.toInt())
    return buffer.usePinned { bytes ->
        if (CFStringGetCString(value, bytes.addressOf(0), capacity, kCFStringEncodingUTF8)) {
            bytes.addressOf(0).toKString()
        } else {
            null
        }
    }
}

private fun COpaquePointer.asDataString(): String {
    val value: CFDataRef = reinterpret()
    val size = CFDataGetLength(value).toInt()
    val bytes = CFDataGetBytePtr(value)
    return bytes?.readBytes(size)?.decodeToString().orEmpty()
}

private fun keychainFailure(operation: String, status: Int): Nothing =
    throw SecretStoreException("Keychain $operation failed with OSStatus $status.")
