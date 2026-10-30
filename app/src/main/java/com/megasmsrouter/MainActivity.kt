package com.megasmsrouter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.megasmsrouter.ui.navigation.MsrAppRoot
import com.megasmsrouter.ui.theme.MsrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MsrTheme {
                // Default UI language is Persian (RTL). When an English translation is added in
                // values-en/, derive the direction from the active locale instead.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MsrAppRoot()
                }
            }
        }
    }
}
