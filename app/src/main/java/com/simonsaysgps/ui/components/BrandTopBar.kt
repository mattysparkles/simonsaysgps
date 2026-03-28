package com.simonsaysgps.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.simonsaysgps.ui.theme.Aqua
import com.simonsaysgps.ui.theme.ElectricBlue
import com.simonsaysgps.ui.theme.NightSky
import com.simonsaysgps.ui.theme.Sun

@Composable
fun BrandTopBar(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    mascotResId: Int? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(NightSky, ElectricBlue, Aqua)),
                RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
            )
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                mascotResId?.let { resId ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Sun.copy(alpha = 0.18f)
                    ) {
                        Image(
                            painter = painterResource(id = resId),
                            contentDescription = "Simon mascot",
                            modifier = Modifier
                                .size(68.dp)
                                .padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    badge?.let {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Sun.copy(alpha = 0.92f)
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = NightSky
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}
