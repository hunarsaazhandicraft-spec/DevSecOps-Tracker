package com.devsecops.tracker.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private object Routes {
    const val TODAY = "today"
    const val REVISE = "revise"
    const val MASTERY = "mastery"
    const val PROGRESS = "progress"

    const val STUDY = "study"
    const val WEAK_SPOTS = "weak_spots"
    const val ADD_WEAK_SPOT = "add_weak_spot"
    const val RECORD_MASTERY = "record_mastery"
    const val ADD_CARD = "add_card"
    const val SETTINGS = "settings"
    const val MODULE = "module/{id}"
    const val GATE = "gate/{id}"
    const val TOOLS = "tools"
    const val PLANNER = "planner"
    const val FOCUS = "focus"
    const val INSIGHTS = "insights"
    const val SEARCH = "search"
    const val GLOSSARY = "glossary"
    const val INTERVIEW = "interview"
    const val PROJECTS = "projects"
    const val COMMANDS = "commands"
    const val BANDIT = "bandit"
    const val CALIBRATION = "calibration"
    const val FADING = "fading"
    const val PATH = "path"
    const val LIBRARY = "library"
    const val ATTENDANCE = "attendance"
    const val CHAT = "chat/{no}"
    const val DOC = "doc?path={path}"
}

// Set by MainActivity when a home-screen widget is tapped ("revise" | "study" | "today").
object DeepLink {
    private val state = androidx.compose.runtime.mutableStateOf<String?>(null)
    var target: String?
        get() = state.value
        set(v) { state.value = v }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.TODAY, "Today", Icons.Filled.Today),
    Tab(Routes.PATH, "Path", Icons.Filled.Map),
    Tab(Routes.REVISE, "Revise", Icons.Filled.Quiz),
    Tab(Routes.LIBRARY, "Library", Icons.Filled.LocalLibrary),
    Tab(Routes.PROGRESS, "Progress", Icons.Filled.Insights)
)

private const val ANIM_MS = 260

// Five top-level destinations reachable at any time from the bottom bar —
// no more going back to Home to get anywhere. Detail screens and forms push
// on top and hide the bar, so they read as "a thing you're doing" rather
// than "a place you are".
@Composable
fun TrackerNavHost() {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = TABS.any { it.route == currentRoute }

    // Same behaviour as tapping the bottom bar, for buttons elsewhere that
    // jump to a tab (so they don't stack a second copy on top of it).
    fun goTab(route: String) {
        if (navController.currentDestination?.route == route) return
        // Learn (mastery) and Weak spots are no longer tabs: push them.
        if (TABS.none { it.route == route }) {
            navController.navigate(route) { launchSingleTop = true }
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val deepTarget = DeepLink.target
    LaunchedEffect(deepTarget) {
        when (deepTarget) {
            "revise" -> goTab(Routes.REVISE)
            "study" -> navController.navigate(Routes.STUDY) { launchSingleTop = true }
        }
        if (deepTarget != null) DeepLink.target = null
    }

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        // Switching tabs shouldn't pile up history.
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            // Outer Scaffold owns the system-bar insets; mark them consumed so inner
            // Scaffolds don't add them a second time. IME is handled once, here.
            modifier = Modifier
                .padding(scaffoldPadding)
                .consumeWindowInsets(scaffoldPadding)
                .imePadding()
                // Keep content readable on a wide tablet: centred column, max 720dp.
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
            enterTransition = {
                if (TABS.any { it.route == targetState.destination.route }) fadeIn(tween(150))
                else slideIn()
            },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = {
                if (TABS.any { it.route == targetState.destination.route }) fadeIn(tween(150))
                else fadeIn(tween(ANIM_MS))
            },
            popExitTransition = {
                if (TABS.any { it.route == initialState.destination.route }) fadeOut(tween(150))
                else slideOut()
            }
        ) {
            composable(Routes.TODAY) {
                HomeScreen(
                    onLogStudy = { navController.navigate(Routes.STUDY) },
                    onViewWeakSpots = { goTab(Routes.WEAK_SPOTS) },
                    onViewMastery = { goTab(Routes.MASTERY) },
                    onViewProgress = { goTab(Routes.PROGRESS) },
                    onStartRevision = { goTab(Routes.REVISE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpen = {
                        if (it == "revise" || it == "weak_spots" || it == "mastery" || it == "progress" || it == "path" || it == "library") goTab(it)
                        else navController.navigate(it)
                    }
                )
            }
            composable(Routes.REVISE) {
                QuizScreen(
                    onBack = { navController.popBackStack() },
                    onAddCard = { navController.navigate(Routes.ADD_CARD) },
                    showBackButton = false
                )
            }
            composable(Routes.MASTERY) {
                MasteryScreen(
                    onBack = { navController.popBackStack() },
                    onRecordEvidence = { navController.navigate(Routes.RECORD_MASTERY) },
                    onOpenModule = { navController.navigate("module/$it") },
                    showBackButton = true
                )
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(
                    onBack = { navController.popBackStack() },
                    showBackButton = false,
                    onOpen = { navController.navigate(it) }
                )
            }

            composable(
                Routes.MODULE,
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                ModuleDetailScreen(
                    moduleId = entry.arguments?.getInt("id") ?: 1,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PATH) {
                PathScreen(onOpenChat = { navController.navigate("chat/$it") })
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(onOpenDoc = { navController.navigate("doc?path=" + android.net.Uri.encode(it)) })
            }
            composable(
                Routes.CHAT,
                arguments = listOf(navArgument("no") { type = NavType.IntType })
            ) { entry ->
                ChatDetailScreen(
                    chatNo = entry.arguments?.getInt("no") ?: 1,
                    onBack = { navController.popBackStack() },
                    onOpenDoc = { navController.navigate("doc?path=" + android.net.Uri.encode(it)) }
                )
            }
            composable(
                Routes.DOC,
                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                DocScreen(path = entry.arguments?.getString("path") ?: "", onBack = { navController.popBackStack() })
            }
            composable(Routes.CALIBRATION) { CalibrationScreen { navController.popBackStack() } }
            composable(Routes.FADING) {
                FadingSoonScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCard = { goTab(Routes.REVISE) }
                )
            }
            composable(Routes.ATTENDANCE) { AttendanceScreen { navController.popBackStack() } }
            composable(Routes.PLANNER) { PlannerScreen { navController.popBackStack() } }
            composable(Routes.FOCUS) { FocusScreen { navController.popBackStack() } }
            composable(Routes.INSIGHTS) { InsightsScreen { navController.popBackStack() } }
            composable(Routes.SEARCH) { SearchScreen { navController.popBackStack() } }
            composable(Routes.GLOSSARY) { GlossaryScreen { navController.popBackStack() } }
            composable(Routes.INTERVIEW) { InterviewScreen { navController.popBackStack() } }
            composable(Routes.PROJECTS) { ProjectsScreen { navController.popBackStack() } }
            composable(Routes.COMMANDS) { CommandsScreen { navController.popBackStack() } }
            composable(Routes.BANDIT) { BanditScreen { navController.popBackStack() } }
            composable(Routes.STUDY) {
                FormFrame("Log study", { navController.popBackStack() }) {
                    StudyLogScreen(onSaved = { navController.popBackStack() })
                }
            }
            composable(Routes.WEAK_SPOTS) {
                WeakSpotsScreen(
                    onBack = { navController.popBackStack() },
                    onAddWeakSpot = { navController.navigate(Routes.ADD_WEAK_SPOT) },
                    showBackButton = true
                )
            }
            composable(Routes.ADD_WEAK_SPOT) {
                FormFrame("Open a weak spot", { navController.popBackStack() }) {
                    AddWeakSpotScreen(onSaved = { navController.popBackStack() })
                }
            }
            composable(Routes.RECORD_MASTERY) {
                FormFrame("Record evidence", { navController.popBackStack() }) {
                    RecordMasteryScreen(onSaved = { navController.popBackStack() })
                }
            }
            composable(Routes.ADD_CARD) {
                FormFrame("New revision card", { navController.popBackStack() }) {
                    AddCardScreen(onSaved = { navController.popBackStack() })
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun AnimatedContentTransitionScope<*>.slideIn() =
    slideInHorizontally(tween(ANIM_MS)) { 48 } + fadeIn(tween(ANIM_MS))

private fun AnimatedContentTransitionScope<*>.slideOut() =
    slideOutHorizontally(tween(ANIM_MS)) { 48 } + fadeOut(tween(ANIM_MS))

// Same header as every other screen, around the plain input forms.
@Composable
private fun FormFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { AppTitleBar(title = title, onBack = onBack) }) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}
