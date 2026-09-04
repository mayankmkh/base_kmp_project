package dev.mayankmkh.basekmpproject.platform.securestorage

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue

class FileKeysetVaultTest {
    @Test
    fun `fallback file round trips`() {
        val file = Files.createTempDirectory("file-keyset-vault").resolve("secure-storage.keyset")
        val vault = FileKeysetVault(file.toFile())

        assertEquals(null, vault.read())
        vault.write("{\"keyset\":\"secret\"}")

        assertEquals("{\"keyset\":\"secret\"}", vault.read())
    }

    @Test
    fun `fallback file is owner only on POSIX`() {
        assumeTrue(
            "The current file system has no POSIX permission view.",
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
        )
        val file =
            Files.createTempDirectory("file-keyset-permissions").resolve("secure-storage.keyset")
        FileKeysetVault(file.toFile()).write("keyset")

        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(file),
        )
    }
}
