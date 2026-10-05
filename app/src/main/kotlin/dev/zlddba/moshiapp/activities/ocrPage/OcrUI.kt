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
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.activities.common.PageTopBar
import dev.zlddba.moshiapp.activities.textPage.TitleField
import dev.zlddba.moshiapp.ui.theme.MoshiShapeMedium
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

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
        OcrImagePreview(uiState.imageUri, uiState.isRecognizing) { zoomImage = true }
        Spacer(modifier = Modifier.height(12.dp))
        TitleField(
            title = uiState.title,
            generating = uiState.isGeneratingTitle,
            onTitleChange = { onEvent(OcrViewModel.OcrEvent.TitleChanged(it)) },
            onRegenerate = { onEvent(OcrViewModel.OcrEvent.RegenerateTitle) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.tags,
            onValueChange = { onEvent(OcrViewModel.OcrEvent.TagsChanged(it)) },
            singleLine = true,
            label = { Text(stringResource(R.string.tag_label)) },
            placeholder = { Text(stringResource(R.string.tag_input_hint)) },
            shape = MoshiShapeMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ocr_text_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        when {
            uiState.isRecognizing -> RecognisingBox()
            !uiState.recognized -> WaitingBox()
            else -> OutlinedTextField(
                value = uiState.text,
                onValueChange = { onEvent(OcrViewModel.OcrEvent.TextChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ocr_text_hint)) },
                minLines = 6,
                maxLines = 12,
                shape = MoshiShapeMedium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.sourceNote,
            onValueChange = { onEvent(OcrViewModel.OcrEvent.SourceNoteChanged(it)) },
            label = { Text(stringResource(R.string.ocr_source_note)) },
            placeholder = { Text(stringResource(R.string.ocr_source_note_hint)) },
            singleLine = true,
            shape = MoshiShapeMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { onEvent(OcrViewModel.OcrEvent.ConfirmClicked) },
            enabled = uiState.text.isNotBlank() && !uiState.isRecognizing,
            modifier = Modifier.fillMaxWidth(),
            shape = MoshiShapeMedium
        ) {
            Text(stringResource(R.string.ocr_confirm))
        }
        Spacer(modifier = Modifier.height(8.dp))
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
                enabled = !uiState.isRecognizing && uiState.imageUri != null,
                modifier = Modifier.weight(1f),
                shape = MoshiShapeMedium,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.ocr_retake))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (zoomImage && uiState.imageUri != null) {
        OcrImageZoomDialog(imageUri = uiState.imageUri, onDismiss = { zoomImage = false })
    }
}

@Composable
private fun RecognisingBox() {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.ocr_recognising),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WaitingBox() {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.ocr_waiting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun OcrImagePreview(imageUri: String?, recognizing: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MoshiShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                if (recognizing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun OcrImageZoomDialog(imageUri: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 新版重载把 centroid 放在首位；本次仍按平移增量处理，缩回原始比例时归零。
    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        scale = next
        offset = if (next <= MIN_ZOOM) Offset.Zero else offset + panChange
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

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

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
