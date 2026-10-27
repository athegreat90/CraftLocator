package io.github.athegreat90.craftlocator.core

import kotlin.math.floor

data class Point(val x: Double, val y: Double, val z: Double) {
    fun squared(other: Point): Double = (x-other.x)*(x-other.x) + (y-other.y)*(y-other.y) + (z-other.z)*(z-other.z)
}
data class Region(val x: Int, val y: Int, val z: Int) {
    fun minSquared(p: Point): Double {
        fun delta(n: Double, low: Int) = maxOf(low.toDouble()-n, 0.0, n-(low+16.0))
        val dx=delta(p.x,x*16);val dy=delta(p.y,y*16);val dz=delta(p.z,z*16)
        return dx*dx+dy*dy+dz*dz
    }
}
object Geometry {
    fun validateRadius(radius: Int, maximum: Int): Int { require(radius in 1..maximum && maximum in 1..128); return radius }
    fun regions(p: Point, radius: Int, minY: Int, maxY: Int): List<Region> {
        validateRadius(radius,128)
        fun section(v: Double) = floor(v/16).toInt()
        return buildList {
            for (x in section(p.x-radius)..section(p.x+radius))
                for (z in section(p.z-radius)..section(p.z+radius))
                    for (y in section(maxOf(p.y-radius,minY.toDouble()))..section(minOf(p.y+radius,maxY.toDouble()))) {
                        val r=Region(x,y,z);if(r.minSquared(p)<=radius.toDouble()*radius)add(r)
                    }
        }.sortedWith(compareBy<Region>{it.minSquared(p)}.thenBy{it.x}.thenBy{it.y}.thenBy{it.z})
    }
    fun prefer(distance: Double, key: String, bestDistance: Double, bestKey: String): Boolean =
        distance < bestDistance || (distance == bestDistance && key < bestKey)
}
