/*
 * Spry (2026) | the Friends screen: sign in, your username, add friends, who's online.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.friends

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold

@Composable
fun FriendsScreen(navController: NavController) {
    YumaSettingsScaffold(
        title = { Text("Friends") },
        onBackClick = { navController.navigateUp() },
    ) {
        FriendsCard(Modifier.padding(horizontal = 16.dp))
    }
}
