package com.functy.autofewards.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.functy.autofewards.AutoFewardsApp
import com.functy.autofewards.data.repository.SettingsRepository
import com.functy.autofewards.data.repository.SettingsRepositoryImpl
import com.functy.autofewards.data.repository.changes
import com.functy.autofewards.ui.UiMode
import com.functy.autofewards.ui.theme.AppSettings
import com.functy.autofewards.ui.theme.ThemeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val enableNavigationBadge: Boolean,
    val uiMode: UiMode,
)

class MainActivityViewModel : ViewModel() {

    private val prefs = AutoFewardsApp.instance.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val settingRepo: SettingsRepository = SettingsRepositoryImpl()

    private val _uiState = MutableStateFlow(readUiState())
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()

    private val _selectedMainPage = MutableStateFlow(0)
    val selectedMainPage: StateFlow<Int> = _selectedMainPage.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.changes(SettingsRepositoryImpl.observedKeys).collect {
                _uiState.value = readUiState()
            }
        }
    }

    fun setSelectedMainPage(page: Int) {
        _selectedMainPage.value = MainPagerConfig.coercePage(page)
    }

    private fun readUiState(): MainActivityUiState {
        return MainActivityUiState(
            appSettings = ThemeController.getAppSettings(settingRepo),
            pageScale = settingRepo.pageScale,
            enableBlur = settingRepo.enableBlur,
            enableFloatingBottomBar = settingRepo.enableFloatingBottomBar,
            enableFloatingBottomBarBlur = settingRepo.enableFloatingBottomBarBlur,
            enableNavigationBadge = settingRepo.enableNavigationBadge,
            uiMode = UiMode.fromValue(settingRepo.uiMode),
        )
    }
}

object MainPagerConfig {
    const val PAGE_COUNT = 4
    const val LAST_PAGE_INDEX = PAGE_COUNT - 1

    fun coercePage(page: Int): Int = page.coerceIn(0, LAST_PAGE_INDEX)
}
