package com.coshelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coshelper.ui.components.FeatureTile
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.coshelper.ui.screens.ChatScreen
import com.coshelper.ui.screens.RvcScreen
import com.coshelper.ui.screens.SettingsScreen
import com.coshelper.ui.screens.SttScreen
import com.coshelper.ui.theme.CosHelperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosHelperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CosHelperNavHost()
                }
            }
        }
    }
}

@Composable
fun CosHelperNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onPtt = { navController.navigate("chat") },
                onStt = { navController.navigate("stt") },
                onRvc = { navController.navigate("rvc") },
                onSettings = { navController.navigate("settings") }
            )
        }
        composable("chat") { ChatScreen(onBack = { navController.popBackStack() }) }
        composable("stt") { SttScreen(onBack = { navController.popBackStack() }) }
        composable("rvc") { RvcScreen(onBack = { navController.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
fun HomeScreen(
    onPtt: () -> Unit,
    onStt: () -> Unit,
    onRvc: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CosHelper",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { FeatureTile(icon = Icons.Default.Mic, label = "对讲", onClick = onPtt) }
            item { FeatureTile(icon = Icons.Default.Chat, label = "语音转文字", onClick = onStt) }
            item { FeatureTile(icon = Icons.Default.Build, label = "变声器", onClick = onRvc) }
            item { FeatureTile(icon = Icons.Default.Settings, label = "设置", onClick = onSettings) }
        }
    }
}


