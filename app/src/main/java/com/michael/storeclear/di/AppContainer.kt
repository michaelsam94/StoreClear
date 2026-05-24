package com.michael.storeclear.di

import android.content.Context
import androidx.room.Room
import com.michael.storeclear.data.datasource.local.*
import com.michael.storeclear.data.repository.impl.*
import com.michael.storeclear.domain.repository.*
import com.michael.storeclear.domain.usecase.*

class AppContainer(private val context: Context) {

    val database: StoreClearDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            StoreClearDatabase::class.java,
            "store_clear.db"
        ).fallbackToDestructiveMigration().build()
    }

    val dao: StoreClearDao by lazy {
        database.dao()
    }

    // Datasources
    val safDataSource by lazy { StorageAccessFrameworkDataSource(context) }
    val fileHashingDataSource by lazy { FileHashingDataSource(context, dao) }
    val fileOverwriteDataSource by lazy { FileOverwriteDataSource(context) }
    val cacheDataSource by lazy { CacheDataSource(context) }

    // Repositories
    val fileRepository: FileRepository by lazy { FileRepositoryImpl(safDataSource) }
    val hashRepository: HashRepository by lazy { HashRepositoryImpl(fileHashingDataSource, dao) }
    val shredRepository: ShredRepository by lazy { ShredRepositoryImpl(fileOverwriteDataSource, dao) }
    val cacheRepository: CacheRepository by lazy { CacheRepositoryImpl(cacheDataSource, safDataSource) }

    // UseCases
    val getStorageSummaryUseCase by lazy { GetStorageSummaryUseCase(fileRepository) }
    val scanStorageUseCase by lazy { ScanStorageUseCase(fileRepository) }
    val findDuplicatesUseCase by lazy { FindDuplicatesUseCase(fileRepository, hashRepository) }
    val buildHeatmapUseCase by lazy { BuildHeatmapUseCase(fileRepository) }
    val shredFilesUseCase by lazy { ShredFilesUseCase(shredRepository) }
    val cleanEmptyDirsUseCase by lazy { CleanEmptyDirsUseCase(cacheRepository) }
    val cleanBrokenCacheUseCase by lazy { CleanBrokenCacheUseCase(cacheRepository) }
}
