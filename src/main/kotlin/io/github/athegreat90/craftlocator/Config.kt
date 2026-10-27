package io.github.athegreat90.craftlocator

import net.neoforged.neoforge.common.ModConfigSpec
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.permissions.Permissions

object Config {
    private val b = ModConfigSpec.Builder()
    val defaultRadius = b.defineInRange("search.defaultRadius",64,1,128)
    val maximumRadius = b.defineInRange("search.maximumRadius",128,1,128)
    val blockBudget = b.defineInRange("search.maxBlockChecksPerTick",2048,16,65536)
    val millisBudget = b.defineInRange("search.maxScanTimeMillisPerTick",2,1,10)
    val concurrent = b.defineInRange("search.maxConcurrentSearches",4,1,16)
    val timeout = b.defineInRange("search.timeoutSeconds",120,1,600)
    val blocks = b.define("features.enableBlockSearch",true)
    val items = b.define("features.enableDroppedItemSearch",true)
    val sources = b.define("features.enableSourceSearch",true)
    val planner = b.define("features.enableCraftingPlanner",true)
    val containers = b.comment("Requires operator permission AND exact position allowlist; never authorizes other containers.")
        .define("features.enableContainerSearch",false)
    val stations = b.define("features.enableCraftingStationSearch",true)
    val worldOperator = b.define("security.requireOperatorForWorldSearch",true)
    val craftingOperator = b.define("security.requireOperatorForCrafting",false)
    val authorizedContainers = b.comment("Explicit public containers: dimension@x,y,z. Operators only, even if world scanning is public.")
        .defineListAllowEmpty("security.authorizedContainers", listOf<String>(), { "minecraft:overworld@0,64,0" },
            { v -> v is String && v.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+@-?[0-9]+,-?[0-9]+,-?[0-9]+")) })
    val palette = b.define("performance.enableSectionPaletteOptimization",true)
    val progress = b.define("performance.enableProgressNotifications",true)
    val spec: ModConfigSpec = b.build()
    fun operator(source: CommandSourceStack) = source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
    fun worldAllowed(source: CommandSourceStack) = !worldOperator.get() || operator(source)
    fun craftAllowed(source: CommandSourceStack) = !craftingOperator.get() || operator(source)
}
