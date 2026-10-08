package com.music.bitchord.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** Lets artwork, glass and custom drawing follow the selected Pink Cloud palette. */
val LocalPinkCloud = staticCompositionLocalOf { false }

/** Native Android surfaces and Expressive controls, without artwork or glass backgrounds. */
val LocalMaterialExpressive = staticCompositionLocalOf { false }
