package com.markbook.android

import java.text.Normalizer
import java.util.Locale

/** Pure protection rules for Vault paths; UI filtering must not be the only guard. */
object VaultPathPolicy {
    private val protectedRootChildren = setOf(
        ".obsidian", ".markbook", ".trash", "assets", "attachments"
    )

    fun isProtected(relativePath: String): Boolean =
        relativePath.split('/').any { it in protectedRootChildren }

    fun isProtectedRootName(name: String): Boolean =
        Normalizer.normalize(name, Normalizer.Form.NFC).lowercase(Locale.ROOT) in protectedRootChildren
}
