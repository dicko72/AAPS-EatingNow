package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.overview.graph.TbrState

@Preview(showBackground = true)
@Composable
internal fun TbrChipHighPreview() {
    MaterialTheme { TbrChip(state = TbrState.HIGH, text = "1.20 U/h", progress = 0.4f, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
internal fun TbrChipLowPreview() {
    MaterialTheme { TbrChip(state = TbrState.LOW, text = "0.45 U/h", progress = 0.8f, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
internal fun TbrChipNonePreview() {
    MaterialTheme { TbrChip(state = TbrState.NONE, text = "0.90 U/h", onClick = {}) }
}
