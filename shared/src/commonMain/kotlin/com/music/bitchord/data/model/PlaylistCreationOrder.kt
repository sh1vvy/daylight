package com.music.bitchord.data.model

/** Creation APIs use raw ids while library cards address the same playlist with VL. */
fun playlistCreationOrderKey(id: String): String = id.trim().removePrefix("VL")
