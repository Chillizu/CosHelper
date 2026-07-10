package com.coshelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CosHelper",
            style = MaterialTheme.typography.titleLarge,
            color = colorScheme.primary
        )
        BigRoundButton(text = "对讲", color = colorScheme.primaryContainer, contentColor = colorScheme.onPrimaryContainer, onClick = onPtt)
        BigRoundButton(text = "语音转文字", color = colorScheme.secondaryContainer, contentColor = colorScheme.onSecondaryContainer, onClick = onStt)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            SmallRoundButton(text = "变声器", onClick = onRvc)
            SmallRoundButton(text = "设备设置", onClick = onSettings)
        }
    }
}

@Composable
fun BigRoundButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(180.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor)
    ) {
        Text(text = text, fontSize = 26.sp)
    }
}

@Composable
fun SmallRoundButton(text: String, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = Modifier.size(140.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer
        )
    ) {
        Text(text = text, fontSize = 22.sp)
    }
}
