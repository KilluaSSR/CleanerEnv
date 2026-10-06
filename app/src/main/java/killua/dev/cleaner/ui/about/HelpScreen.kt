package killua.dev.cleaner.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import killua.dev.arsenal.material.Spacing
import killua.dev.arsenal.material.templates.scaffolds.plus
import killua.dev.arsenal.navigation.annotation.NavDestination
import killua.dev.cleaner.R
import killua.dev.cleaner.ui.components.CleanerSurfaces
import killua.dev.cleaner.ui.components.DetailScaffold

private val sections = listOf(
    R.string.help_how_title to R.string.help_how_body,
    R.string.help_requirements_title to R.string.help_requirements_body,
    R.string.help_apply_title to R.string.help_apply_body,
    R.string.help_folders_title to R.string.help_folders_body,
    R.string.help_export_title to R.string.help_export_body,
    R.string.help_uninstall_title to R.string.help_uninstall_body,
)

@NavDestination(route = "help")
@Composable
fun HelpScreen(navController: NavHostController, backStackEntry: NavBackStackEntry) {
    DetailScaffold(title = stringResource(R.string.help_title), onBack = { navController.navigateUp() }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(sections) { (title, body) ->
                Surface(shape = MaterialTheme.shapes.large, color = CleanerSurfaces.row) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
