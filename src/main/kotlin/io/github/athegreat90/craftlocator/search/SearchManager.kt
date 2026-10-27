package io.github.athegreat90.craftlocator.search

import io.github.athegreat90.craftlocator.Config
import io.github.athegreat90.craftlocator.Feedback
import io.github.athegreat90.craftlocator.core.Point
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.ArrayDeque
import java.util.UUID

class SearchManager {
    private val jobs=linkedMapOf<UUID,SearchJob>()
    private val queue=ArrayDeque<UUID>()
    private var tick=0L
    fun active(uuid: UUID)=jobs[uuid]
    fun cancel(uuid: UUID): Boolean {
        val job=jobs.remove(uuid) ?: return false
        job.cancel();queue.remove(uuid);return true
    }
    fun clear() {jobs.values.forEach {it.cancel()};jobs.clear();queue.clear()}
    fun start(player: ServerPlayer,targets: List<Target>,radius: Int,type: SearchType=SearchType.BLOCK) {
        require(targets.size in 1..64) {"target_limit"}
        val replaced=cancel(player.uuid)
        if(replaced) Feedback.send(player,"cancelled")
        require(jobs.size<Config.concurrent.get()) {"busy"}
        val level=player.level()
        val allowedContainers=Config.containers.get() && Config.operator(player.createCommandSourceStack())
        // Snapshot explicit authorization strings, but feature and operator permission are rechecked by tick().
        val allowlist=Config.authorizedContainers.get().toSet()
        val dimension=level.dimension().identifier().toString()
        val authorize: (BlockPos)->Boolean = {p -> "$dimension@${p.x},${p.y},${p.z}" in allowlist &&
            "$dimension@${p.x},${p.y},${p.z}" in Config.authorizedContainers.get()}
        val job=SearchJob(player.uuid,level.dimension(),Point(player.x,player.y,player.z),radius,targets,
            level.minY,level.maxY,allowedContainers,authorize,type)
        job.state=JobState.QUEUED;jobs[player.uuid]=job;queue.add(player.uuid)
        Feedback.send(player,"searching",radius)
    }
    fun tick(server: MinecraftServer) {
        tick++
        val deadline=System.nanoTime()+Config.millisBudget.get()*1_000_000L
        var work=0
        while(queue.isNotEmpty() && work<Config.blockBudget.get() && System.nanoTime()<deadline) {
            val owner=queue.removeFirst();val job=jobs[owner] ?: continue
            val player=server.playerList.getPlayer(owner)
            val source=player?.createCommandSourceStack()
            if(player==null || !player.isAlive || player.level().dimension()!=job.dimension ||
                source==null || !Config.worldAllowed(source) || !when(job.type) {
                    SearchType.BLOCK -> Config.blocks.get()
                    SearchType.ITEM -> Config.items.get() || Config.containers.get()
                    SearchType.SOURCE -> Config.sources.get()
                    SearchType.CRAFT -> Config.planner.get() && Config.craftAllowed(source)
                } ||
                (job.allowContainers && (!Config.containers.get() || !Config.operator(source))) ||
                System.nanoTime()-job.created>Config.timeout.get()*1_000_000_000L) {
                cancel(owner);if(player!=null) Feedback.send(player,"cancelled");continue
            }
            try {
                job.advance(player.level(),Config.palette.get());work++
                if(job.state==JobState.COMPLETED) {
                    // Permission is checked again immediately before disclosing coordinates.
                    if(Config.worldAllowed(source)) Feedback.result(player,job)
                    jobs.remove(owner);job.release()
                } else {
                    queue.addLast(owner)
                    if(Config.progress.get() && tick%100L==0L && work<=jobs.size) Feedback.send(player,"progress",job.progress)
                }
            } catch(e: Exception) {
                job.state=JobState.FAILED;job.release();jobs.remove(owner)
                LoggerFactory.getLogger("CraftLocator").error("Search {} failed for {} in {}",job.id,owner,job.dimension,e)
                Feedback.send(player,"failed",e.message?.takeIf {it in setOf("entity_limit","container_limit")} ?: "internal_error")
            }
        }
    }
}
