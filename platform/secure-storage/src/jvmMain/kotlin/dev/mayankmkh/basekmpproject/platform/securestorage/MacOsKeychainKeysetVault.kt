package dev.mayankmkh.basekmpproject.platform.securestorage

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

internal class MacOsKeychainKeysetVault
private constructor(
    private val service: String,
    private val account: String,
    private val security: MacSecurity,
) : KeysetVault {
    internal constructor(
        service: String
    ) : this(service, KeysetAccount, Native.load(SecurityFramework, MacSecurity::class.java))

    override fun read(): String? =
        keychainOperation("read") {
            withNativeStrings { servicePointer, serviceSize, accountPointer, accountSize ->
                val passwordSize = IntByReference()
                val passwordData = PointerByReference()
                val status =
                    security.SecKeychainFindGenericPassword(
                        null,
                        serviceSize,
                        servicePointer,
                        accountSize,
                        accountPointer,
                        passwordSize,
                        passwordData,
                        null,
                    )
                when (status) {
                    ErrSecItemNotFound -> null
                    ErrSecSuccess ->
                        try {
                            requireNotNull(passwordData.value) {
                                    "Keychain returned no password data for an existing item."
                                }
                                .getByteArray(0, passwordSize.value)
                                .decodeToString()
                        } finally {
                            freePasswordContent(passwordData.value)
                        }
                    else -> macKeychainFailure("read", status)
                }
            }
        }

    override fun write(json: String) {
        keychainOperation("write") {
            val item = findItem()
            if (item == null) {
                add(json)
            } else {
                try {
                    modify(item, json)
                } finally {
                    CoreFoundation.CFTypeRef(item).release()
                }
            }
        }
    }

    internal fun delete() {
        keychainOperation("delete") {
            val item = findItem() ?: return@keychainOperation
            try {
                val status = security.SecKeychainItemDelete(item)
                if (status != ErrSecSuccess) macKeychainFailure("delete", status)
            } finally {
                CoreFoundation.CFTypeRef(item).release()
            }
        }
    }

    private fun findItem(): Pointer? =
        withNativeStrings { servicePointer, serviceSize, accountPointer, accountSize ->
            val passwordSize = IntByReference()
            val passwordData = PointerByReference()
            val item = PointerByReference()
            val status =
                security.SecKeychainFindGenericPassword(
                    null,
                    serviceSize,
                    servicePointer,
                    accountSize,
                    accountPointer,
                    passwordSize,
                    passwordData,
                    item,
                )
            when (status) {
                ErrSecItemNotFound -> null
                ErrSecSuccess -> {
                    freePasswordContent(passwordData.value)
                    item.value ?: macKeychainFailure("read item reference", status)
                }
                else -> macKeychainFailure("read", status)
            }
        }

    private fun add(json: String) {
        withNativeStrings { servicePointer, serviceSize, accountPointer, accountSize ->
            val password = json.encodeToByteArray()
            val status =
                security.SecKeychainAddGenericPassword(
                    null,
                    serviceSize,
                    servicePointer,
                    accountSize,
                    accountPointer,
                    password.size,
                    password.nativeBytes(),
                    null,
                )
            if (status != ErrSecSuccess) macKeychainFailure("add", status)
        }
    }

    private fun modify(item: Pointer, json: String) {
        val password = json.encodeToByteArray()
        val status =
            security.SecKeychainItemModifyAttributesAndData(
                item,
                null,
                password.size,
                password.nativeBytes(),
            )
        if (status != ErrSecSuccess) macKeychainFailure("update", status)
    }

    private fun freePasswordContent(passwordData: Pointer?) {
        if (passwordData == null) return
        val status = security.SecKeychainItemFreeContent(null, passwordData)
        if (status != ErrSecSuccess) macKeychainFailure("free password content", status)
    }

    private inline fun <T> withNativeStrings(block: (Pointer, Int, Pointer, Int) -> T): T {
        val serviceBytes = service.encodeToByteArray()
        val accountBytes = account.encodeToByteArray()
        return block(
            serviceBytes.nativeBytes(),
            serviceBytes.size,
            accountBytes.nativeBytes(),
            accountBytes.size,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> keychainOperation(operation: String, block: () -> T): T =
        try {
            block()
        } catch (failure: SecretStoreException) {
            throw failure
        } catch (failure: LinkageError) {
            throw KeysetVaultUnavailableException(
                "macOS Keychain became unavailable during $operation: ${failure.message}",
                failure,
            )
        } catch (failure: Exception) {
            throw SecretStoreException("macOS Keychain keyset $operation failed.", failure)
        }
}

@Suppress("FunctionNaming", "LongParameterList")
private interface MacSecurity : Library {
    fun SecKeychainFindGenericPassword(
        keychainOrArray: Pointer?,
        serviceNameLength: Int,
        serviceName: Pointer,
        accountNameLength: Int,
        accountName: Pointer,
        passwordLength: IntByReference,
        passwordData: PointerByReference,
        itemRef: PointerByReference?,
    ): Int

    fun SecKeychainAddGenericPassword(
        keychain: Pointer?,
        serviceNameLength: Int,
        serviceName: Pointer,
        accountNameLength: Int,
        accountName: Pointer,
        passwordLength: Int,
        passwordData: Pointer,
        itemRef: PointerByReference?,
    ): Int

    fun SecKeychainItemModifyAttributesAndData(
        itemRef: Pointer,
        attributes: Pointer?,
        length: Int,
        data: Pointer,
    ): Int

    fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer?): Int

    fun SecKeychainItemDelete(itemRef: Pointer): Int
}

private fun ByteArray.nativeBytes(): Memory =
    Memory(size.coerceAtLeast(1).toLong()).also { memory ->
        if (isNotEmpty()) memory.write(0, this, 0, size)
    }

private fun macKeychainFailure(operation: String, status: Int): Nothing {
    val cause = IllegalStateException("Security.framework returned OSStatus $status.")
    throw SecretStoreException(
        "macOS Keychain $operation failed with OSStatus $status.",
        cause,
    )
}

private const val SecurityFramework = "/System/Library/Frameworks/Security.framework/Security"
private const val KeysetAccount = "keyset"
private const val ErrSecSuccess = 0
private const val ErrSecItemNotFound = -25300
