package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.BrowserScreen
import com.example.ui.BrowserViewModel
import com.example.ui.ThemeMode
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BrowserScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
