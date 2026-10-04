package com.example.nutricart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.nutricart.navigation.NutriCartNavHost
import com.example.nutricart.ui.theme.NutriCartTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutriCartTheme {
                NutriCartNavHost()
            }
        }
    }
}
