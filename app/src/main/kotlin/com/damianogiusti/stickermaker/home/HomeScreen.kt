package com.damianogiusti.stickermaker.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.damianogiusti.stickermaker.R

/** Start screen. The Photo Picker result is handed up as a URI string; the menu state is UI-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPhotoChosen: (uri: String) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPhotoChosen(it.toString()) }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                actions = { HomeOverflowMenu(onAbout) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) {
                Text(stringResource(R.string.home_choose_photo))
            }
        }
    }
}

@Composable
private fun HomeOverflowMenu(onAbout: () -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { isOpen = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.home_more))
    }
    DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.about_title)) },
            onClick = {
                isOpen = false
                onAbout()
            },
        )
    }
}
