/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.designsystem.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.ic_home_outline,
        iconIdActive = R.drawable.ic_home,
        route = "home",
    )

    // Spry: friends get their own tab, between Home and Search.
    object Friends : Screens(
        titleId = R.string.spry_friends,
        iconIdInactive = R.drawable.ic_friends_outline,
        iconIdActive = R.drawable.ic_friends,
        route = "friends",
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.ic_search,
        iconIdActive = R.drawable.ic_search,
        route = "search",
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.ic_library_outline,
        iconIdActive = R.drawable.ic_library,
        route = "library",
    )

    // Spry: the developer's own tab, after Library; only admins get it (AdminScreens).
    object Dev : Screens(
        titleId = R.string.spry_dev,
        iconIdInactive = R.drawable.ic_dev_outline,
        iconIdActive = R.drawable.ic_dev,
        route = "dev",
    )

    object MoodAndGenres : Screens(
        titleId = R.string.mood_and_genres,
        iconIdInactive = R.drawable.style,
        iconIdActive = R.drawable.style,
        route = "mood_and_genres",
    )

    companion object {
        val MainScreens = listOf(Home, Friends, Search, Library)
        val AdminScreens = MainScreens + Dev
        val TvMainScreens = listOf(Home, Search, Library)
    }
}
