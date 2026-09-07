package com.functy.autofewards.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.functy.autofewards.ui.component.PagerNavigationSpringSpec
import com.functy.autofewards.ui.component.bottombar.BottomBar
import com.functy.autofewards.ui.component.bottombar.MainPagerState
import com.functy.autofewards.ui.component.bottombar.NavigationBadgeState
import com.functy.autofewards.ui.component.bottombar.SideRail
import com.functy.autofewards.ui.component.bottombar.rememberMainPagerState
import com.functy.autofewards.ui.component.bottombar.useNavigationRail
import com.functy.autofewards.ui.screen.account.AccountScreen
import com.functy.autofewards.ui.screen.bing.BingScreen
import com.functy.autofewards.ui.screen.home.HomeScreen
import com.functy.autofewards.ui.screen.settings.SettingPager
import com.functy.autofewards.ui.theme.AutoFewardsTheme
import com.functy.autofewards.ui.theme.LocalColorMode
import com.functy.autofewards.ui.theme.LocalEnableBlur
import com.functy.autofewards.ui.theme.LocalEnableFloatingBottomBar
import com.functy.autofewards.ui.theme.LocalEnableFloatingBottomBarBlur
import com.functy.autofewards.ui.theme.LocalEnableNavigationBadge
import com.functy.autofewards.ui.util.rememberBlurBackdrop
import com.functy.autofewards.ui.viewmodel.MainActivityViewModel
import com.functy.autofewards.ui.viewmodel.MainPagerConfig
import com.functy.autofewards.ui.navigation3.LocalNavigator
import com.functy.autofewards.ui.navigation3.Navigator
import com.functy.autofewards.ui.navigation3.Route
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.entryProvider
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel = viewModel<MainActivityViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedMainPage by viewModel.selectedMainPage.collectAsStateWithLifecycle()
            val appSettings = uiState.appSettings
            val darkMode = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && androidx.compose.foundation.isSystemInDarkTheme())

            androidx.compose.runtime.DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, uiState.pageScale) {
                Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalDensity provides density,
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableBlur provides uiState.enableBlur,
                LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides uiState.enableFloatingBottomBarBlur,
                LocalEnableNavigationBadge provides uiState.enableNavigationBadge,
                LocalUiMode provides uiState.uiMode,
            ) {
                val navController = com.functy.autofewards.ui.navigation3.rememberNavigator(
                    com.functy.autofewards.ui.navigation3.Route.Main
                )
                CompositionLocalProvider(
                    com.functy.autofewards.ui.navigation3.LocalNavigator provides navController
                ) {
                    AutoFewardsTheme(appSettings = appSettings) {
                        NavDisplay(
                            backStack = navController.backStack,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            onBack = { navController.pop() },
                            entryProvider = entryProvider {
                                entry<Route.Main> {
                                    MainScreen(
                                        initialPage = selectedMainPage,
                                        onPageChanged = viewModel::setSelectedMainPage,
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
) {
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val useNavigationRail = useNavigationRail(enableFloatingBottomBar)
    val navController = LocalNavigator.current
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { MainPagerConfig.PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(
        pagerState = pagerState,
        animatePageChanges = !useNavigationRail,
    )

    val enableNavigationBadge = LocalEnableNavigationBadge.current
    val navigationBadge = NavigationBadgeState()

    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBlurBackdrop(enableBlur)

    val backdrop = key(surfaceColor) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    }

    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(settledPage) {
        onPageChanged(settledPage)
    }

    // 启动即查询任务真实状态（本地标记短路；Engine M4）
    LaunchedEffect(Unit) {
        com.functy.autofewards.core.TaskScheduler.refreshStatuses(
            com.functy.autofewards.data.repository.SettingsRepositoryImpl(),
        )
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }

    MainScreenBackHandler(mainPagerState, navController)

    CompositionLocalProvider(
        com.functy.autofewards.ui.LocalMainPagerState provides mainPagerState
    ) {
        val pagerContent = @Composable { bottomInnerPadding: Dp ->
            Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
                HorizontalPager(
                    modifier = Modifier
                        .then(if (enableFloatingBottomBar && enableFloatingBottomBarBlur) Modifier.layerBackdrop(backdrop) else Modifier),
                    state = mainPagerState.pagerState,
                    beyondViewportPageCount = 3,
                    overscrollEffect = null,
                    userScrollEnabled = true,
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(bottomInnerPadding, page == settledPage)
                        1 -> AccountScreen(bottomInnerPadding, page == settledPage)
                        2 -> BingScreen(bottomInnerPadding, page == settledPage)
                        3 -> SettingPager(bottomInnerPadding)
                    }
                }
            }
        }

        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            Scaffold { _ ->
                Row {
                    SideRail(navigationBadge)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .consumeWindowInsets(startInsets)
                    ) {
                        pagerContent(navBarBottomPadding)
                    }
                }
            }
        } else {
            val bottomBar = @Composable {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        navigationBadge = navigationBadge,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            Scaffold(bottomBar = bottomBar) { innerPadding ->
                pagerContent(innerPadding.calculateBottomPadding())
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main &&
                    navController.backStackSize() == 1 &&
                    mainState.selectedPage != 0
        }
    }
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = { mainState.animateToPage(0) },
    )
}
