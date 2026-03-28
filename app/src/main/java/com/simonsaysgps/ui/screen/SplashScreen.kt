package com.simonsaysgps.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.simonsaysgps.R
import com.simonsaysgps.ui.theme.Coral
import com.simonsaysgps.ui.theme.Sun
import kotlinx.coroutines.delay

private val SplashWarm = Color(0xFFFFF8E2)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1450)
        onFinished()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val splashAsset = when {
        isLandscape -> R.drawable.splash_landscape_1920x1080
        configuration.screenWidthDp >= 500 || configuration.screenHeightDp >= 900 -> R.drawable.splash_portrait_1440x2560
        else -> R.drawable.splash_portrait_1080x1920
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashWarm)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Image(
                    painter = painterResource(id = splashAsset),
                    contentDescription = "Simon Says GPS splash artwork",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopCenter
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(if (isLandscape) 28.dp else 52.dp)
                        .background(SplashWarm)
                )
            }
        }

        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            color = Coral,
            trackColor = Sun.copy(alpha = 0.28f)
        )
    }
}
