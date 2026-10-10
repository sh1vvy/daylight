package com.music.bitchord.data.settings

/** Retired settings cannot be revived by an older backup. */
internal object RetiredSettings {
    fun isRetired(key: String): Boolean =
        key == "legacy_mesh_gradient" || key.startsWith("webdav_") || key.startsWith("smb_")
}
