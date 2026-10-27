package io.github.athegreat90.craftlocator

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import io.github.athegreat90.craftlocator.search.*
import java.util.Locale
import kotlin.math.floor
import kotlin.math.sqrt

object Feedback {
    private val english: Map<String,String> = (Feedback::class.java.getResourceAsStream("/assets/craftlocator/lang/en_us.json") ?: error("Missing bundled English translations")).use {
        com.google.gson.JsonParser.parseReader(it.reader()).asJsonObject.entrySet().associate { e -> e.key to e.value.asString }
    }
    private fun translated(key: String,vararg args: Any): MutableComponent =
        Component.translatableWithFallback(key,english[key] ?: key,*args)
    fun text(key: String,vararg args: Any): MutableComponent = translated("craftlocator.$key",*args)
    fun send(source: CommandSourceStack,key: String,vararg args: Any) {source.sendSystemMessage(text(key,*args))}
    fun send(player: ServerPlayer,key: String,vararg args: Any) {player.sendSystemMessage(text(key,*args))}
    fun result(player: ServerPlayer,job: SearchJob) {
        send(player,"complete",job.radius,job.blocksExamined,job.sectionsExamined,job.chunksExamined.size,job.skippedSections)
        for(target in job.targets) {
            val c=job.best[target.id]
            if(c==null) {send(player,if(target.id=="minecraft:crafting_table") "station_missing" else "not_found",target.id);continue}
            send(player,"found",target.id,c.label,text("kind.${c.kind}"))
            val coords="${floor(c.position.x).toInt()}, ${floor(c.position.y).toInt()}, ${floor(c.position.z).toInt()}"
            val dimension=job.dimension.identifier().toString()
            val component=text("position",coords,dimension,String.format(Locale.ROOT,"%.1f",sqrt(job.origin.squared(c.position))))
                .withStyle { it.withClickEvent(ClickEvent.CopyToClipboard(coords))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("$coords — $dimension"))) }
            player.sendSystemMessage(component)
            if(c.conditions.isNotEmpty()) for (condition in c.conditions.split("\n")) player.sendSystemMessage(translated(condition))
        }
    }
}
