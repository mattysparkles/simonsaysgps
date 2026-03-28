package com.simonsaysgps.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simonsaysgps.ui.theme.Aqua
import com.simonsaysgps.ui.theme.ElectricBlue
import com.simonsaysgps.ui.theme.NightSky
import com.simonsaysgps.ui.theme.RouteWhite
import com.simonsaysgps.ui.theme.Sun

@Composable
fun TopLevelNavigationBar(
    selected: TopLevelDestination,
    onMapClick: () -> Unit,
    onExploreClick: () -> Unit
) {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = NightSky
    ) {
        NavigationBar(containerColor = NightSky) {
            NavigationBarItem(
                selected = selected == TopLevelDestination.MAP,
                onClick = onMapClick,
                icon = { Icon(Icons.Default.Map, contentDescription = null) },
                label = { Text("Drive") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NightSky,
                    selectedTextColor = RouteWhite,
                    indicatorColor = Sun,
                    unselectedIconColor = Aqua.copy(alpha = 0.82f),
                    unselectedTextColor = Aqua.copy(alpha = 0.82f)
                )
            )
            NavigationBarItem(
                selected = selected == TopLevelDestination.EXPLORE,
                onClick = onExploreClick,
                icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                label = { Text("Play") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NightSky,
                    selectedTextColor = RouteWhite,
                    indicatorColor = ElectricBlue,
                    unselectedIconColor = Aqua.copy(alpha = 0.82f),
                    unselectedTextColor = Aqua.copy(alpha = 0.82f)
                )
            )
        }
    }
}

enum class TopLevelDestination { MAP, EXPLORE }
