package com.damianogiusti.stickermaker.imports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferErrorDialog
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferKind
import com.damianogiusti.stickermaker.feature.packs.transfer.TransferProgressDialog

/** Shows progress and errors of a file received from another app and opens the imported pack. */
@Composable
fun IncomingImportHost(
    onOpenPack: (packId: String) -> Unit,
    viewModel: IncomingImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importedPackId = state.importedPackId

    LaunchedEffect(importedPackId) {
        importedPackId?.let {
            onOpenPack(it)
            viewModel.onPackOpened()
        }
    }

    when (val phase = state.phase) {
        ImportPhase.Idle -> Unit
        ImportPhase.Importing -> TransferProgressDialog(TransferKind.IMPORT)
        is ImportPhase.Failed -> TransferErrorDialog(phase.error, viewModel::dismissError)
    }
}
