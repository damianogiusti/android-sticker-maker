package com.damianogiusti.stickermaker.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.error.TransferError
import com.damianogiusti.stickermaker.domain.model.PackArchive
import com.damianogiusti.stickermaker.domain.model.Sticker
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.BackupReader
import com.damianogiusti.stickermaker.domain.repository.PackArchiveStore
import com.damianogiusti.stickermaker.domain.repository.PackFileSource
import com.damianogiusti.stickermaker.domain.repository.PackImportRepository
import java.io.ByteArrayInputStream
import java.io.InputStream

/** Records what happens to a backup so tests can assert the pack-by-pack order. */
class FakeBackupReader(
    private val archives: Map<String, PackArchive>,
    private val log: MutableList<String>,
    private val failOn: String? = null,
) : BackupReader {
    override val sourceIds: List<String> = archives.keys.toList()
    var discarded = 0
        private set

    override suspend fun load(sourceId: String): Either<TransferError, PackArchive> {
        log += "load $sourceId"
        return if (sourceId == failOn) TransferError.InvalidBackup("bad $sourceId").left() else archives.getValue(sourceId).right()
    }

    override suspend fun discard() {
        discarded++
    }
}

class FakePackArchiveStore : PackArchiveStore {
    var wastickers: Either<TransferError, PackArchive> = TransferError.NotAZip.left()
    var backupToOpen: Either<TransferError, FakeBackupReader> = TransferError.NotAZip.left()
    var writeFailure: TransferError? = null
    var writtenShare: Pair<String, PackArchive>? = null
    var writtenBackup: Pair<String, List<StickerPack>>? = null
    var backupFiles: PackFileSource? = null

    override suspend fun readWastickers(location: String) = wastickers

    override suspend fun writeWastickersForSharing(fileName: String, archive: PackArchive): Either<TransferError, String> {
        writeFailure?.let { return it.left() }
        writtenShare = fileName to archive
        return "/cache/share/$fileName".right()
    }

    override suspend fun openBackup(location: String): Either<TransferError, BackupReader> = backupToOpen

    override suspend fun writeBackup(
        location: String,
        packs: List<StickerPack>,
        files: PackFileSource,
    ): Either<TransferError, Unit> {
        writeFailure?.let { return it.left() }
        writtenBackup = location to packs
        backupFiles = files
        return Unit.right()
    }
}

class FakePackFileSource(private val files: Map<String, ByteArray>) : PackFileSource {
    var reads = 0
        private set

    override suspend fun read(packId: StickerPackId, fileName: String): Either<StickerPackError, ByteArray> {
        reads++
        return files["${packId.value}/$fileName"]?.right() ?: StickerPackError.StickerNotFound(packId, fileName).left()
    }

    override suspend fun open(packId: StickerPackId, fileName: String): Either<StickerPackError, InputStream> =
        read(packId, fileName).map { ByteArrayInputStream(it) }
}

/** Inserts into the same fake the use cases read from, so rollbacks are observable. */
class FakePackImportRepository(
    private val repository: FakeStickerPackRepository,
    private val failOnCall: Int? = null,
    private val log: MutableList<String>? = null,
) : PackImportRepository {
    val inserted = mutableListOf<Pair<PackArchive, Boolean>>()
    private var calls = 0

    override suspend fun insertPack(archive: PackArchive, isImported: Boolean): Either<StickerPackError, StickerPack> {
        calls++
        log?.add("insert ${archive.name}")
        if (calls == failOnCall) return StickerPackError.Storage("disk full").left()
        inserted += archive to isImported
        val pack = aPack(
            id = (5000 + calls).toString(),
            name = archive.name,
            publisher = archive.publisher,
            stickers = archive.stickers.mapIndexed { i, s -> Sticker("${i + 1}.webp", s.emojis, s.bytes.size.toLong(), i) },
        )
        repository.packs.value = repository.packs.value + pack
        return pack.right()
    }
}
