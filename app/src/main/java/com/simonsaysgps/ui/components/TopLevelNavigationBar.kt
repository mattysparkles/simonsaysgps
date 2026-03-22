package com.simonsaysgps.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun TopLevelNavigationBar(
    selected: TopLevelDestination,
    onMapClick: () -> Unit,
    onExploreClick: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == TopLevelDestination.MAP,
            onClick = onMapClick,
            icon = { Icon(Icons.Default.Map, contentDescription = null) },
            label = { Text("Map") }
        )
        NavigationBarItem(
            selected = selected == TopLevelDestination.EXPLORE,
            onClick = onExploreClick,
            icon = { Icon(Icons.Default.Explore, contentDescription = null) },
            label = { Text("Explore") }
        )
    }
}

enum class TopLevelDestination { MAP, EXPLORE }
