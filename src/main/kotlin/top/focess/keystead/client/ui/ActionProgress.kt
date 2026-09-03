package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ActionProgress(busy: Boolean) {
    if (busy) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}
