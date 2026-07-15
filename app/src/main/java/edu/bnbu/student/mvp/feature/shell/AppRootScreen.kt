package edu.bnbu.student.mvp.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import edu.bnbu.student.mvp.R
import edu.bnbu.student.mvp.core.designsystem.BNBUMotion
import edu.bnbu.student.mvp.core.state.StudentAppState
import edu.bnbu.student.mvp.feature.checkin.CheckInScreen
import edu.bnbu.student.mvp.feature.courses.CoursesScreen
import edu.bnbu.student.mvp.feature.dashboard.DashboardScreen
import edu.bnbu.student.mvp.feature.grades.GradesScreen
import edu.bnbu.student.mvp.feature.login.LoginScreen
import edu.bnbu.student.mvp.feature.notifications.NotificationSheet
import edu.bnbu.student.mvp.feature.profile.PrivacyPolicyScreen
import edu.bnbu.student.mvp.feature.profile.ProfileScreen
import edu.bnbu.student.mvp.feature.scoring.EnduranceScoringScreen
import edu.bnbu.student.mvp.feature.exemption.ExemptionScreen

enum class AppTab(
    val label: String,
    val icon: ImageVector?
) {
    Dashboard("首页", null),
    Courses("课程", Icons.AutoMirrored.Filled.MenuBook),
    CheckIn("打卡", Icons.Filled.AddBox),
    Grades("成绩", Icons.Filled.BarChart),
    Profile("我的", Icons.Filled.AccountCircle)
}

enum class SubScreen {
    None,
    EnduranceScoring,
    Exemption,
    PrivacyPolicy
}

private enum class AuthUiState {
    Restoring,
    Authenticated,
    Login
}

@Composable
fun AppRootScreen(
    appState: StudentAppState,
    isRestoringSession: Boolean = false
) {
    var showLoginPrivacy by rememberSaveable { mutableStateOf(false) }
    val authUiState = when {
        isRestoringSession -> AuthUiState.Restoring
        appState.isAuthenticated -> AuthUiState.Authenticated
        else -> AuthUiState.Login
    }
    AnimatedContent(
        targetState = authUiState,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = if (targetState == AuthUiState.Authenticated) 1 else -1
            (fadeIn(tween(BNBUMotion.Standard, delayMillis = 40)) +
                slideInHorizontally(
                    animationSpec = tween(BNBUMotion.Emphasized, easing = FastOutSlowInEasing),
                    initialOffsetX = { direction * (it / 10) }
                )) togetherWith
                (fadeOut(tween(BNBUMotion.Quick)) +
                    slideOutHorizontally(
                        animationSpec = tween(BNBUMotion.Standard, easing = FastOutSlowInEasing),
                        targetOffsetX = { -direction * (it / 12) }
                    ))
        },
        label = "authentication-transition"
    ) { state ->
        when (state) {
            AuthUiState.Restoring -> SessionRestoreScreen()
            AuthUiState.Authenticated -> AuthenticatedAppContent(appState)
            AuthUiState.Login -> {
                if (showLoginPrivacy) {
                    PreLoginPrivacyScreen(onBack = { showLoginPrivacy = false })
                } else {
                    LoginScreen(
                        onLogin = { account, password -> appState.login(account, password) },
                        onOpenPrivacy = { showLoginPrivacy = true },
                        isLoading = appState.isLoading,
                        errorMessage = appState.lastError
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRestoreScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )
            Text(
                text = "正在恢复登录状态…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PreLoginPrivacyScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        PrivacyPolicyScreen(onBack = onBack)
    }
}

@Composable
private fun AuthenticatedAppContent(appState: StudentAppState) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Dashboard) }
    var subScreen by rememberSaveable { mutableStateOf(SubScreen.None) }
    var renderedSubScreen by rememberSaveable { mutableStateOf(subScreen) }
    var exemptionTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNotificationSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(subScreen) {
        if (subScreen != SubScreen.None) renderedSubScreen = subScreen
    }

    BackHandler(enabled = subScreen != SubScreen.None) {
        exemptionTargetId = null
        subScreen = SubScreen.None
    }
    BackHandler(
        enabled = subScreen == SubScreen.None &&
            selectedTab != AppTab.Dashboard &&
            !showNotificationSheet
    ) {
        selectedTab = AppTab.Dashboard
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                StudentBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AnimatedVisibility(
                        visible = appState.lastError != null || appState.isShowingCachedData,
                        enter = expandVertically(tween(BNBUMotion.Standard)) +
                            fadeIn(tween(BNBUMotion.Standard)),
                        exit = shrinkVertically(tween(BNBUMotion.Standard)) +
                            fadeOut(tween(BNBUMotion.Quick))
                    ) {
                        SyncStatusBanner(appState)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        RootTabContent(
                            tab = selectedTab,
                            appState = appState,
                            contentPadding = PaddingValues(0.dp),
                            onOpenNotificationSheet = { showNotificationSheet = true },
                            openExemption = { targetId ->
                                exemptionTargetId = targetId
                                renderedSubScreen = SubScreen.Exemption
                                subScreen = SubScreen.Exemption
                            },
                            openEnduranceScoring = {
                                renderedSubScreen = SubScreen.EnduranceScoring
                                subScreen = SubScreen.EnduranceScoring
                            },
                            openPrivacy = {
                                renderedSubScreen = SubScreen.PrivacyPolicy
                                subScreen = SubScreen.PrivacyPolicy
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = subScreen != SubScreen.None,
            enter = fadeIn(tween(BNBUMotion.Standard)) +
                slideInHorizontally(
                    animationSpec = tween(BNBUMotion.Emphasized, easing = FastOutSlowInEasing),
                    initialOffsetX = { it / 8 }
                ),
            exit = fadeOut(tween(BNBUMotion.Quick)) +
                slideOutHorizontally(
                    animationSpec = tween(BNBUMotion.Standard, easing = FastOutSlowInEasing),
                    targetOffsetX = { it / 10 }
                )
        ) {
            SubScreenOverlay(
                subScreen = renderedSubScreen,
                appState = appState,
                exemptionTargetId = exemptionTargetId,
                onClose = {
                    exemptionTargetId = null
                    subScreen = SubScreen.None
                }
            )
        }
    }

    if (showNotificationSheet) {
        NotificationSheet(
            notices = appState.visibleNotices,
            unreadCount = appState.unreadNoticeCount,
            onDismiss = { showNotificationSheet = false },
            onMarkRead = appState::markNoticeRead,
            onMarkAllRead = appState::markAllNoticesRead,
            onOpenExemption = { targetId ->
                showNotificationSheet = false
                exemptionTargetId = targetId
                renderedSubScreen = SubScreen.Exemption
                subScreen = SubScreen.Exemption
            }
        )
    }
}

@Composable
private fun SubScreenOverlay(
    subScreen: SubScreen,
    appState: StudentAppState,
    exemptionTargetId: String?,
    onClose: () -> Unit
) {
    val repo = appState.apiRepository
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(18.dp)
    ) {
        when (subScreen) {
            SubScreen.EnduranceScoring -> if (repo != null) {
                EnduranceScoringScreen(
                    appState = appState,
                    student = appState.workspace.student,
                    repository = repo,
                    onUnauthorized = appState::handleUnauthorized,
                    onBack = onClose
                )
            }
            SubScreen.Exemption -> if (repo != null) {
                ExemptionScreen(
                    appState = appState,
                    repository = repo,
                    initialApplicationId = exemptionTargetId,
                    onUnauthorized = appState::handleUnauthorized,
                    onBack = onClose
                )
            }
            SubScreen.PrivacyPolicy -> PrivacyPolicyScreen(onBack = onClose)
            SubScreen.None -> Unit
        }
    }
}

@Composable
private fun SyncStatusBanner(appState: StudentAppState) {
    val cs = MaterialTheme.colorScheme
    val message = appState.lastError
        ?: "当前显示缓存数据，内容可能不是最新"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = cs.onErrorContainer,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = message,
            color = cs.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        if (appState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = cs.onErrorContainer,
                strokeWidth = 2.dp
            )
        } else {
            IconButton(
                onClick = appState::retryLoadWorkspace,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "重新同步",
                    tint = cs.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun StudentBottomBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = cs.surface,
        contentColor = cs.onSurface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.08f else 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                ),
                label = "bottomBarIconScale"
            )
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    if (tab.icon == null) {
                        Icon(
                            painter = painterResource(R.drawable.bnbu_emblem),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    } else {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = cs.onSurface,
                    selectedTextColor = cs.onSurface,
                    indicatorColor = cs.primaryContainer,
                    unselectedIconColor = cs.onSurfaceVariant,
                    unselectedTextColor = cs.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun RootTabContent(
    tab: AppTab,
    appState: StudentAppState,
    contentPadding: PaddingValues,
    onOpenNotificationSheet: () -> Unit,
    openExemption: (String?) -> Unit = {},
    openEnduranceScoring: () -> Unit = {},
    openPrivacy: () -> Unit = {}
) {
    val tabStateHolder = rememberSaveableStateHolder()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(18.dp)
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (fadeIn(tween(BNBUMotion.Standard, delayMillis = 40)) +
                    slideInHorizontally(
                        animationSpec = tween(BNBUMotion.Emphasized, easing = FastOutSlowInEasing),
                        initialOffsetX = { direction * (it / 12) }
                    )).togetherWith(
                    fadeOut(tween(BNBUMotion.Quick)) +
                        slideOutHorizontally(
                            animationSpec = tween(BNBUMotion.Standard, easing = FastOutSlowInEasing),
                            targetOffsetX = { -direction * (it / 14) }
                        )
                )
            },
            label = "rootTabTransition"
        ) { animatedTab ->
            tabStateHolder.SaveableStateProvider(animatedTab.name) {
                when (animatedTab) {
                    AppTab.Dashboard -> DashboardScreen(appState, onOpenNotificationSheet)
                    AppTab.Courses -> CoursesScreen(appState)
                    AppTab.CheckIn -> CheckInScreen(appState)
                    AppTab.Grades -> GradesScreen(appState)
                        AppTab.Profile -> ProfileScreen(
                            appState = appState,
                            onOpenExemption = openExemption,
                            onOpenEnduranceScoring = openEnduranceScoring,
                            onOpenPrivacy = openPrivacy
                        )
                }
            }
        }
    }
}
