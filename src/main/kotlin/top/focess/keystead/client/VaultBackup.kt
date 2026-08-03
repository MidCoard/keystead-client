package top.focess.keystead.client

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

/** Client boundary for password-protected, independently restorable `.ksbackup` archives. */
internal object VaultBackup {
    fun export(
        session: LocalVaultSession,
        backupPassword: CharArray,
        output: OutputStream,
    ) {
        session.exportFullBackup(backupPassword, output)
    }

    fun restore(
        target: Path,
        input: InputStream,
        backupPassword: CharArray,
        newMasterPassphrase: CharArray,
    ): LocalVaultSession {
        if (Files.exists(target)) {
            throw FileAlreadyExistsException(target.toString())
        }
        return LocalVaultSession.restoreFullBackup(
            target,
            input,
            backupPassword,
            newMasterPassphrase,
        )
    }
}
