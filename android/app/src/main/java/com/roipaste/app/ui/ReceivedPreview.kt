package com.roipaste.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roipaste.app.R
import com.roipaste.app.domain.ClipLimits

@Composable
fun ReceivedPreview(
    text: String?,
    visible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.preview_heading),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !text.isNullOrEmpty(), onClick = onToggle),
        ) {
            Text(
                text = previewLabel(text, visible),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun previewLabel(text: String?, visible: Boolean): String {
    if (text.isNullOrEmpty()) {
        return stringResource(R.string.preview_empty)
    }
    if (!visible) {
        return stringResource(R.string.preview_hidden)
    }
    return if (text.length <= ClipLimits.PREVIEW_CHARS) text else text.take(ClipLimits.PREVIEW_CHARS) + "…"
}
