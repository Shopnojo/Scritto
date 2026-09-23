package com.internship.scritto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.internship.scritto.navigation.ScrittoNavigation
import com.internship.scritto.ui.theme.ScrittoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            ScrittoTheme {
                ScrittoNavigation()
            }
        }
    }
}