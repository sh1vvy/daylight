package com.music.bitchord.ui

import com.music.bitchord.data.model.DetailPage

/** Each navigation visit owns a viewport, even when its browse id is already on the stack. */
internal fun DetailPage.detailRouteKey(): String = "detail:$instanceId:$browseId"
