package com.materialmail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.materialmail.appshell.ComposeRequest
import com.materialmail.appshell.MaterialMailNavHost
import com.materialmail.appshell.parseComposeIntent
import com.materialmail.designsystem.theme.MaterialMailTheme

class MainActivity : ComponentActivity() {

    private val pendingCompose = mutableStateOf<ComposeRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingCompose.value = parseComposeIntent(intent)
        setContent {
            MaterialMailTheme {
                MaterialMailNavHost(
                    container = (application as MaterialMailApp).container,
                    pendingCompose = pendingCompose,
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        parseComposeIntent(intent)?.let { pendingCompose.value = it }
    }
}