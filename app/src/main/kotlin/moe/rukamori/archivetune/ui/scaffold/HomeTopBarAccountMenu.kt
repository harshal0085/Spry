/*
 * YumaPlayer (2026) | Original work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.ui.scaffold.accountmenu.TopBarAccountAnchor
import moe.rukamori.archivetune.ui.scaffold.accountmenu.TopBarAccountDropdownMenu
import moe.rukamori.archivetune.ui.scaffold.accountmenu.TopBarAccountMenuContent
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
fun HomeTopBarAccountMenu(
    avatarUrl: String?,
    accountName: String,
    hasUnreadNews: Boolean,
    hasUpdate: Boolean,
    onNavigateProfile: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateNews: () -> Unit,
    onNavigateNewRelease: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    hazeState: HazeState? = null,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    blurNavBar: Boolean = true,
    pureBlack: Boolean = false,
    onNavigateFriends: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val friendRequests by moe.rukamori.archivetune.friends.SpryFriends.requests.collectAsState()

    Box(modifier = modifier) {
        TopBarAccountAnchor(
            avatarUrl = avatarUrl,
            hasBadge = hasUnreadNews || hasUpdate || friendRequests.isNotEmpty(),
            onClick = { expanded = !expanded },
        )

        TopBarAccountDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            hazeState = hazeState,
            blurRadius = blurRadius,
            blurNavBar = blurNavBar,
            pureBlack = pureBlack,
        ) {
            // Spry: one login. The header is the Spry (Google) account; YouTube login stays optional in Settings > Account.
            val spryUser by moe.rukamori.archivetune.friends.SpryFriends.user.collectAsState()
            val spryName by moe.rukamori.archivetune.friends.SpryFriends.username.collectAsState()
            TopBarAccountMenuContent(
                avatarUrl = spryUser?.photoUrl?.toString() ?: avatarUrl,
                accountName = spryName?.takeIf { it.isNotEmpty() }?.let { "@$it" } ?: spryUser?.displayName.orEmpty(),
                accountSubtitle = spryUser?.email,
                accentColor = accentColor,
                hasUnreadNews = hasUnreadNews,
                hasUpdate = hasUpdate,
                onNavigateProfile = {
                    expanded = false
                    onNavigateFriends()
                },
                onNavigateHistory = {
                    expanded = false
                    onNavigateHistory()
                },
                onNavigateNews = {
                    expanded = false
                    onNavigateNews()
                },
                onNavigateNewRelease = {
                    expanded = false
                    onNavigateNewRelease()
                },
                onNavigateSettings = {
                    expanded = false
                    onNavigateSettings()
                },
                onNavigateFriends = {
                    expanded = false
                    onNavigateFriends()
                },
            )
        }
    }
}
