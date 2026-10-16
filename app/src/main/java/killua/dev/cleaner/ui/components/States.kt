package killua.dev.cleaner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import killua.dev.arsenal.material.templates.basics.EmptyView
import killua.dev.cleaner.R

@Composable
fun ServerRequiredView(modifier: Modifier = Modifier) {
    EmptyView(
        icon = Icons.Rounded.LinkOff,
        title = stringResource(R.string.server_required_title),
        message = stringResource(R.string.server_required_message),
        modifier = modifier,
    )
}
