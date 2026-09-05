package com.rentalps.tv

import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        setContent {
            RentalPSTvScreen()
        }
    }
}

@Composable
fun RentalPSTvScreen() {

    var remainingSeconds by remember {
        mutableLongStateOf(0L)
    }

    var sessionActive by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(sessionActive) {
        while (sessionActive && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }

        if (remainingSeconds <= 0) {
            sessionActive = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        if (sessionActive && remainingSeconds > 0) {

            // Saat sesi aktif, layar game tetap normal.
            // Overlay timer baru muncul ketika tersisa 5 menit.
            if (remainingSeconds <= 300) {

                Text(
                    text = formatTime(remainingSeconds),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = 12.dp
                        )
                )
            }

        } else {

            // Waktu habis → blank screen.
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = "WAKTU HABIS",
                    color = Color.White,
                    fontSize = 32.sp
                )

                Text(
                    text = "Silakan ke kasir",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

private fun formatTime(seconds: Long): String {

    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return "%02d:%02d:%02d".format(
        hours,
        minutes,
        secs
    )
}
