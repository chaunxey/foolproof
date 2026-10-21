package com.foolproof

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.foolproof.ui.FoolproofScreen
import com.foolproof.ui.theme.FoolproofTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoolproofTheme {
                FoolproofScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
