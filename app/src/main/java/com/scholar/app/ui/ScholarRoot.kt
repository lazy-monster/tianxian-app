package com.scholar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.screen.*
import com.scholar.app.ui.theme.Theme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("learn", "Learn", Icons.Outlined.School),
    Tab("read", "Read", Icons.Outlined.MenuBook),
    Tab("review", "Review", Icons.Outlined.Style),
    Tab("dict", "Dict", Icons.Outlined.Search),
)

@Composable
fun ScholarRoot(graph: AppGraph, dark: Boolean, onToggleTheme: () -> Unit, startRoute: String? = null) {
    val nav = rememberNavController()
    val x = Theme.x
    // Deep-link from a notification or widget tap: jump to the requested route once on launch.
    LaunchedEffect(startRoute) { if (!startRoute.isNullOrBlank()) runCatching { nav.navigate(startRoute) } }
    val current = nav.currentBackStackEntryAsState().value?.destination
    val route = current?.route
    // Keep the tab bar (so Home is always one tap away) on the top-level tabs and on the
    // Learn sub-screens; hide it only on the immersive full-screen flows.
    val onTab = TABS.any { tab -> current?.hierarchy?.any { it.route == tab.route } == true }
    val showBar = onTab || route?.startsWith("learn/") == true

    Scaffold(
        containerColor = x.bg,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = x.bg2, tonalElevation = 0.dp) {
                TABS.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.route } == true ||
                        (tab.route == "learn" && route?.startsWith("learn/") == true)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            // Pop back to Home (the start destination) so every tab — Home
                            // included — is reliably reachable. The old saveState/restoreState
                            // pair is what made the Home tab silently do nothing from Learn.
                            // Re-tapping a Learn sub-screen returns to the Learn root.
                            if (route != tab.route) nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = x.cinnabar, selectedTextColor = x.cinnabar,
                            unselectedIconColor = x.textFaint, unselectedTextColor = x.textFaint,
                            indicatorColor = x.surface),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad).background(x.bg)) {
            composable("home") {
                TodayScreen(graph,
                    onOpenReview = { nav.navigate("review") },
                    onOpenLibrary = { nav.navigate("read") },
                    onOpenLearn = { nav.navigate("learn") },
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenBook = { id -> nav.navigate("reader/$id") },
                    onOpenChar = { ch -> nav.navigate("char/$ch") })
            }
            composable("learn") { LearnScreen(graph, onOpen = { route -> nav.navigate(route) }) }
            composable("learn/pinyin") { PinyinScreen(graph, onBack = { nav.popBackStack() }) }
            composable("learn/radicals") { RadicalsScreen(graph, onBack = { nav.popBackStack() },
                onOpenChar = { ch -> nav.navigate("char/$ch") }) }
            composable("learn/levels") { LevelsScreen(graph, onBack = { nav.popBackStack() },
                onOpenChar = { ch -> nav.navigate("char/$ch") }) }
            composable("learn/cultivation") { CultivationScreen(graph, onBack = { nav.popBackStack() },
                onOpenChar = { ch -> nav.navigate("char/$ch") }) }
            composable("learn/writing") { WritingPickerScreen(graph, onBack = { nav.popBackStack() },
                onPractice = { ch -> nav.navigate("writing/$ch") }) }

            composable("read") { LibraryScreen(graph, onOpenBook = { id -> nav.navigate("reader/$id") }) }
            composable("review") { ReviewScreen(graph, onOpenChar = { ch -> nav.navigate("char/$ch") }) }
            composable("dict") { DictionaryScreen(graph, onOpenChar = { ch -> nav.navigate("char/$ch") }) }
            composable("settings") { SettingsScreen(graph, dark, onToggleTheme, onBack = { nav.popBackStack() }) }

            composable("reader/{id}") { e ->
                ReaderScreen(graph, bookId = e.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() }, onOpenChar = { ch -> nav.navigate("char/$ch") })
            }
            composable("char/{ch}") { e ->
                CharacterDetailScreen(graph, ch = e.arguments?.getString("ch").orEmpty(),
                    onBack = { nav.popBackStack() }, onPractice = { ch -> nav.navigate("writing/$ch") },
                    onOpenChar = { ch -> nav.navigate("char/$ch") })
            }
            composable("writing/{ch}") { e ->
                WritingScreen(graph, ch = e.arguments?.getString("ch").orEmpty(),
                    onBack = { nav.popBackStack() }, onOpenChar = { ch -> nav.navigate("char/$ch") })
            }
        }
    }
}
