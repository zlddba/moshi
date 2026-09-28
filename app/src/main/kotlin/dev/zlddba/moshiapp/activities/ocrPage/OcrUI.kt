package dev.zlddba.moshiapp.activities.ocrPage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import androidx.core.net.toUri

@Composable
fun OcrPageScreen(
    uiState: OcrViewModel.OcrUiState,
    onBack: () -> Unit,
    onEvent: (OcrViewModel.OcrEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomImage by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        PageTopBar(
            titleRes = R.string.ocr_title,
            backDescRes = R.string.ocr_title,
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(8.dp))
        OcrImagePreview(uiState.imageUri) { zoomImage = true }
        if (uiState.isRecognizing) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ocr_text_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.text,
            onValueChange = { onEvent(OcrViewModel.OcrEvent.TextChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.ocr_text_hint)) },
            minLines = 6,
            maxLines = 10,
            shape = MoshiShapeMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ocr_source_note),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.sourceNote,
            onValueChange = { onEvent(OcrViewModel.OcrEvent.SourceNoteChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.ocr_source_note_hint)) },
            singleLine = true,
            shape = MoshiShapeMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onEvent(OcrViewModel.OcrEvent.RecaptureClicked) },
                modifier = Modifier.weight(1f),
                shape = MoshiShapeMedium,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.ocr_recapture))
            }
            OutlinedButton(
                onClick = { onEvent(OcrViewModel.OcrEvent.RecognizeAgain) },
                enabled = !uiState.isRecognizing,
                modifier = Modifier.weight(1f),
                shape = MoshiShapeMedium,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.ocr_retake))
            }
            Button(
                onClick = { onEvent(OcrViewModel.OcrEvent.ConfirmClicked) },
                modifier = Modifier.weight(1f),
                shape = MoshiShapeMedium,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.ocr_confirm))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (zoomImage && uiState.imageUri != null) {
        OcrImageZoomDialog(imageUri = uiState.imageUri, onDismiss = { zoomImage = false })
    }
}

@Composable
private fun OcrImagePreview(imageUri: String?, onClick: () -> Unit) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable(enabled = imageUri != null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri.isNullOrBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ocr_preview_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                AsyncImage(
                    model = imageUri.toUri(),
                    contentDescription = stringResource(R.string.ocr_preview_label),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun OcrImageZoomDialog(imageUri: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + panChange
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .transformable(state = transformableState)
                .pointerInput(imageUri) {
                    detectTapGestures(onTap = { onDismiss() })
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUri.toUri(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
private fun OcrPageScreenPreview() {
    MoshiTheme {
        OcrPageScreen(
            uiState = OcrViewModel.OcrUiState(),
            onBack = {},
            onEvent = {}
        )
    }
}
