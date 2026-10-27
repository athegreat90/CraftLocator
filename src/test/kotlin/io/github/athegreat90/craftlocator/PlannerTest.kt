package io.github.athegreat90.craftlocator

import io.github.athegreat90.craftlocator.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlannerTest {
    private fun formula(id: String,out: String,count: Long,vararg inputs: String,station: Boolean=false)=
        Formula(id,MaterialStack(out,count=count),inputs.map {Choice(listOf(it))},station)
    private val pick=formula("pick","pickaxe",1,"diamond","diamond","diamond","stick","stick",station=true)
    private val sticks=formula("sticks","stick",4,"planks","planks")
    private val planks=formula("planks","planks",4,"log")
    private fun planner(vararg formulas: Formula)=CraftingPlanner(formulas.toList().groupBy {it.output.item})
    @Test fun `inventory subtracts once and aggregates diamonds`() {
        val p=planner(pick,sticks,planks).plan("pickaxe",1,listOf(MaterialStack("diamond",count=1),MaterialStack("stick",count=2)))
        assertEquals(mapOf("diamond" to 2L),p.missing)
        assertEquals(1L,p.requirements.first {it.choice.label=="diamond"}.available)
    }
    @Test fun `all materials present`() {
        val p=planner(pick).plan("pickaxe",1,listOf(MaterialStack("diamond",count=3),MaterialStack("stick",count=2)))
        assertTrue(p.missing.isEmpty());assertEquals(setOf("minecraft:crafting_table"),p.stations)
    }
    @Test fun `recursive batch outputs leave correct surplus`() {
        val p=planner(pick,sticks,planks).plan("pickaxe",1,listOf(MaterialStack("diamond",count=3),MaterialStack("log",count=1)))
        assertTrue(p.missing.isEmpty());assertEquals(2L,p.surplus["stick"]);assertEquals(2L,p.surplus["planks"])
    }
    @Test fun `quantity multiplies required batches`() {
        val chest=formula("chest","chest",1,*Array(8){"planks"},station=true)
        val p=planner(chest).plan("chest",4,listOf(MaterialStack("planks",count=20)))
        assertEquals(32L,p.requirements.single().required);assertEquals(12L,p.missing["planks"])
    }
    @Test fun `multiple stacks share accounting`() {
        val p=planner(pick).plan("pickaxe",1,listOf(MaterialStack("diamond",count=1),MaterialStack("diamond",count=2),MaterialStack("stick",count=1)))
        assertEquals(mapOf("stick" to 1L),p.missing)
    }
    @Test fun `shared inventory is not counted twice across branches`() {
        val f=formula("target","target",1,"a","b")
        val p=planner(f,formula("a","a",1,"log"),formula("b","b",1,"log")).plan("target",1,listOf(MaterialStack("log",count=1)))
        assertEquals(1L,p.missing["log"])
    }
    @Test fun `surplus is reused across branches`() {
        val f=formula("target","target",1,"a","b")
        val p=planner(f,formula("a","a",1,"stick"),formula("b","b",1,"stick"),sticks,planks)
            .plan("target",1,listOf(MaterialStack("log",count=1)))
        assertTrue(p.missing.isEmpty());assertEquals(2L,p.surplus["stick"])
    }
    @Test fun `output rounding uses ceiling`() {
        val p=planner(sticks).plan("stick",5,emptyList())
        assertEquals(4L,p.missing["planks"]);assertEquals(3L,p.surplus["stick"])
    }
    @Test fun `inventory of final target does not distort planned surplus`() {
        val p=planner(sticks).plan("stick",2,listOf(MaterialStack("stick",count=10)))
        assertEquals(2L,p.surplus["stick"])
    }
    @Test fun `recipe selection prefers inventory coverage`() {
        val a=formula("a","target",1,"stone");val b=formula("b","target",1,"wood")
        assertEquals("b",planner(a,b).plan("target",1,listOf(MaterialStack("wood"))).recipe.id)
    }
    @Test fun `recipe selection breaks ties by id`() {
        assertEquals("a",planner(formula("z","t",1,"b"),formula("a","t",1,"a")).plan("t",1,emptyList()).recipe.id)
    }
    @Test fun `tag alternatives accept mixed variants`() {
        val f=Formula("chest",MaterialStack("chest"),List(8){Choice(listOf("oak","spruce"))},true)
        val p=planner(f).plan("chest",1,listOf(MaterialStack("oak",count=4),MaterialStack("spruce",count=4)))
        assertTrue(p.missing.isEmpty())
    }
    @Test fun `component sensitive ingredient does not accept wrong variant`() {
        val choice=Choice(listOf("gem"),"special"){it.variant=="special"}
        val f=Formula("t",MaterialStack("t"),listOf(choice),false)
        val p=planner(f).plan("t",1,listOf(MaterialStack("gem","ordinary")))
        assertEquals(1L,p.missing["gem"])
    }
    @Test fun `cycles terminate without inflating missing raw materials`() {
        val diamond=formula("unpack","diamond",9,"block")
        val block=formula("pack","block",1,*Array(9){"diamond"})
        val p=planner(pick,diamond,block).plan("pickaxe",1,listOf(MaterialStack("diamond",count=1),MaterialStack("stick",count=2)))
        assertEquals(mapOf("diamond" to 2L),p.missing)
    }
    @Test fun `direct self cycle is left unresolved`() {
        val p=planner(formula("a","a",1,"a")).plan("a",1,emptyList())
        assertEquals(1L,p.missing["a"]);assertTrue("cycle" in p.warnings)
    }
    @Test fun `depth limit is explicit`() {
        val fs=(0..4).map {formula("$it","$it",1,"${it+1}")}
        val p=CraftingPlanner(fs.groupBy{it.output.item},maxDepth=1).plan("0",1,emptyList())
        assertTrue("depth_limit" in p.warnings);assertTrue(p.missing.isNotEmpty())
    }
    @Test fun `node limit fails explicitly`() {
        val fs=listOf(formula("a","a",1,"b"),formula("b","b",1,"c"))
        assertThrows(PlanningLimit::class.java){CraftingPlanner(fs.groupBy{it.output.item},maxNodes=1).plan("a",1,emptyList())}
    }
    @Test fun `unsupported recipe does not create invented formula`() {
        assertThrows(IllegalArgumentException::class.java){planner().plan("machine",1,emptyList())}
    }
    @Test fun `quantity bounds fail`() {
        for(n in listOf(0L,-1L,4097L)) assertThrows(IllegalArgumentException::class.java){planner(sticks).plan("stick",n,emptyList())}
    }
    @Test fun `two by two recipe needs no station`() {assertTrue(planner(planks).plan("planks",1,emptyList()).stations.isEmpty())}
    @Test fun `scarce exact ingredient gets inventory before broad alternative`() {
        val f=Formula("t",MaterialStack("t"),listOf(Choice(listOf("oak","spruce")),Choice(listOf("oak"))),false)
        assertTrue(planner(f).plan("t",1,listOf(MaterialStack("oak"),MaterialStack("spruce"))).missing.isEmpty())
    }
    @Test fun `stocked compressed material breaks a reversible cycle`() {
        val diamond=formula("unpack","diamond",9,"block")
        val block=formula("pack","block",1,*Array(9){"diamond"})
        val p=planner(pick,diamond,block).plan("pickaxe",1,listOf(MaterialStack("block"),MaterialStack("stick",count=2)))
        assertTrue(p.missing.isEmpty());assertEquals(6L,p.surplus["diamond"])
    }
    @Test fun `cyclic alternative does not hide valid intermediate recipe`() {
        val p=planner(formula("t","t",1,"a"),formula("a_cycle","a",1,"b"),
            formula("b","b",1,"a"),formula("z_raw","a",1,"raw")).plan("t",1,emptyList())
        assertEquals(mapOf("raw" to 1L),p.missing)
    }
    @Test fun `acyclic root recipe is preferred to unusable self cycle`() {
        val p=planner(formula("a_cycle","t",1,"t"),formula("z_raw","t",1,"raw")).plan("t",1,emptyList())
        assertEquals("z_raw",p.recipe.id);assertEquals(mapOf("raw" to 1L),p.missing)
    }
    @Test fun `large ingredient tags keep bounded feedback labels`() {
        val choice=Choice((1..8192).map {"example:item$it"})
        assertTrue(choice.label.length<200);assertTrue(choice.label.endsWith("(+8184)"))
        assertEquals(8192,choice.alternatives.size)
    }
}
