package top.focess.keystead.client

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinCrypt
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.Base64
import top.focess.keystead.memory.Wipe

internal interface WindowsDpapiPort {
    fun protect(plaintext: ByteArray, entropy: ByteArray): ByteArray
    fun unprotect(ciphertext: ByteArray, entropy: ByteArray): ByteArray
}

internal class JnaWindowsDpapiPort : WindowsDpapiPort {
    override fun protect(plaintext: ByteArray, entropy: ByteArray): ByteArray =
        Crypt32Util.cryptProtectData(plaintext, entropy, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "Keystead", null)
    override fun unprotect(ciphertext: ByteArray, entropy: ByteArray): ByteArray =
        Crypt32Util.cryptUnprotectData(ciphertext, entropy, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null)
}

class WindowsDpapiSecretStore internal constructor(
    private val directory: Path,
    private val port: WindowsDpapiPort,
) : OsSecretStore {
    constructor(directory: Path) : this(directory, JnaWindowsDpapiPort())
    override val providerId = "windows-dpapi"

    override fun availability(): OsSecretStoreAvailability =
        if (isWindows()) OsSecretStoreAvailability(OsSecretStoreStatus.AVAILABLE, "windows-dpapi-available")
        else OsSecretStoreAvailability(OsSecretStoreStatus.UNSUPPORTED, "windows-dpapi-unsupported")

    override fun save(instanceId: String, secret: ByteArray) {
        requireWindows()
        val plaintext = secret.copyOf()
        val entropy = entropy(instanceId)
        val protected = try {
            mapErrors("windows-dpapi-protect") { port.protect(plaintext, entropy) }
        } finally {
            Wipe.wipe(plaintext)
            Wipe.wipe(entropy)
        }
        try {
            Files.createDirectories(directory)
            val target = blob(instanceId)
            val temporary = target.resolveSibling(".${target.fileName}.${java.util.UUID.randomUUID()}.tmp")
            try {
                FileChannel.open(temporary, CREATE_NEW, WRITE).use { channel -> channel.write(java.nio.ByteBuffer.wrap(protected)); channel.force(true) }
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        } catch (error: java.io.IOException) {
            throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "windows-dpapi-write", error)
        } finally { Wipe.wipe(protected) }
    }

    override fun load(instanceId: String): ByteArray? {
        requireWindows()
        val target = blob(instanceId)
        if (!Files.exists(target)) return null
        val protected = try { Files.readAllBytes(target) } catch (error: java.io.IOException) { throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "windows-dpapi-read", error) }
        val entropy = entropy(instanceId)
        return try { mapErrors("windows-dpapi-unprotect") { port.unprotect(protected, entropy) } } finally { Wipe.wipe(protected); Wipe.wipe(entropy) }
    }

    override fun delete(instanceId: String) {
        requireWindows()
        try { Files.deleteIfExists(blob(instanceId)) } catch (error: java.io.IOException) { throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "windows-dpapi-delete", error) }
    }

    private fun blob(instanceId: String): Path = directory.resolve(Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(instanceId.toByteArray())) + ".dpapi")
    private fun entropy(instanceId: String): ByteArray = MessageDigest.getInstance("SHA-256").digest("top.focess.keystead.desktop|$instanceId".toByteArray())
    private fun requireWindows() { if (!isWindows()) throw OsSecretStoreException(OsSecretStoreFailure.UNSUPPORTED, "windows-dpapi-unsupported") }
    private fun isWindows() = System.getProperty("os.name").lowercase().contains("windows")
    private fun <T> mapErrors(code: String, action: () -> T): T = try { action() } catch (error: Win32Exception) { throw OsSecretStoreException(if (error.errorCode == 5) OsSecretStoreFailure.ACCESS_DENIED else OsSecretStoreFailure.CORRUPT, code, error) } catch (error: RuntimeException) { throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, code, error) }
}
