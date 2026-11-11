package com.damianogiusti.stickermaker.provider

import com.damianogiusti.stickermaker.domain.model.StickerPack

/** A cursor as plain data: the provider copies [rows] into a `MatrixCursor`. */
data class CursorData(val columns: List<String>, val rows: List<List<Any?>>)

/** Domain packs to the rows of the WhatsApp sticker contract. No Android types, so JVM-testable. */
object StickerCursorMapper {
    val METADATA_COLUMNS = listOf(
        "sticker_pack_identifier",
        "sticker_pack_name",
        "sticker_pack_publisher",
        "sticker_pack_icon",
        "android_play_store_link",
        "ios_app_download_link",
        "sticker_pack_publisher_email",
        "sticker_pack_publisher_website",
        "sticker_pack_privacy_policy_website",
        "sticker_pack_license_agreement_website",
        "image_data_version",
        "whatsapp_will_not_cache_stickers",
        "animated_sticker_pack",
    )

    val STICKER_COLUMNS = listOf("sticker_file_name", "sticker_emoji", "is_animated_sticker")

    fun metadata(packs: List<StickerPack>): CursorData = CursorData(METADATA_COLUMNS, packs.map(::metadataRow))

    /** Store links and web fields stay empty: this app has no store listing or website. */
    private fun metadataRow(pack: StickerPack): List<Any?> = listOf(
        pack.id.value,
        pack.name,
        pack.publisher,
        pack.trayFileName.orEmpty(),
        "",
        "",
        "",
        "",
        "",
        "",
        pack.imageDataVersion,
        0,
        0,
    )

    /** One row per sticker, in position order. No placeholder rows: the validator blocks small packs. */
    fun stickers(pack: StickerPack): CursorData = CursorData(
        STICKER_COLUMNS,
        pack.stickers.sortedBy { it.position }.map { listOf(it.fileName, it.emojis.joinToString(","), 0) },
    )
}
