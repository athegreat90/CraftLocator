package io.github.athegreat90.craftlocator.compat

import net.neoforged.fml.ModContainer
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec

/** FML 12 (Minecraft 26.3) renamed the server-authoritative configuration type. */
fun registerGameplayConfig(container: ModContainer, spec: ModConfigSpec) {
    container.registerConfig(ModConfig.Type.SYNCED, spec)
}
