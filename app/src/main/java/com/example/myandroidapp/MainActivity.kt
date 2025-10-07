package com.example.myandroidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.myandroidapp.screens.ComplianceOnboardingDialog
import com.example.myandroidapp.screens.RootNavigation
import com.example.myandroidapp.shared.DiagnosticsManager
import com.example.myandroidapp.security.SecurePreferencesManager
import com.example.myandroidapp.theme.CryptoTraderTheme

class MainActivity : ComponentActivity() {
    private val diagnostics = DiagnosticsManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(diagnostics)
        val securePreferencesManager = SecurePreferencesManager.getInstance(applicationContext)
        val shouldShowOnboarding = !securePreferencesManager.getBoolean(PREF_ONBOARDING_COMPLETE, false)

        setContent {
            CryptoTraderTheme {
                val showOnboarding = remember { mutableStateOf(shouldShowOnboarding) }
                Box {
                    RootNavigation(securePreferencesManager)
                    if (showOnboarding.value) {
                        ComplianceOnboardingDialog(onAcknowledge = {
                            securePreferencesManager.putBoolean(PREF_ONBOARDING_COMPLETE, true)
                            showOnboarding.value = false
                        })
                    }
                }
            }
        }
    }

    companion object {
        private const val PREF_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
