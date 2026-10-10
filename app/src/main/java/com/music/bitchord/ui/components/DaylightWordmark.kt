package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R

/** Vector outlines preserve the supplied lettering and stay crisp at every density. */
@Composable
fun DaylightWordmark(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.daylight_wordmark),
        contentDescription = "Daylight",
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.width(96.dp).aspectRatio(1210f / 359f),
    )
}
