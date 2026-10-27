package io.github.athegreat90.craftlocator.search

import io.github.athegreat90.craftlocator.Config
import io.github.athegreat90.craftlocator.core.*
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.item.Item
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.capabilities.Capabilities
import java.util.UUID
import kotlin.math.floor

enum class SearchType { BLOCK, ITEM, SOURCE, CRAFT }
enum class JobState { CREATED, QUEUED, RUNNING, COMPLETED, CANCELLED, FAILED }
data class Target(val id: String, val blocks: Set<Block> = emptySet(), val item: Item? = null,
                  val potential: Boolean = false, val conditions: String = "",
                  val itemTest: (net.minecraft.world.item.ItemStack)->Boolean = {true},
                  val blockConditions: Map<Block,String> = emptyMap())
data class Candidate(val position: Point, val kind: String, val label: String, val tie: String,
                     val conditions: String = "")

/** All reads happen in advance(), on the logical server thread. No chunk ticket is created. */
class SearchJob(val owner: UUID, val dimension: ResourceKey<Level>, val origin: Point, val radius: Int,
                val targets: List<Target>, minY: Int, maxY: Int, val allowContainers: Boolean = false,
                val authorizeContainer: (BlockPos) -> Boolean = { false }, val type: SearchType = SearchType.BLOCK) {
    val id: UUID = UUID.randomUUID()
    val created = System.nanoTime()
    var state = JobState.CREATED
    var cancelled = false; private set
    var blocksExamined = 0L; private set
    var skippedSections = 0; private set
    var sectionsExamined = 0; private set
    val chunksExamined = mutableSetOf<Pair<Int,Int>>()
    val best = linkedMapOf<String,Candidate>()
    private val regions = Geometry.regions(origin,radius,minY,maxY)
    private var regionIndex = 0
    private var cursor = -1
    private var itemCandidates: List<Candidate> = emptyList()
    private var entityIndex = 0
    private var slot = -1
    private var containerPosition: BlockPos? = null
    val progress: Int get() = if (regions.isEmpty()) 100 else (regionIndex*100/regions.size).coerceAtMost(100)
    fun cancel() { cancelled = true; state = JobState.CANCELLED; release() }
    fun release() { itemCandidates=emptyList(); containerPosition=null }
    private fun consider(target: Target, candidate: Candidate) {
        val d=origin.squared(candidate.position)
        if(d>radius.toDouble()*radius) return
        val old=best[target.id]
        if(old==null || Geometry.prefer(d,candidate.tie,origin.squared(old.position),old.tie)) best[target.id]=candidate
    }
    fun advance(level: ServerLevel, palette: Boolean = true) {
        check(level.server.isSameThread) { "Search world access outside server thread" }
        if(cancelled || state in setOf(JobState.COMPLETED,JobState.FAILED)) return
        state=JobState.RUNNING
        if(regionIndex>=regions.size) {state=JobState.COMPLETED;release();return}
        val r=regions[regionIndex]
        // Recheck every work unit: chunks may unload between ticks. Never retain chunk references.
        val chunk=level.chunkSource.getChunkNow(r.x,r.z)
        if(chunk==null) {skippedSections++;nextRegion();return}
        chunksExamined.add(r.x to r.z)
        val section=chunk.getSection(level.getSectionIndexFromSectionY(r.y))
        if(cursor == -1) {
            sectionsExamined++
            if(targets.all { t -> best[t.id]?.let { r.minSquared(origin)>origin.squared(it.position) } == true }) {
                nextRegion();return
            }
            if(targets.any { it.item != null } && Config.items.get()) {
                val entities=mutableListOf<ItemEntity>()
                level.getEntities(EntityTypeTest.forClass(ItemEntity::class.java),
                    AABB(r.x*16.0,r.y*16.0,r.z*16.0,r.x*16+16.0,r.y*16+16.0,r.z*16+16.0),
                    {true},entities,4097)
                require(entities.size<=4096) { "entity_limit" }
                itemCandidates=entities.filter { e -> floor(e.x/16).toInt()==r.x && floor(e.y/16).toInt()==r.y && floor(e.z/16).toInt()==r.z }
                    .flatMap { e -> targets.filter { it.item==e.item.item && it.itemTest(e.item) }.map { t ->
                        Candidate(Point(e.x,e.y,e.z),"dropped",t.id,e.uuid.toString())
                    } }
                entityIndex=0
            }
            cursor=0
            if(!allowContainers && (targets.none { it.blocks.isNotEmpty() } ||
                (palette && !section.maybeHas { state -> targets.any { state.block in it.blocks } }))) cursor=4096
            return
        }
        if(entityIndex<itemCandidates.size) {
            val c=itemCandidates[entityIndex++]
            if(level.worldBorder.isWithinBounds(BlockPos.containing(c.position.x,c.position.y,c.position.z)))
                targets.firstOrNull { it.id==c.label }?.let {consider(it,c)}
            return
        }
        val cp=containerPosition
        if(cp!=null && slot>=0) {
            // The exact allowlist is checked on every slot read, not just on discovery.
            if(!allowContainers || !containerAuthorized(level,cp)) {containerPosition=null;slot=-1;return}
            val handler=level.getCapability(Capabilities.Item.BLOCK,cp,null)
            if(handler==null || slot>=handler.size()) {containerPosition=null;slot=-1;return}
            require(handler.size()<=4096) { "container_limit" }
            val resource=handler.getResource(slot)
            val amount=handler.getAmountAsLong(slot++)
            if(amount>0 && !resource.isEmpty) targets.filter { it.item==resource.item && it.itemTest(resource.toStack()) }.forEach { t ->
                consider(t,Candidate(Point(cp.x+0.5,cp.y+0.5,cp.z+0.5),"container",t.id,"${cp.x},${cp.y},${cp.z}"))
            }
            return
        }
        if(cursor>=4096) {nextRegion();return}
        val n=cursor++
        val x=r.x*16+(n and 15);val z=r.z*16+((n shr 4) and 15);val y=r.y*16+(n shr 8)
        val point=Point(x+0.5,y+0.5,z+0.5)
        if(origin.squared(point)>radius.toDouble()*radius) return
        val pos=BlockPos(x,y,z)
        if(!level.isInsideBuildHeight(pos) || !level.worldBorder.isWithinBounds(pos)) return
        blocksExamined++
        val state=section.getBlockState(x and 15,y and 15,z and 15)
        for(t in targets) if(state.block in t.blocks) {
            val blockId=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block).toString()
            consider(t,Candidate(point,if(t.potential) "potential" else "block",blockId,"$x,$y,$z",t.blockConditions[state.block] ?: t.conditions))
        }
        if(allowContainers && state.hasBlockEntity() && containerAuthorized(level,pos)) {
            containerPosition=pos;slot=0
        }
    }
    private fun containerAuthorized(level: ServerLevel,pos: BlockPos): Boolean {
        if(!authorizeContainer(pos)) return false
        val chunk=level.chunkSource.getChunkNow(pos.x shr 4,pos.z shr 4) ?: return false
        val state=chunk.getBlockState(pos)
        if(state.block is net.minecraft.world.level.block.ChestBlock &&
            state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            val partner=pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state))
            // Vanilla's combined chest capability can reach the other half. Authorize and
            // verify it is already loaded before asking for that capability.
            if(!authorizeContainer(partner) || !level.worldBorder.isWithinBounds(partner)) return false
            if(level.chunkSource.getChunkNow(partner.x shr 4,partner.z shr 4)==null) return false
        }
        return true
    }
    private fun nextRegion() {regionIndex++;cursor=-1;itemCandidates=emptyList();entityIndex=0;containerPosition=null;slot=-1}
}
