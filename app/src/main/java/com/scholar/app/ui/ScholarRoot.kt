package com.scholar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun ScholarRoot(graph: AppGraph, dark: Boolean, onToggleTheme: () -> Unit) {
    val nav = rememberNavController()
    val x = Theme.x
    val current = nav.currentBackStackEntryAsState().value?.destination
    val showBar = TABS.any { tab -> current?.hierarchy?.any { it.route == tab.route } == true }

    Scaffold(
        containerColor = x.bg,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = x.bg2, tonalElevation = 0.dp) {
                TABS.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
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
                    onOpenBook = { id -> nav.navigate("reader/$id") })
            }
            composable("learn") { LearnScreen(graph, onOpen = { route -> nav.navigate(route) }) }
            composable("learn/pinyin") { PinyinScreen(graph, onBack = { nav.popBackStack() }) }
            composable("learn/radicals") { RadicalsScreen(graph, onBack = { nav.popBackStack() },
                onOpenChar = { ch -> nav.navigate("char/$ch") }) }
            composable("learn/levels") { LevelsScreen(graph, onBack = { nav.popBackStack() }) }
            composable("learn/cultivation") { CultivationScreen(graph, onBack = { nav.popBackStack() }) }
            composable("learn/writing") { WritingPickerScreen(graph, onBack = { nav.popBackStack() },
                onPractice = { ch -> nav.navigate("writing/$ch") }) }

            composable("read") { LibraryScreen(graph, onOpenBook = { id -> nav.navigate("reader/$id") }) }
            composable("review") { ReviewScreen(graph) }
            composable("dict") { DictionaryScreen(graph, onOpenChar = { ch -> nav.navigate("char/$ch") }) }
            composable("settings") { SettingsScreen(dark, onToggleTheme, onBack = { nav.popBackStack() }) }

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
                WritingScreen(graph, ch = e.arguments?.getString("ch").orEmpty(), onBack = { nav.popBackStack() })
            }
        }
    }
}
