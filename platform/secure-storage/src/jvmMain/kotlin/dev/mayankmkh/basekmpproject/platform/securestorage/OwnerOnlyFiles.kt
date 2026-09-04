package dev.mayankmkh.basekmpproject.platform.securestorage

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

internal fun createOwnerOnly(directory: File) {
    val path = Files.createDirectories(directory.toPath())
    setPosixPermissions(path, DIRECTORY_PERMISSIONS)
}

internal fun readOwnerOnlyFile(file: File): ByteArray? =
    if (Files.exists(file.toPath())) Files.readAllBytes(file.toPath()) else null

/**
 * Writes through an owner-only sibling and renames it over [file], so readers never see a torn
 * keyset.
 */
internal fun writeOwnerOnlyFile(file: File, bytes: ByteArray) {
    createOwnerOnly(requireNotNull(file.parentFile) { "Keyset file has no parent directory." })
    val path = file.toPath()
    val temporary = path.resolveSibling("${file.name}.tmp")
    Files.deleteIfExists(temporary)
    createOwnerOnlyFile(temporary)
    try {
        Files.write(
            temporary,
            bytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        Files.move(
            temporary,
            path,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } finally {
        Files.deleteIfExists(temporary)
    }
    setPosixPermissions(path, FILE_PERMISSIONS)
}

private fun createOwnerOnlyFile(path: Path) {
    try {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))
    } catch (_: FileAlreadyExistsException) {
        setPosixPermissions(path, FILE_PERMISSIONS)
    } catch (_: UnsupportedOperationException) {
        try {
            Files.createFile(path)
        } catch (_: FileAlreadyExistsException) {
            // The account ACL of the application data directory protects this file on Windows.
        }
    }
}

private fun setPosixPermissions(path: Path, permissions: Set<PosixFilePermission>) {
    try {
        Files.setPosixFilePermissions(path, permissions)
    } catch (_: UnsupportedOperationException) {
        // Windows has no POSIX permission view. Its application directory keeps the account ACL.
    }
}

private val DIRECTORY_PERMISSIONS =
    setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
    )

private val FILE_PERMISSIONS =
    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
