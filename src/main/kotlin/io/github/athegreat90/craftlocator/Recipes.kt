package io.github.athegreat90.craftlocator

import io.github.athegreat90.craftlocator.core.*
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.*
import net.minecraft.world.item.crafting.display.SlotDisplayContext
import org.slf4j.LoggerFactory

class RecipeResolver {
    private data class Definition(val id: String,val output: ItemStack,val ingredients: List<Ingredient>,val station: Boolean)
    private var index: Map<String,List<Definition>> = emptyMap()
    private var unsupported: Set<String> = emptySet()
    private var predicates: Map<String,Ingredient> = emptyMap()
    fun accepts(signature: String,stack: ItemStack)=predicates[signature]?.test(stack) ?: false
    fun clear() {index=emptyMap();unsupported=emptySet();predicates=emptyMap()}
    fun reload(level: ServerLevel) {
        val definitions=mutableListOf<Definition>()
        val unsupportedOutputs=mutableSetOf<String>()
        val recipes=level.server.recipeManager.recipes
        require(recipes.size<=50000) {"CraftLocator recipe index exceeds 50000 entries"}
        val context=SlotDisplayContext.fromLevel(level)
        for(holder in recipes.sortedBy {it.id().toString()}) {
            val recipe=holder.value()
            try {
                if(recipe is ShapedRecipe || recipe is ShapelessRecipe) {
                    val crafting=recipe as CraftingRecipe
                    val output=crafting.assemble(CraftingInput.EMPTY)
                    val ingredients=if(recipe is ShapedRecipe) recipe.ingredients.mapNotNull { it.orElse(null) }
                        else recipe.placementInfo().ingredients()
                    if(!output.isEmpty && ingredients.isNotEmpty() && ingredients.size<=9) {
                        val station=if(recipe is ShapedRecipe) recipe.width>2 || recipe.height>2 else ingredients.size>4
                        definitions.add(Definition(holder.id().identifier().toString(),output.copy(),ingredients,station))
                    }
                } else {
                    for(display in recipe.display().take(8)) for(stack in display.result().resolveForStacks(context).take(64))
                        if(!stack.isEmpty) unsupportedOutputs.add(BuiltInRegistries.ITEM.getKey(stack.item).toString())
                }
            } catch(e: Exception) {
                LoggerFactory.getLogger("CraftLocator").warn("Cannot analyze recipe {}: {}",holder.id(),e.message)
            }
        }
        index=definitions.groupBy {BuiltInRegistries.ITEM.getKey(it.output.item).toString()}
        unsupported=unsupportedOutputs
        predicates=definitions.flatMap { d -> d.ingredients.mapIndexed { i, ingredient -> "${d.id}#${d.ingredients.indexOf(ingredient)}" to ingredient } }.toMap()
    }
    fun hasUnsupported(item: String)=item in unsupported
    fun hasRecipe(item: String)=item in index
    fun plan(player: ServerPlayer,item: String,quantity: Long): Plan {
        val variants=mutableMapOf<String,ItemStack>()
        val inventory=buildList {
            val inv=player.inventory
            for(i in 0 until inv.containerSize) {
                val stack=inv.getItem(i)
                if(!stack.isEmpty && net.minecraft.world.entity.player.Inventory.isUsableForCrafting(stack)) {
                    val key="inventory:$i";variants[key]=stack.copy()
                    add(MaterialStack(BuiltInRegistries.ITEM.getKey(stack.item).toString(),key,stack.count.toLong()))
                }
            }
        }
        val formulas: (String)->List<Formula> = {key ->
            index[key].orEmpty().map {d ->
                val outputKey="recipe:${d.id}"
                variants[outputKey]=d.output
                val choices=d.ingredients.map { ingredient ->
                    val alts=ingredient.items().map {BuiltInRegistries.ITEM.getKey(it.value()).toString()}.sorted().toList()
                    require(alts.isNotEmpty() && alts.size<=8192) {"ingredient_limit"}
                    // Keep custom component predicates distinct; the actual predicate evaluates each stack.
                    Choice(alts,"${d.id}#${d.ingredients.indexOf(ingredient)}") { material ->
                        variants[material.variant]?.let {ingredient.test(it)} ?: false
                    }
                }
                Formula(d.id,MaterialStack(key,outputKey,d.output.count.toLong()),choices,d.station)
            }
        }
        return CraftingPlanner(formulas).plan(item,quantity,inventory)
    }
}
