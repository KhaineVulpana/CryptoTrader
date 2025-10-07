package com.example.myandroidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.myandroidapp.screens.RootNavigation
import com.example.myandroidapp.shared.DiagnosticsManager
import com.example.myandroidapp.theme.CryptoTraderTheme

class MainActivity : ComponentActivity() {
    private val diagnostics = DiagnosticsManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(diagnostics)
        setContent {
            CryptoTraderTheme {
                RootNavigation()
            }
        }
    }
}
