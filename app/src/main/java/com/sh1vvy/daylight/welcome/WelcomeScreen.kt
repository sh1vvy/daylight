/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-13 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.theme.daylightBackground
import kotlinx.coroutines.launch

/** A finite native reveal, not a bundled video or an always-running effect. */
@Composable
fun WelcomeScreen(reduceMotion: Boolean, onYouTube: () -> Unit, onGuest: () -> Unit, modifier: Modifier = Modifier) {
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val bloom = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) { reveal.snapTo(1f); bloom.snapTo(1f) }
        else {
            launch { bloom.animateTo(1f, tween(2200, easing = FastOutSlowInEasing)) }
            reveal.animateTo(1f, tween(620, delayMillis = 100, easing = FastOutSlowInEasing))
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().daylightBackground().testTag("daylight-welcome")) {
        val compact = maxHeight < 620.dp
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(if (compact) 16.dp else 32.dp))
            Box(Modifier.widthIn(max = 440.dp).fillMaxWidth().height(if (compact) 160.dp else 300.dp)
                .clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                WelcomeBloom(bloom = { bloom.value }, modifier = Modifier.fillMaxSize())
                Icon(painterResource(R.drawable.ic_logo), null, tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(if (compact) 96.dp else 120.dp).graphicsLayer {
                        val t = bloom.value
                        scaleX = .86f + .14f * t; scaleY = scaleX; alpha = .35f + .65f * t
                        rotationZ = -6f * (1f - t)
                    })
            }
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().graphicsLayer {
                alpha = reveal.value; translationY = (1f - reveal.value) * 20.dp.toPx()
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(if (compact) 24.dp else 36.dp))
                Button(onClick = onYouTube, shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("daylight-welcome-youtube")) {
                    Icon(painterResource(R.drawable.ic_youtube_brand), null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.welcome_youtube_login), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onGuest, shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("daylight-welcome-guest")) {
                    Text(stringResource(R.string.welcome_continue_guest), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.welcome_sign_in_later), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Soft wing-like ribbons follow the same finite bloom; all colors come from the active theme. */
@Composable
private fun WelcomeBloom(bloom: () -> Float, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val t = bloom()
        val center = Offset(size.width * .5f, size.height * .50f)
        val radius = size.minDimension * (.47f + .13f * t)
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = .32f), primary.copy(alpha = .12f), Color.Transparent), center, radius), radius, center)
        for (wing in 0..1) rotate(if (wing == 0) -14f * (1f - t) else 180f + 14f * (1f - t), center) {
            val path = Path().apply {
                moveTo(center.x, center.y)
                cubicTo(size.width * .12f, -size.height * .04f, size.width * .12f, size.height * .73f, center.x, center.y)
                cubicTo(size.width * .74f, size.height * .13f, size.width * .88f, size.height * .72f, center.x, center.y)
                close()
            }
            drawPath(path, Brush.linearGradient(listOf(secondary.copy(alpha = .06f), primary.copy(alpha = .28f), secondary.copy(alpha = .03f)),
                Offset(size.width * .2f, 0f), Offset(size.width * .8f, size.height)))
        }
    }
}
