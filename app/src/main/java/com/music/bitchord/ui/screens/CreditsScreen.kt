package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.music.bitchord.R

/** Acknowledgments live here so the main Settings footer stays concise. */
@Composable
fun CreditsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = stringResource(R.string.credits),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
        )
        SettingsGroup(
            header = stringResource(R.string.credits_bitchord_heading),
        ) {
            Text(
                text = buildAnnotatedString {
                    withLink(LinkAnnotation.Url("https://github.com/sh1vvy/daylight/blob/main/LICENSE", linkStyles)) {
                        append("GNU General Public License v3.0")
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(18.dp),
            )
        }
        SettingsGroup(header = stringResource(R.string.credits_icon_heading)) {
            Text(
                text = buildAnnotatedString {
                    append("© art by 11 (")
                    withLink(LinkAnnotation.Url("https://www.instagram.com/_artbyeleven/", linkStyles)) {
                        append("_artbyeleven on IG")
                    }
                    append(")")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(18.dp),
            )
        }
    }
}
