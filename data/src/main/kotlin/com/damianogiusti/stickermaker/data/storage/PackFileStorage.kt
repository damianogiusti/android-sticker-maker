package com.damianogiusti.stickermaker.data.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Files of every pack live in `<root>/<packId>/`. Writes go to a temp file and are renamed,
 * so a reader never sees a half-written sticker.
 */
class PackFileStorage(private val root: File) {

    fun fileOf(packId: String, fileName: String): File = File(packDir(packId), requireSafeName(fileName))

    fun exists(packId: String, fileName: String): Boolean = fileOf(packId, fileName).isFile

    fun write(packId: String, fileName: String, bytes: ByteArray): File {
        val directory = packDir(packId)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
        val target = File(directory, requireSafeName(fileName))
        val temp = File(directory, "${target.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Cannot move $temp to $target")
        }
        return target
    }

    fun delete(packId: String, fileName: String) {
        val file = fileOf(packId, fileName)
        if (file.exists() && !file.delete()) throw IOException("Cannot delete $file")
    }

    fun deletePackDirectory(packId: String) {
        val directory = packDir(packId)
        if (directory.exists() && !directory.deleteRecursively()) throw IOException("Cannot delete $directory")
    }

    private fun packDir(packId: String): File = File(root, requireSafeName(packId))

    private fun requireSafeName(name: String): String {
        require(name.isNotEmpty() && name != "." && !name.contains("..") && !name.contains('/') && !name.contains('\\')) {
            "Unsafe file name: $name"
        }
        return name
    }
}
