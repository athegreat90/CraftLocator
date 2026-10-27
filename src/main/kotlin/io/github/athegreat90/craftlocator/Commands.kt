package io.github.athegreat90.craftlocator

import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.arguments.IdentifierArgument
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.github.athegreat90.craftlocator.core.Geometry
import io.github.athegreat90.craftlocator.search.*
import io.github.athegreat90.craftlocator.search.Target
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.event.RegisterCommandsEvent

class CraftLocatorCommands(private val searches: SearchManager,private val sources: SourceResolver,private val recipes: RecipeResolver) {
    fun register(event: RegisterCommandsEvent) {
        val root=literal("craftlocate").executes {help(it.source)}
        root.then(literal("help").executes {help(it.source)})
        root.then(literal("cancel").executes {
            val p=it.source.playerOrException
            Feedback.send(it.source,if(searches.cancel(p.uuid)) "cancelled" else "no_job");1
        })
        root.then(literal("status").executes {
            val j=searches.active(it.source.playerOrException.uuid)
            if(j==null) Feedback.send(it.source,"no_job") else Feedback.send(it.source,"status","${j.type}: "+j.targets.joinToString {t->t.id},
                j.state.name,j.progress,j.blocksExamined,j.chunksExamined.size,(System.nanoTime()-j.created)/1_000_000,j.best.size)
            1
        })
        val find=literal("find")
        for(kind in listOf("block","item")) {
            val id=argument("id",IdentifierArgument.id()).suggests { _,b -> SharedSuggestionProvider.suggestResource(
                if(kind=="block") BuiltInRegistries.BLOCK.keySet() else BuiltInRegistries.ITEM.keySet(),b) }
                .executes {find(it,kind,false,null)}
                .then(argument("radius",IntegerArgumentType.integer(1,128)).executes {find(it,kind,false,IntegerArgumentType.getInteger(it,"radius"))})
            find.then(literal(kind).then(id))
        }
        root.then(find)
        root.then(literal("source").then(argument("id",IdentifierArgument.id())
            .suggests {_,b->SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(),b)}
            .executes {find(it,"item",true,null)}
            .then(argument("radius",IntegerArgumentType.integer(1,128)).executes {find(it,"item",true,IntegerArgumentType.getInteger(it,"radius"))})))
        root.then(literal("craft").then(argument("id",IdentifierArgument.id())
            .suggests {_,b->SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(),b)}
            .executes {craft(it,1,null)}
            .then(argument("quantity",IntegerArgumentType.integer(1,4096)).executes {craft(it,IntegerArgumentType.getInteger(it,"quantity"),null)}
                .then(argument("radius",IntegerArgumentType.integer(1,128)).executes {craft(it,IntegerArgumentType.getInteger(it,"quantity"),IntegerArgumentType.getInteger(it,"radius"))}))))
        event.dispatcher.register(root)
    }
    private fun fail(key: String,vararg args: Any): Nothing = throw SimpleCommandExceptionType(Feedback.text(key,*args)).create()
    private fun radius(requested: Int?): Int {
        val n=requested ?: minOf(Config.defaultRadius.get(),Config.maximumRadius.get())
        if(n !in 1..Config.maximumRadius.get()) fail("radius",Config.maximumRadius.get())
        return Geometry.validateRadius(n,Config.maximumRadius.get())
    }
    private fun id(context: CommandContext<CommandSourceStack>,block: Boolean): String {
        val parsed=IdentifierArgument.getId(context,"id")
        val raw=parsed.toString()
        if(!(if(block) BuiltInRegistries.BLOCK.containsKey(parsed) else BuiltInRegistries.ITEM.containsKey(parsed))) fail("unknown",raw)
        return parsed.toString()
    }
    private fun targets(item: String,includeSources: Boolean): Target {
        val rules=if(includeSources) sources.rules(item) else emptyList()
        return Target(item,rules.flatMap {it.blocks}.toSet(),BuiltInRegistries.ITEM.getValue(Identifier.parse(item)),
            includeSources,blockConditions=rules.flatMap {rule -> rule.blocks.map {it to rule.conditions}}.groupBy {it.first}
                .mapValues {(_, entries) -> entries.map {it.second}.distinct().take(4).joinToString("\n")})
    }
    private fun find(c: CommandContext<CommandSourceStack>,kind: String,source: Boolean,r: Int?): Int {
        val player=c.source.playerOrException
        if(!Config.worldAllowed(c.source)) fail("permission")
        if(source && !Config.sources.get() || !source && kind=="block" && !Config.blocks.get() ||
            !source && kind=="item" && !Config.items.get() && !Config.containers.get()) fail("disabled")
        val target=id(c,kind=="block")
        val query=if(kind=="block") Target(target,setOf(BuiltInRegistries.BLOCK.getValue(Identifier.parse(target)))) else targets(target,source)
        if(source && query.blocks.isEmpty()) Feedback.send(c.source,"no_mapping",target)
        try {searches.start(player,listOf(query),radius(r),if(source) SearchType.SOURCE else if(kind=="block") SearchType.BLOCK else SearchType.ITEM)} catch(e: IllegalArgumentException) {fail("failed",e.message ?: "invalid_search")}
        return 1
    }
    private fun craft(c: CommandContext<CommandSourceStack>,quantity: Int,r: Int?): Int {
        val player=c.source.playerOrException
        if(!Config.craftAllowed(c.source)) fail("permission")
        if(!Config.planner.get()) fail("disabled")
        val target=id(c,false);val rad=radius(r)
        if(!recipes.hasRecipe(target)) fail(if(recipes.hasUnsupported(target)) "unsupported" else "no_recipe",target)
        val plan=try {recipes.plan(player,target,quantity.toLong())} catch(e: IllegalArgumentException) {fail("failed",e.message ?: "planning_limit")}
        Feedback.send(c.source,"crafting",target,quantity,plan.recipe.id)
        for(line in plan.requirements.filter {it.depth==0}) Feedback.send(c.source,"requirement",line.choice.label,line.required,line.available,line.missing)
        for((item,n) in plan.missing) Feedback.send(c.source,"missing",item,n)
        if(plan.missing.isEmpty()) Feedback.send(c.source,"no_missing")
        for((item,n) in plan.surplus) Feedback.send(c.source,"surplus",item,n)
        for(w in plan.warnings) Feedback.send(c.source,"warning",w)
        val queries=plan.missing.keys.map {item ->
            val constraints=plan.unresolved[item].orEmpty()
            targets(item,Config.sources.get()).copy(itemTest={stack -> constraints.any {recipes.accepts(it,stack)}})
        }.toMutableList()
        for(q in queries) if(q.blocks.isEmpty()) Feedback.send(c.source,"no_mapping",q.id)
        if(plan.stations.isNotEmpty()) {
            Feedback.send(c.source,"station_required")
            if(Config.stations.get()) queries.add(Target("minecraft:crafting_table",setOf(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)))
            if(recipes.hasRecipe("minecraft:crafting_table")) {
                val station=recipes.plan(player,"minecraft:crafting_table",1)
                Feedback.send(c.source,"station_recipe",station.requirements.filter {it.depth==0}.joinToString {"${it.choice.label} ×${it.required}"})
            }
        } else Feedback.send(c.source,"no_station")
        if(queries.isNotEmpty()) {
            if(Config.worldAllowed(c.source)) {
                try {searches.start(player,queries,rad,SearchType.CRAFT)} catch(e: IllegalArgumentException) {fail("failed",e.message ?: "invalid_search")}
            } else Feedback.send(c.source,"analysis_only")
        }
        return 1
    }
    private fun help(source: CommandSourceStack): Int {
        for(key in listOf("help.find_block","help.find_item","help.source","help.craft","help.controls","help.limits")) Feedback.send(source,key)
        return 1
    }
}
