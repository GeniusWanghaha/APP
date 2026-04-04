package com.photosentinel.health.infrastructure.di

import android.content.Context
import com.photosentinel.health.application.service.HealthAgentService
import com.photosentinel.health.application.service.LifestyleCatalogService
import com.photosentinel.health.application.usecase.AnalyzeMeasurementUseCase
import com.photosentinel.health.application.usecase.AnalyzeTrendUseCase
import com.photosentinel.health.application.usecase.CheckServiceHealthUseCase
import com.photosentinel.health.application.usecase.GetHealthPlansUseCase
import com.photosentinel.health.application.usecase.GetMallProductsUseCase
import com.photosentinel.health.application.usecase.GetRecentRecordsUseCase
import com.photosentinel.health.application.usecase.SendChatMessageUseCase
import com.photosentinel.health.application.usecase.UpdateHealthPlanCompletionUseCase
import com.photosentinel.health.data.repository.BleHardwareBridgeRepository
import com.photosentinel.health.data.repository.PreferenceUserSettingsRepository
import com.photosentinel.health.data.repository.SqliteHealthRepository
import com.photosentinel.health.data.repository.SqliteLifestyleRepository
import com.photosentinel.health.data.repository.SqliteSessionAuditRepository
import com.photosentinel.health.domain.repository.HealthRepository
import com.photosentinel.health.domain.repository.HardwareInterfaceRepository
import com.photosentinel.health.domain.repository.LifestyleRepository
import com.photosentinel.health.domain.repository.SessionAuditRepository
import com.photosentinel.health.domain.repository.UserSettingsRepository
import com.photosentinel.health.infrastructure.db.HealthDbHelper
import com.photosentinel.health.infrastructure.report.LlmContentProvider

object AppContainer {
    @Volatile
    private var initialized = false

    private lateinit var healthRepository: HealthRepository
    private lateinit var lifestyleRepository: LifestyleRepository
    private lateinit var hardwareInterfaceRepositoryInternal: HardwareInterfaceRepository
    private lateinit var healthAgentService: HealthAgentService
    private lateinit var lifestyleCatalogService: LifestyleCatalogService
    private lateinit var userSettingsRepositoryInternal: UserSettingsRepository
    private lateinit var sessionAuditRepositoryInternal: SessionAuditRepository

    private lateinit var analyzeMeasurementUseCaseInternal: AnalyzeMeasurementUseCase
    private lateinit var analyzeTrendUseCaseInternal: AnalyzeTrendUseCase
    private lateinit var getRecentRecordsUseCaseInternal: GetRecentRecordsUseCase
    private lateinit var sendChatMessageUseCaseInternal: SendChatMessageUseCase
    private lateinit var checkServiceHealthUseCaseInternal: CheckServiceHealthUseCase
    private lateinit var getHealthPlansUseCaseInternal: GetHealthPlansUseCase
    private lateinit var getMallProductsUseCaseInternal: GetMallProductsUseCase
    private lateinit var updateHealthPlanCompletionUseCaseInternal: UpdateHealthPlanCompletionUseCase

    fun initialize(context: Context) {
        if (initialized) {
            return
        }
        synchronized(this) {
            if (initialized) {
                return
            }

            val appContext = context.applicationContext
            val dbHelper = HealthDbHelper(appContext)

            userSettingsRepositoryInternal = PreferenceUserSettingsRepository(appContext)
            healthRepository = SqliteHealthRepository(dbHelper = dbHelper)
            lifestyleRepository = SqliteLifestyleRepository(dbHelper = dbHelper)
            sessionAuditRepositoryInternal = SqliteSessionAuditRepository(
                context = appContext,
                dbHelper = dbHelper
            )
            hardwareInterfaceRepositoryInternal = BleHardwareBridgeRepository(
                context = appContext,
                configProvider = { userSettingsRepositoryInternal.currentBleConfig() }
            )

            healthAgentService = HealthAgentService(
                repository = healthRepository,
                contentProvider = LlmContentProvider()
            )
            lifestyleCatalogService = LifestyleCatalogService(repository = lifestyleRepository)

            analyzeMeasurementUseCaseInternal = AnalyzeMeasurementUseCase(service = healthAgentService)
            analyzeTrendUseCaseInternal = AnalyzeTrendUseCase(service = healthAgentService)
            getRecentRecordsUseCaseInternal = GetRecentRecordsUseCase(service = healthAgentService)
            sendChatMessageUseCaseInternal = SendChatMessageUseCase(service = healthAgentService)
            checkServiceHealthUseCaseInternal = CheckServiceHealthUseCase(service = healthAgentService)
            getHealthPlansUseCaseInternal = GetHealthPlansUseCase(service = lifestyleCatalogService)
            getMallProductsUseCaseInternal = GetMallProductsUseCase(service = lifestyleCatalogService)
            updateHealthPlanCompletionUseCaseInternal =
                UpdateHealthPlanCompletionUseCase(service = lifestyleCatalogService)

            initialized = true
        }
    }

    private fun requireInitialized() {
        check(initialized) {
            "依赖容器尚未初始化，请先在 Application.onCreate 中调用 AppContainer.initialize"
        }
    }

    val analyzeMeasurementUseCase: AnalyzeMeasurementUseCase
        get() {
            requireInitialized()
            return analyzeMeasurementUseCaseInternal
        }

    val analyzeTrendUseCase: AnalyzeTrendUseCase
        get() {
            requireInitialized()
            return analyzeTrendUseCaseInternal
        }

    val getRecentRecordsUseCase: GetRecentRecordsUseCase
        get() {
            requireInitialized()
            return getRecentRecordsUseCaseInternal
        }

    val sendChatMessageUseCase: SendChatMessageUseCase
        get() {
            requireInitialized()
            return sendChatMessageUseCaseInternal
        }

    val checkServiceHealthUseCase: CheckServiceHealthUseCase
        get() {
            requireInitialized()
            return checkServiceHealthUseCaseInternal
        }

    val getHealthPlansUseCase: GetHealthPlansUseCase
        get() {
            requireInitialized()
            return getHealthPlansUseCaseInternal
        }

    val getMallProductsUseCase: GetMallProductsUseCase
        get() {
            requireInitialized()
            return getMallProductsUseCaseInternal
        }

    val updateHealthPlanCompletionUseCase: UpdateHealthPlanCompletionUseCase
        get() {
            requireInitialized()
            return updateHealthPlanCompletionUseCaseInternal
        }

    val hardwareInterfaceRepository: HardwareInterfaceRepository
        get() {
            requireInitialized()
            return hardwareInterfaceRepositoryInternal
        }

    val userSettingsRepository: UserSettingsRepository
        get() {
            requireInitialized()
            return userSettingsRepositoryInternal
        }

    val sessionAuditRepository: SessionAuditRepository
        get() {
            requireInitialized()
            return sessionAuditRepositoryInternal
        }
}
