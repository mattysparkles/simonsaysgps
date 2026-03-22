package com.simonsaysgps.domain.repository

import com.simonsaysgps.domain.model.SettingsModel
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<SettingsModel>
    suspend fun update(transform: (SettingsModel) -> SettingsModel)
}
