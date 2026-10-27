package io.github.athegreat90.craftlocator.core

/** Pure, bounded greedy planning. Quantities are items, not recipe executions. */
data class MaterialStack(val item: String, val variant: String = item, val count: Long = 1)
data class Choice(val alternatives: List<String>, val signature: String = alternatives.joinToString("|"), val accepts: (MaterialStack) -> Boolean = { it.item in alternatives }) {
    init { require(alternatives.isNotEmpty()) }
    val label: String get() = alternatives.take(8).joinToString(" | ") + if(alternatives.size>8) " (+${alternatives.size-8})" else ""
}
data class Formula(val id: String, val output: MaterialStack, val ingredients: List<Choice>, val station: Boolean)
data class Requirement(val choice: Choice, val required: Long, val available: Long, val depth: Int) {
    val missing: Long get() = required - available
}
data class Plan(val recipe: Formula, val requirements: List<Requirement>, val missing: Map<String, Long>,
                val surplus: Map<String, Long>, val unresolved: Map<String,List<String>>, val stations: Set<String>, val warnings: Set<String>)
class PlanningLimit(message: String) : IllegalArgumentException(message)

class CraftingPlanner(private val recipeLookup: (String)->List<Formula>, private val maxDepth: Int = 12,
                      private val maxNodes: Int = 256) {
    constructor(formulas: Map<String,List<Formula>>,maxDepth: Int=12,maxNodes: Int=256):
        this({item -> formulas[item].orEmpty()},maxDepth,maxNodes)
    private class Stock(val stack: MaterialStack, var remaining: Long, val generated: Boolean = false)
    fun plan(target: String, quantity: Long, inventory: List<MaterialStack>): Plan {
        require(quantity in 1..4096)
        val stock = inventory.map { Stock(it, it.count) }.toMutableList()
        val lines = mutableListOf<Requirement>()
        val missing = sortedMapOf<String, Long>()
        val unresolved = mutableMapOf<String,MutableList<String>>()
        val warnings = sortedSetOf<String>()
        val stations = sortedSetOf<String>()
        var nodes = 0
        var evaluations = 0
        val lookup = mutableMapOf<String,List<Formula>>()
        fun candidates(item: String): List<Formula> {
            if (++evaluations > 50000) throw PlanningLimit("recipe_evaluations")
            if (lookup.size >= 4096 && item !in lookup) throw PlanningLimit("recipe_cache")
            return lookup.getOrPut(item) { recipeLookup(item).sortedBy { it.id }.take(32) }
        }
        fun consume(choice: Choice, amount: Long): Long {
            var left = amount
            for (s in stock) {
                if (left == 0L) break
                if (s.remaining > 0 && choice.accepts(s.stack)) {
                    val use = minOf(left, s.remaining); s.remaining -= use; left -= use
                }
            }
            return amount - left
        }
        fun score(f: Formula, amount: Long): Long {
            val batches = (amount + f.output.count - 1) / f.output.count
            val remaining = stock.map { it.remaining }.toMutableList()
            var deficit = 0L
            for (choice in f.ingredients) {
                var need = batches
                for ((i, s) in stock.withIndex()) if (choice.accepts(s.stack)) {
                    val use = minOf(need, remaining[i]); remaining[i] -= use; need -= use
                }
                deficit += need
            }
            return deficit
        }
        fun select(item: String, amount: Long): Formula? = candidates(item).minWithOrNull(compareBy<Formula> { score(it, amount) }.thenBy { it.id })
        fun addMissing(item: String, n: Long, choice: Choice) {
            missing[item] = Math.addExact(missing[item] ?: 0, n)
            unresolved.getOrPut(item) { mutableListOf() }.add(choice.signature)
        }
        fun cyclic(f: Formula, path: Set<String>, budget: IntArray, amount: Long): Boolean {
            if (--budget[0] < 0 || path.size > 64) return true
            val batches=(amount+f.output.count-1)/f.output.count
            return f.ingredients.groupBy {it.signature}.values.any { occurrences ->
                val choice=occurrences.first()
                val need=batches*occurrences.size
                val have=stock.filter {choice.accepts(it.stack)}.sumOf {it.remaining}
                if(have>=need) return@any false
                choice.alternatives.distinct().sorted().take(64).all { item ->
                    if (item in path) true else {
                        val options = candidates(item)
                        options.isNotEmpty() && options.all { cyclic(it, path + item, budget, need-have) }
                    }
                }
            }
        }
        lateinit var expand: (Formula, Long, Int, Set<String>) -> Unit
        expand = { formula, count, depth, path ->
            if (++nodes > maxNodes) throw PlanningLimit("recipe_nodes")
            val batches = (count + formula.output.count - 1) / formula.output.count
            if (formula.station) stations.add("minecraft:crafting_table")
            // Scarce exact ingredients first reduces greedy allocation of overlapping tags.
            for ((choice, occurrences) in formula.ingredients.groupBy { it.signature }.values
                .map { it.first() to it.size }.sortedWith(compareBy<Pair<Choice, Int>>({ it.first.alternatives.size }, { it.first.label }))) {
                val need = Math.multiplyExact(batches, occurrences.toLong())
                if (need > 1_000_000_000L) throw PlanningLimit("recipe_quantity")
                val physical = minOf(need, stock.filter { !it.generated && choice.accepts(it.stack) }.sumOf { it.remaining })
                val have = consume(choice, need)
                lines.add(Requirement(choice, need, physical, depth))
                val rest = need - have
                if (rest > 0) {
                    val alternatives = choice.alternatives.distinct().sorted().take(64)
                    val eligible = alternatives.flatMap { id -> candidates(id).map {id to it} }
                        // Filter constraints/cycles before ranking alternative recipes.
                        .filter { (_, f) -> choice.accepts(f.output) && f.output.item !in path && !cyclic(f, path + f.output.item, intArrayOf(256), rest) }
                    val next = eligible.minWithOrNull(compareBy<Pair<String, Formula>> { score(it.second, rest) }.thenBy { it.second.id })
                    if (depth >= maxDepth || next == null) {
                        if (depth >= maxDepth) warnings.add("depth_limit")
                        if (alternatives.any { it in path }) warnings.add("cycle")
                        addMissing(alternatives.first(), rest, choice)
                    } else {
                        expand(next.second, rest, depth + 1, path + next.first)
                        // Generated surplus is shared with later branches; planned inputs are read-only.
                        val made = consume(choice, rest)
                        check(made == rest) { "Recipe output does not satisfy selected ingredient" }
                    }
                }
            }
            val produced = Math.multiplyExact(batches, formula.output.count)
            stock.add(Stock(formula.output, produced, true))
        }
        val root = candidates(target).filterNot {cyclic(it,setOf(target),intArrayOf(256),quantity)}
            .minWithOrNull(compareBy<Formula>{score(it,quantity)}.thenBy{it.id})
            ?: select(target, quantity) ?: throw IllegalArgumentException("no_recipe")
        expand(root, quantity, 0, setOf(target))
        stock.last().remaining -= quantity
        val surplus = stock.filter { it.generated && it.remaining > 0 }.groupBy { it.stack.item }
            .mapValues { (_, v) -> v.sumOf { it.remaining } }
        return Plan(root, lines, missing, surplus, unresolved, stations, warnings)
    }
}
