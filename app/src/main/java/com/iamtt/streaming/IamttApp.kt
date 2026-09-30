package com.iamtt.streaming

import android.app.Application
import com.iamtt.streaming.data.ConfigStore
import com.iamtt.streaming.data.LibraryRepository
import com.iamtt.streaming.data.ProfilePhotos
import com.iamtt.streaming.data.WatchHistory
import com.iamtt.streaming.drive.DriveAuth
import com.iamtt.streaming.drive.DriveClient
import com.iamtt.streaming.drive.GoogleAccountAuth
import com.iamtt.streaming.drive.ServiceAccountAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Simple app-wide object graph; small enough that a DI framework isn't worth it. */
class IamttApp : Application() {
    lateinit var config: ConfigStore
        private set
    lateinit var auth: DriveAuth
        private set
    lateinit var drive: DriveClient
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var history: WatchHistory
        private set
    lateinit var photos: ProfilePhotos
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val baseHttp = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        config = ConfigStore(this)
        auth = DriveAuth(
            config = config,
            serviceAccount = ServiceAccountAuth(keyProvider = { config.current.serviceAccountJson }, http = baseHttp),
            google = GoogleAccountAuth(this, accountProvider = { config.current.googleAccount }),
        )
        drive = DriveClient(auth, baseHttp)
        library = LibraryRepository(this, drive, config, appScope)
        history = WatchHistory(this)
        photos = ProfilePhotos(this)
    }
}
