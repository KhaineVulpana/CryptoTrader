package com.example.myandroidapp

import android.app.Application
import android.content.Context
import com.example.myandroidapp.monitoring.AppPipelineObserver
import com.example.myandroidapp.work.BackgroundPollingScheduler

class CryptoTraderApp : Application() {
    lateinit var pipelineObserver: AppPipelineObserver
        private set

    override fun onCreate() {
        super.onCreate()
        pipelineObserver = AppPipelineObserver(this)
        BackgroundPollingScheduler.schedule(this)
    }

    companion object {
        fun pipelineObserver(context: Context): AppPipelineObserver {
            val app = context.applicationContext as CryptoTraderApp
            return app.pipelineObserver
        }
    }
}
