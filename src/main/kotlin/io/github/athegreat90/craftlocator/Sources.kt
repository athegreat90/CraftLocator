package io.github.athegreat90.craftlocator

import com.google.gson.JsonParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.Block
import org.slf4j.LoggerFactory

data class SourceRule(val target: String, val blocks: Set<Block>, val conditions: String)

class SourceResolver {
    private var rules: Map<String,List<SourceRule>> = emptyMap()
    fun rules(item: String): List<SourceRule> = rules[item].orEmpty()
    fun clear() { rules = emptyMap() }
    fun reload(server: MinecraftServer) {
        val parsed = mutableListOf<SourceRule>()
        val resources = server.resourceManager.listResources("craftlocator/sources") { it.path.endsWith(".json") }
        if(resources.size>4096) LoggerFactory.getLogger("CraftLocator").error("Source rule limit exceeded: processing the first 4096 sorted resources")
        for ((id,resource) in resources.toSortedMap(compareBy { it.toString() }).entries.take(4096)) {
            try {
                resource.openAsReader().use { reader ->
                    val buffer=CharArray(65537)
                    var length=0
                    while(length<buffer.size) {val n=reader.read(buffer,length,buffer.size-length);if(n<0) break;length+=n}
                    require(length<=65536) { "Rule exceeds 64 KiB" }
                    val text=String(buffer,0,length)
                    val json = JsonParser.parseString(text).asJsonObject
                    require(json.keySet().all { it in setOf("target_item","source_blocks","conditions","source_type","processing_input","processing_recipe_type") }) { "Unknown rule field" }
                    val target = json.get("target_item").asString
                    require(BuiltInRegistries.ITEM.containsKey(Identifier.parse(target))) { "Unknown target item: $target" }
                    require(json.get("source_type").asString == "harvestable_block") { "Unsupported source_type" }
                    val ids = json.getAsJsonArray("source_blocks")
                    require(ids.size() in 1..256) { "Expected 1..256 source_blocks" }
                    val blocks = ids.map {
                        val blockId=Identifier.parse(it.asString)
                        require(BuiltInRegistries.BLOCK.containsKey(blockId)) { "Unknown source block: $blockId" }
                        BuiltInRegistries.BLOCK.getValue(blockId)
                    }.toSet()
                    val conditions = json.get("conditions")?.asString ?: "craftlocator.condition.unknown"
                    require(conditions.length <= 512) { "Conditions too long" }
                    val inputId=json.get("processing_input")?.asString
                    val processingType=json.get("processing_recipe_type")?.asString
                    require((inputId==null)==(processingType==null)) { "Processing input and recipe type must be declared together" }
                    if(inputId!=null && processingType!=null) {
                        require(processingType in setOf("minecraft:smelting","minecraft:blasting")) { "Unsupported processing path" }
                        val input=Identifier.parse(inputId)
                        require(BuiltInRegistries.ITEM.containsKey(input)) { "Unknown processing input" }
                        val stack=net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(input))
                        val recipeInput=net.minecraft.world.item.crafting.SingleRecipeInput(stack)
                        val valid=server.recipeManager.recipes.any { holder ->
                            val recipe=holder.value() as? net.minecraft.world.item.crafting.AbstractCookingRecipe
                            recipe!=null && BuiltInRegistries.RECIPE_TYPE.getKey(recipe.type).toString()==processingType &&
                                recipe.matches(recipeInput,server.overworld()) &&
                                BuiltInRegistries.ITEM.getKey(recipe.assemble(recipeInput).item).toString()==target
                        }
                        require(valid) { "No active $processingType path from $inputId to $target; source omitted" }
                    }
                    parsed.add(SourceRule(target,blocks,conditions))
                }
            } catch (e: Exception) {
                LoggerFactory.getLogger("CraftLocator").error("Invalid source rule {}: {}",id,e.message)
            }
        }
        rules = parsed.groupBy { it.target }
    }
}
