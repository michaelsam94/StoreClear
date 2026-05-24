package com.michael.storeclear.playstore

import android.app.Application
import com.michael.storeclear.di.AppContainer
import com.michael.storeclear.presentation.viewmodel.StoreClearViewModel

fun createPlayStoreViewModel(application: Application): StoreClearViewModel {
    val context = application.applicationContext
    val container = AppContainer(context)
    return StoreClearViewModel(
        context = context,
        getStorageSummaryUseCase = container.getStorageSummaryUseCase,
        scanStorageUseCase = container.scanStorageUseCase,
        findDuplicatesUseCase = container.findDuplicatesUseCase,
        buildHeatmapUseCase = container.buildHeatmapUseCase,
        shredFilesUseCase = container.shredFilesUseCase,
        cleanEmptyDirsUseCase = container.cleanEmptyDirsUseCase,
        cleanBrokenCacheUseCase = container.cleanBrokenCacheUseCase,
        hashRepository = container.hashRepository,
        shredRepository = container.shredRepository,
        playStorePreviewMode = true,
    )
}
