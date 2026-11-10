package com.mfouad.mobiletrackpad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mfouad.mobiletrackpad.ui.HomeScreen
import com.mfouad.mobiletrackpad.ui.theme.TrackPadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackPadTheme {
                HomeScreen()
            }
        }
    }
}
