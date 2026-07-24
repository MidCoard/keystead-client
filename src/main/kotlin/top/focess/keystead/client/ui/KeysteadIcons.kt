package top.focess.keystead.client.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun materialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

val KeyIcon: ImageVector by lazy {
    materialIcon(
        "Key",
        "M12.65 10C11.83 7.31 9.61 5.5 7 5.5c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.81 5.65-4.5H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z",
    )
}

val SyncIcon: ImageVector by lazy {
    materialIcon(
        "Sync",
        "M12 6v3l4-4-4-4v3c-4.42 0-8 3.58-8 8 0 1.57.46 3.03 1.24 4.26L6.7 14.8c-.45-.83-.7-1.79-.7-2.8 0-3.31 2.69-6 6-6zm6.76 1.74L17.3 9.2c.44.84.7 1.79.7 2.8 0 3.31-2.69 6-6 6v-3l-4 4 4 4v-3c4.42 0 8-3.58 8-8 0-1.57-.46-3.03-1.24-4.26z",
    )
}

val ShieldIcon: ImageVector by lazy {
    materialIcon(
        "Shield",
        "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z",
    )
}
