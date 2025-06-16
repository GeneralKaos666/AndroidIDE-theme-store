package moe.smoothie.androidide.themestore.util

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

@Throws(IOException::class)
fun unzip(zipFile: File, targetDirectory: File) {
    if (!targetDirectory.exists()) {
        targetDirectory.mkdirs()
    }

    ZipInputStream(zipFile.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val newFile = File(targetDirectory, entry.name)
            // Security check for "Zip Slip" vulnerability
            if (!newFile.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
                throw IOException("Zip Slip vulnerability detected: Entry is outside of the target dir: ${entry.name}")
            }

            if (entry.isDirectory) {
                newFile.mkdirs()
            } else {
                // Ensure parent directory exists for files within subdirectories
                File(newFile.parent!!).mkdirs()
                FileOutputStream(newFile).use { fos ->
                    zis.copyTo(fos)
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}
