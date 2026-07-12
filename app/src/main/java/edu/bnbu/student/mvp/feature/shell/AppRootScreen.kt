package edu.bnbu.student.mvp.feature.shell

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.GridBackground
import edu.bnbu.student.mvp.core.state.StudentAppState
import edu.bnbu.student.mvp.feature.checkin.CheckInScreen
import edu.bnbu.student.mvp.feature.courses.CoursesScreen
import edu.bnbu.student.mvp.feature.dashboard.DashboardScreen
import edu.bnbu.student.mvp.feature.grades.GradesScreen
import edu.bnbu.student.mvp.feature.login.LoginScreen
import edu.bnbu.student.mvp.feature.profile.PrivacyPolicyScreen
import edu.bnbu.student.mvp.feature.profile.ProfileScreen
import edu.bnbu.student.mvp.feature.scoring.EnduranceScoringScreen
import edu.bnbu.student.mvp.feature.exemption.ExemptionScreen

enum class AppTab(
    val label: String,
    val icon: ImageVector
) {
    Dashboard("首页", Icons.Filled.GridView),
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

@Composable
fun AppRootScreen(appState: StudentAppState) {
    // Keep appState as-is — it's now managed by the Activity lifecycle.
    var selectedTab by remember { mutableStateOf(AppTab.Dashboard) }
    var subScreen by remember { mutableStateOf(SubScreen.None) }

    if (!appState.isAuthenticated) {
        Box(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            GridBackground(modifier = Modifier.fillMaxSize())
            LoginScreen(
                onLogin = { account, password ->
                    appState.login(account, password)
                },
                isLoading = appState.isLoading,
                errorMessage = appState.lastError
            )
        }
        return
    }

    // Sub-screen overlay for tools launched from Profile
    if (subScreen != SubScreen.None) {
        BackHandler {
            subScreen = SubScreen.None
        }
        val repo = appState.apiRepository
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            GridBackground(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(18.dp)
            ) {
                when (subScreen) {
                    SubScreen.EnduranceScoring -> {
                        if (repo != null) {
                            EnduranceScoringScreen(
                                student = appState.workspace.student,
                                repository = repo,
                                onBack = { subScreen = SubScreen.None }
                            )
                        }
                    }
                    SubScreen.Exemption -> {
                        if (repo != null) {
                            ExemptionScreen(
                                repository = repo,
                                onBack = { subScreen = SubScreen.None }
                            )
                        }
                    }
                    SubScreen.PrivacyPolicy -> {
                        PrivacyPolicyScreen(
                            onBack = { subScreen = SubScreen.None }
                        )
                    }
                    SubScreen.None -> { /* unreachable */ }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            StudentBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                checkInBadge = appState.actionableRecordCount,
                profileBadge = appState.unreadNoticeCount
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            GridBackground(modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (appState.lastError != null || appState.isShowingCachedData) {
                    SyncStatusBanner(appState)
                }
                Box(modifier = Modifier.weight(1f)) {
                    RootTabContent(
                        tab = selectedTab,
                        appState = appState,
                        contentPadding = PaddingValues(0.dp),
                        openCheckIn = { selectedTab = AppTab.CheckIn },
                        openGrades = { selectedTab = AppTab.Grades },
                        openProfile = { selectedTab = AppTab.Profile },
                        openScoring = { subScreen = SubScreen.EnduranceScoring },
                        openExemption = { subScreen = SubScreen.Exemption },
                        openPrivacy = { subScreen = SubScreen.PrivacyPolicy }
                    )
                }
            }
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
    onTabSelected: (AppTab) -> Unit,
    checkInBadge: Int,
    profileBadge: Int
) {
    val cs = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = cs.surface,
        contentColor = cs.onSurface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        AppTab.entries.forEach { tab ->
            val badge = when (tab) {
                AppTab.CheckIn -> checkInBadge
                AppTab.Profile -> profileBadge
                else -> 0
            }
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Box {
                        Icon(imageVector = tab.icon, contentDescription = tab.label)
                        if (badge > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .background(cs.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = badge.toString(),
                                    color = cs.onPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
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
    openCheckIn: () -> Unit,
    openGrades: () -> Unit,
    openProfile: () -> Unit,
    openScoring: () -> Unit = {},
    openExemption: () -> Unit = {},
    openPrivacy: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(18.dp)
    ) {
        when (tab) {
            AppTab.Dashboard -> DashboardScreen(appState, openCheckIn, openGrades, openProfile)
            AppTab.Courses -> CoursesScreen(appState)
            AppTab.CheckIn -> CheckInScreen(appState)
            AppTab.Grades -> GradesScreen(appState)
            AppTab.Profile -> ProfileScreen(
                    appState = appState,
                    onOpenScoring = openScoring,
                    onOpenExemption = openExemption,
                    onOpenPrivacy = openPrivacy
                )
        }
    }
}
