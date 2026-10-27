package io.github.athegreat90.craftlocator

import io.github.athegreat90.craftlocator.search.SearchManager
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.OnDatapackSyncEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

@Mod("craftlocator")
class CraftLocatorMod(bus: IEventBus,container: ModContainer) {
    private val searches=SearchManager()
    private val sources=SourceResolver()
    private val recipes=RecipeResolver()
    private val commands=CraftLocatorCommands(searches,sources,recipes)
    init {
        if(java.lang.Boolean.getBoolean("craftlocator.tests")) CraftLocatorGameTests.register(bus)
        container.registerConfig(ModConfig.Type.SYNCED,Config.spec)
        NeoForge.EVENT_BUS.addListener { e: RegisterCommandsEvent -> commands.register(e) }
        NeoForge.EVENT_BUS.addListener { e: ServerTickEvent.Post -> searches.tick(e.server) }
        NeoForge.EVENT_BUS.addListener { e: ServerStartedEvent -> sources.reload(e.server);recipes.reload(e.server.overworld()) }
        NeoForge.EVENT_BUS.addListener { e: OnDatapackSyncEvent ->
            if(e.player==null) {searches.clear();sources.reload(e.playerList.server);recipes.reload(e.playerList.server.overworld())}
        }
        NeoForge.EVENT_BUS.addListener { _: ServerStoppingEvent -> searches.clear();sources.clear();recipes.clear() }
    }
}
