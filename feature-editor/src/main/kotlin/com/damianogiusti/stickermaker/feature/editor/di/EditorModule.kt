package com.damianogiusti.stickermaker.feature.editor.di

import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import java.io.File
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import com.damianogiusti.stickermaker.domain.clipboard.StickerClipboard
import com.damianogiusti.stickermaker.domain.clipboard.StickerImageFormat
import com.damianogiusti.stickermaker.feature.editor.CopyStickerUseCase
import com.damianogiusti.stickermaker.feature.editor.FileStickerClipboard
import com.damianogiusti.stickermaker.feature.editor.AndroidEditorLogger
import com.damianogiusti.stickermaker.feature.editor.EditorLogger
import com.damianogiusti.stickermaker.feature.editor.EditorViewModel
import com.damianogiusti.stickermaker.feature.editor.OutlinePreferences
import com.damianogiusti.stickermaker.feature.editor.SharedOutlinePreferences
import com.damianogiusti.stickermaker.imaging.cut.BitmapImageCutter
import com.damianogiusti.stickermaker.imaging.cut.ImageCutter
import com.damianogiusti.stickermaker.imaging.export.BitmapStickerExporter
import com.damianogiusti.stickermaker.imaging.export.StickerExporter
import com.damianogiusti.stickermaker.imaging.outline.BitmapOutlineRenderer
import com.damianogiusti.stickermaker.imaging.outline.OutlineRenderer
import com.damianogiusti.stickermaker.imaging.segmentation.DeepLabSubjectSegmenter
import com.damianogiusti.stickermaker.imaging.segmentation.SubjectSegmenter
import com.damianogiusti.stickermaker.imaging.source.AndroidSourceImageLoader
import com.damianogiusti.stickermaker.imaging.source.SourceImageLoader
import com.damianogiusti.stickermaker.imaging.store.EditorImageStore
import com.damianogiusti.stickermaker.imaging.store.FileEditorImageStore
import com.damianogiusti.stickermaker.imaging.text.FontTypefaces
import com.damianogiusti.stickermaker.imaging.text.TextItemPainter

/**
 * Editor ViewModel, use case and imaging bindings.
 * Imaging work runs on `Dispatchers.Default`, passed explicitly.
 */
/** The one place to change if an app pastes WebP badly: PNG is the fallback. */
private val CLIPBOARD_FORMAT = StickerImageFormat.WEBP

val editorModule = module {
    single<SourceImageLoader> { AndroidSourceImageLoader(androidContext(), Dispatchers.Default) }
    single<ImageCutter> { BitmapImageCutter(Dispatchers.Default) }
    single<OutlineRenderer> { BitmapOutlineRenderer(Dispatchers.Default) }
    single<SubjectSegmenter> { DeepLabSubjectSegmenter(androidContext(), Dispatchers.Default) }
    single<EditorImageStore> { FileEditorImageStore(File(androidContext().cacheDir, "editor"), Dispatchers.IO) }
    single<EditorLogger> { AndroidEditorLogger() }
    single { FontTypefaces(androidContext().assets) }
    single { TextItemPainter(get()) }
    single<StickerExporter> { BitmapStickerExporter(get(), Dispatchers.Default) }
    single<OutlinePreferences> { SharedOutlinePreferences(androidContext()) }
    single<StickerClipboard> {
        FileStickerClipboard(
            context = androidContext(),
            directory = File(androidContext().cacheDir, "clipboard"),
            dispatcher = Dispatchers.IO,
            nowMillis = System::currentTimeMillis,
        )
    }
    factory { CopyStickerUseCase(exporter = get(), clipboard = get(), format = CLIPBOARD_FORMAT) }
    viewModel {
        EditorViewModel(
            sourceLoader = get(),
            cutter = get(),
            segmenter = get(),
            outlineRenderer = get(),
            preferences = get(),
            copySticker = get(),
            imageStore = get(),
            savedState = get(),
            logger = get(),
        )
    }
}
