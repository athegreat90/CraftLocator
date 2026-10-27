package io.github.athegreat90.craftlocator

import io.github.athegreat90.craftlocator.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GeometryTest {
    @Test fun `squared Euclidean distance`() {assertEquals(169.0,Point(0.0,0.0,0.0).squared(Point(3.0,4.0,12.0)))}
    @Test fun `nearest wins regardless of enumeration`() {
        val c=listOf(9.0 to "a",1.0 to "z",4.0 to "b")
        var best=Double.POSITIVE_INFINITY to ""
        for(p in c) if(Geometry.prefer(p.first,p.second,best.first,best.second)) best=p
        assertEquals(1.0 to "z",best)
    }
    @Test fun `ties are deterministic`() {assertTrue(Geometry.prefer(1.0,"a",1.0,"b"));assertFalse(Geometry.prefer(1.0,"b",1.0,"a"))}
    @Test fun `section lower bound is conservative`() {
        val p=Point(20.0,20.0,20.0);assertEquals(48.0,Region(0,0,0).minSquared(p))
        assertEquals(0.0,Region(1,1,1).minSquared(p))
    }
    @Test fun `negative coordinates use floor division`() {
        val rs=Geometry.regions(Point(-0.5,-0.5,-0.5),1,-64,319)
        assertTrue(Region(-1,-1,-1) in rs);assertTrue(Region(0,0,0) in rs)
    }
    @Test fun `regions never duplicate`() {
        val rs=Geometry.regions(Point(0.0,64.0,0.0),128,-64,319)
        assertEquals(rs.size,rs.toSet().size)
    }
    @Test fun `regions sorted by lower bound`() {
        val p=Point(0.5,64.0,0.5);val rs=Geometry.regions(p,64,-64,319)
        assertTrue(rs.zipWithNext().all{it.first.minSquared(p)<=it.second.minSquared(p)})
    }
    @Test fun `vertical build bounds respected`() {
        val rs=Geometry.regions(Point(0.0,-64.0,0.0),128,-64,319)
        assertTrue(rs.all{it.y>=-4 && it.y<=19})
    }
    @Test fun `radius validation`() {
        for(n in listOf(0,-1,129)) assertThrows(IllegalArgumentException::class.java){Geometry.validateRadius(n,128)}
        assertThrows(IllegalArgumentException::class.java){Geometry.validateRadius(65,64)}
        assertEquals(1,Geometry.validateRadius(1,128));assertEquals(128,Geometry.validateRadius(128,128))
    }
}
