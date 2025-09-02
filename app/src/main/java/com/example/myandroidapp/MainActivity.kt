package com.example.myandroidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.myandroidapp.screens.RootNavigation
import com.example.myandroidapp.theme.CryptoTraderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CryptoTraderTheme {
                RootNavigation()
            }
        }
    }
}
