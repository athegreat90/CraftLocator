package io.github.athegreat90.craftlocator

import io.github.athegreat90.craftlocator.core.Point
import io.github.athegreat90.craftlocator.search.*
import io.github.athegreat90.craftlocator.search.Target
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.*
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.NameAndId
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.UUID
import java.util.function.Consumer

/** Enabled only in the explicit GameTest run, never during ordinary gameplay. */
object CraftLocatorGameTests {
    private val tests=linkedMapOf<String,(GameTestHelper)->Unit>()
    fun register(bus: IEventBus) {
        tests["nearest_ore"]={h ->
            h.setBlock(1,1,1,Blocks.DIAMOND_ORE);h.setBlock(6,1,1,Blocks.DIAMOND_ORE)
            val origin=h.absolutePos(BlockPos(3,1,1))
            val job=job(h,origin,listOf(Target("ore",setOf(Blocks.DIAMOND_ORE))))
            finish(h,job) {check(it.best["ore"]?.position?.x==h.absolutePos(BlockPos(1,1,1)).x+0.5)}
        }
        tests["unloaded_chunks"]={h ->
            val level=h.level;val pos=BlockPos(1_000_000,64,1_000_000)
            check(level.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)==null)
            val job=job(h,pos,listOf(Target("ore",setOf(Blocks.DIAMOND_ORE))),4)
            finish(h,job) {check(it.best.isEmpty());check(it.skippedSections>0)
                check(level.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4)==null)}
        }
        tests["dropped_item"]={h ->
            h.setBlock(1,1,1,Blocks.DIAMOND_ORE)
            val item=h.spawnItem(Items.DIAMOND,2.5f,2.0f,2.5f);item.setNoGravity(true)
            val job=job(h,h.absolutePos(BlockPos(3,2,2)),listOf(Target("diamond",item=Items.DIAMOND)))
            finish(h,job) {check(it.best["diamond"]?.kind=="dropped")}
        }
        tests["crafting_table"]={h ->
            h.setBlock(2,1,2,Blocks.CRAFTING_TABLE)
            val job=job(h,h.absolutePos(BlockPos(3,1,3)),listOf(Target("table",setOf(Blocks.CRAFTING_TABLE))))
            finish(h,job) {check(it.best["table"]?.label=="minecraft:crafting_table")}
        }
        tests["potential_is_not_item"]={h ->
            h.setBlock(1,1,1,Blocks.DIAMOND_ORE)
            val job=job(h,h.absolutePos(BlockPos(3,1,1)),listOf(Target("diamond",item=Items.DIAMOND)))
            finish(h,job) {check(it.best.isEmpty())}
        }
        tests["container_privacy"]={h ->
            h.setBlock(2,1,2,Blocks.CHEST)
            (h.level.getBlockEntity(h.absolutePos(BlockPos(2,1,2))) as ChestBlockEntity).setItem(0,ItemStack(Items.DIAMOND,3))
            val pos=h.absolutePos(BlockPos(3,1,2))
            val job=SearchJob(UUID.randomUUID(),h.level.dimension(),Point(pos.x+0.5,pos.y+0.5,pos.z+0.5),4,
                listOf(Target("diamond",item=Items.DIAMOND)),h.level.minY,h.level.maxY,true,{false})
            finish(h,job) {check(it.best.isEmpty())}
        }
        tests["authorized_capability"]={h ->
            h.setBlock(2,1,2,Blocks.CHEST)
            val chest=h.absolutePos(BlockPos(2,1,2))
            (h.level.getBlockEntity(chest) as ChestBlockEntity).setItem(0,ItemStack(Items.DIAMOND,3))
            val pos=h.absolutePos(BlockPos(3,1,2))
            val job=SearchJob(UUID.randomUUID(),h.level.dimension(),Point(pos.x+0.5,pos.y+0.5,pos.z+0.5),4,
                listOf(Target("diamond",item=Items.DIAMOND)),h.level.minY,h.level.maxY,true,{it==chest})
            finish(h,job) {check(it.best["diamond"]?.kind=="container")}
        }
        tests["double_chest_requires_both_halves"]={h ->
            val left=h.absolutePos(BlockPos(2,1,2));val right=h.absolutePos(BlockPos(3,1,2))
            h.setBlock(2,1,2,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,net.minecraft.core.Direction.NORTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT))
            h.setBlock(3,1,2,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,net.minecraft.core.Direction.NORTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT))
            (h.level.getBlockEntity(right) as ChestBlockEntity).setItem(0,ItemStack(Items.DIAMOND))
            val pos=h.absolutePos(BlockPos(2,2,2))
            val query=SearchJob(UUID.randomUUID(),h.level.dimension(),Point(pos.x+0.5,pos.y+0.5,pos.z+0.5),4,
                listOf(Target("diamond",item=Items.DIAMOND)),h.level.minY,h.level.maxY,true,{it==left})
            finish(h,query) {check(it.best.isEmpty())}
        }
        tests["recipes_and_inventory"]={h -> withPlayer(h) {p ->
            p.inventory.clearContent();p.inventory.setItem(0,ItemStack(Items.DIAMOND,1));p.inventory.setItem(1,ItemStack(Items.STICK,2))
            val r=RecipeResolver();r.reload(h.level)
            val plan=r.plan(p,"minecraft:diamond_pickaxe",1)
            check(plan.missing==mapOf("minecraft:diamond" to 2L));check(p.inventory.getItem(0).count==1)
            check(r.hasUnsupported("minecraft:iron_ingot"));h.succeed()
        }}
        tests["commands_and_permissions"]={h -> withPlayer(h) {p ->
            val dispatcher=h.level.server.commands.dispatcher
            check(dispatcher.root.getChild("craftlocate")!=null)
            val source=p.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS)
            check(!Config.worldAllowed(source));check(Config.craftAllowed(source))
            val error=runCatching {dispatcher.execute("craftlocate find block minecraft:diamond_ore 4",source)}.exceptionOrNull()
            check(error is com.mojang.brigadier.exceptions.CommandSyntaxException && error.rawMessage.string.contains("Permission denied"))
            val unknown=runCatching {dispatcher.execute("craftlocate craft nonexistent:no_item",source)}.exceptionOrNull()
            check(unknown is com.mojang.brigadier.exceptions.CommandSyntaxException)
            check(dispatcher.execute("craftlocate craft minecraft:diamond_pickaxe 1 4",source)==1)
            h.succeed()
        }}
        tests["cancel_and_disconnect"]={h -> withPlayer(h) {p ->
            val manager=SearchManager();manager.start(p,listOf(Target("ore",setOf(Blocks.DIAMOND_ORE))),64)
            val active=manager.active(p.uuid) ?: error("Missing job")
            check(manager.cancel(p.uuid));check(active.cancelled);check(active.state==JobState.CANCELLED)
            manager.start(p,listOf(Target("ore",setOf(Blocks.DIAMOND_ORE))),64)
            h.level.server.playerList.remove(p)
            manager.tick(h.level.server);check(manager.active(p.uuid)==null);h.succeed()
        }}
        tests["shared_budget_and_fairness"]={h -> withPlayer(h) {p1 -> withPlayer(h) {p2 ->
            val server=h.level.server
            for(p in listOf(p1,p2)) server.playerList.op(NameAndId(p.uuid,p.gameProfile.name),java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER),java.util.Optional.empty())
            try {
                val m=SearchManager();val targets=listOf(Target("air",setOf(Blocks.AIR)))
                m.start(p1,targets,64);m.start(p2,targets,64)
                val a=m.active(p1.uuid) ?: error("job1");val b=m.active(p2.uuid) ?: error("job2")
                var ticks=0
                do {m.tick(server);ticks++} while((a.state==JobState.QUEUED || b.state==JobState.QUEUED) && ticks<8)
                check(a.state==JobState.RUNNING && b.state==JobState.RUNNING) {"Job states: ${a.state}, ${b.state}; operator=${Config.operator(p1.createCommandSourceStack())}, ${Config.operator(p2.createCommandSourceStack())}"}
                check(a.blocksExamined+b.blocksExamined<=Config.blockBudget.get().toLong()*ticks)
                check(kotlin.math.abs(a.blocksExamined-b.blocksExamined)<=1) {"Work imbalance: ${a.blocksExamined}, ${b.blocksExamined}"}
                server.playerList.deop(NameAndId(p1.uuid,p1.gameProfile.name))
                m.tick(server);check(m.active(p1.uuid)==null) {"Revoked operator job not cancelled"}
                m.clear();check(m.active(p2.uuid)==null)
                h.succeed()
            } finally {for(p in listOf(p1,p2)) server.playerList.deop(NameAndId(p.uuid,p.gameProfile.name))}
        }}}
        tests["reloadable_source_rules"]={h ->
            val r=SourceResolver();r.reload(h.level.server)
            check(r.rules("minecraft:diamond").any {Blocks.DIAMOND_ORE in it.blocks})
            r.clear();check(r.rules("minecraft:diamond").isEmpty());r.reload(h.level.server)
            check(r.rules("minecraft:iron_ingot").isNotEmpty());h.succeed()
        }
        tests["modded_standard_recipe"]={h -> withPlayer(h) {p ->
            p.inventory.clearContent();p.inventory.setItem(0,ItemStack(Items.DIAMOND,1))
            val r=RecipeResolver();r.reload(h.level)
            val result=r.plan(p,"craftlocator:test_crystal",2)
            check(result.missing.isEmpty());check(result.surplus.isEmpty());check(!result.recipe.station)
            val dispatcher=h.level.server.commands.dispatcher
            val parsed=dispatcher.parse("craftlocate find item craftlocator:",p.createCommandSourceStack())
            val suggestions=dispatcher.getCompletionSuggestions(parsed).getNow(null) ?: error("Suggestions not completed")
            check(suggestions.list.any {it.text=="craftlocator:test_crystal"})
            h.succeed()
        }}
        tests["item_components"]={h ->
            val a=h.spawnItem(Items.DIAMOND,2f,2f,2f);a.setNoGravity(true)
            val b=h.spawnItem(Items.DIAMOND,5f,2f,2f);b.setNoGravity(true)
            b.item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("reserved"))
            val target=Target("reserved",item=Items.DIAMOND,itemTest={it.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)?.string=="reserved"})
            val query=job(h,h.absolutePos(BlockPos(2,2,2)),listOf(target))
            finish(h,query) {check(it.best["reserved"]?.tie==b.uuid.toString())}
        }
        tests["world_command_execution"]={h -> withPlayer(h) {p ->
            val server=h.level.server
            server.playerList.op(NameAndId(p.uuid,p.gameProfile.name),java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER),java.util.Optional.empty())
            try {
                val dispatcher=server.commands.dispatcher
                val source=p.createCommandSourceStack()
                for(command in listOf("help","find block minecraft:diamond_ore 4","status","find item minecraft:diamond 4","source minecraft:diamond 4","cancel"))
                    check(dispatcher.execute("craftlocate $command",source)==1) {"Command failed: $command"}
                h.succeed()
            } finally {server.playerList.deop(NameAndId(p.uuid,p.gameProfile.name))}
        }}
        tests["dimension_and_death_cancel"]={h -> withPlayer(h) {p ->
            val server=h.level.server
            server.playerList.op(NameAndId(p.uuid,p.gameProfile.name),java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER),java.util.Optional.empty())
            val m=SearchManager()
            try {
                val targets=listOf(Target("ore",setOf(Blocks.DIAMOND_ORE)))
                m.start(p,targets,64)
                val original=p.level()
                p.setServerLevel(server.getLevel(net.minecraft.world.level.Level.NETHER) ?: error("No nether"))
                m.tick(server);check(m.active(p.uuid)==null)
                p.setServerLevel(original)
                m.start(p,targets,64);p.health=0f
                m.tick(server);check(m.active(p.uuid)==null);h.succeed()
            } finally {m.clear();server.playerList.deop(NameAndId(p.uuid,p.gameProfile.name))}
        }}
        val items=DeferredRegister.createItems("craftlocator")
        items.registerSimpleItem("test_crystal")
        items.register(bus)
        val functions=DeferredRegister.create(Registries.TEST_FUNCTION,"craftlocator")
        for((name,body) in tests) functions.register(name) { -> Consumer<GameTestHelper> {h -> body(h)} }
        functions.register(bus)
        bus.addListener {event: RegisterGameTestsEvent ->
            val env=event.registerEnvironment(Identifier.parse("craftlocator:tests"),TestEnvironmentDefinition.AllOf())
            for(name in tests.keys) event.registerTest(Identifier.parse("craftlocator:$name"),
                FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,Identifier.parse("craftlocator:$name")),
                    TestData(env,Identifier.parse("craftlocator:empty"),400,0,true)))
        }
    }
    private fun job(h: GameTestHelper,pos: BlockPos,targets: List<Target>,radius: Int=6)=
        SearchJob(UUID.randomUUID(),h.level.dimension(),Point(pos.x+0.5,pos.y+0.5,pos.z+0.5),radius,targets,h.level.minY,h.level.maxY)
    private fun finish(h: GameTestHelper,job: SearchJob,assertion: (SearchJob)->Unit) {
        // Test work is incremental too. No production code is allowed an unlimited full-radius loop.
        fun step() {
            try {
                repeat(1024) {if(job.state!=JobState.COMPLETED) job.advance(h.level)}
                if(job.state==JobState.COMPLETED) {assertion(job);h.succeed()} else h.runAfterDelay(1) {step()}
            } catch(e: Exception) {h.fail(e.message ?: e.javaClass.simpleName)}
        }
        step()
    }
    private fun withPlayer(h: GameTestHelper,body: (ServerPlayer)->Unit) {
        val p=h.makeMockServerPlayerInLevel()
        try {
            val pos=h.absolutePos(BlockPos(3,2,3));p.teleportTo(pos.x+0.5,pos.y+0.5,pos.z+0.5)
            body(p)
        } finally {if(h.level.server.playerList.getPlayer(p.uuid)!=null) h.level.server.playerList.remove(p)}
    }
}
