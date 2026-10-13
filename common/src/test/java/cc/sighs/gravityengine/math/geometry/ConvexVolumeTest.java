package cc.sighs.gravityengine.math.geometry;

import cc.sighs.gravityengine.api.math.Vec3d;
import cc.sighs.gravityengine.gravity.collision.CollisionNarrowPhase;
import cc.sighs.gravityengine.gravity.kinematic.geometry.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConvexVolumeTest {
    private static OrientedBox box(Vec3d center,double half) {return new OrientedBox(center,half,OrthonormalFrame3d.IDENTITY);}
    @Test void subtractionPreservesPartialFluidAndRejectsFullyOccludedVolume() {
        for(double offset:new double[]{0,20_000_000}) {
            var source=box(new Vec3d(offset,0,0),.5);var volume=ConvexVolume.box(source);
            assertEquals(1,volume.volume(),1e-12);
            assertTrue(volume.subtract(source).isEmpty());
            var pieces=volume.subtract(box(new Vec3d(offset+.5,0,0),.5));
            assertEquals(.5,pieces.stream().mapToDouble(ConvexVolume::volume).sum(),1e-12);
            assertTrue(pieces.stream().anyMatch(p->p.contains(new Vec3d(offset-.25,0,0))));
            assertFalse(pieces.stream().anyMatch(p->p.contains(new Vec3d(offset+.25,0,0))));
        }
    }
    @Test void rotatedCutHasExactVolumeAndMatchesPointMembership() {
        double c=Math.sqrt(.5);
        var rotation=new OrthonormalFrame3d(c,c,0,-c,c,0,0,0,1);
        var source=ConvexVolume.box(box(Vec3d.ZERO,1));
        var hole=new OrientedBox(Vec3d.ZERO,.5,rotation);
        var pieces=source.subtract(hole);
        assertEquals(7,pieces.stream().mapToDouble(ConvexVolume::volume).sum(),1e-10);
        var random=new Random(426);
        for(int i=0;i<2000;i++) {
            var point=new Vec3d(random.nextDouble()*2-1,random.nextDouble()*2-1,random.nextDouble()*2-1);
            var local=hole.worldPointToLocal(point);
            boolean inside=Math.abs(local.x())<.5&&Math.abs(local.y())<.5&&Math.abs(local.z())<.5;
            assertEquals(!inside,pieces.stream().anyMatch(p->p.contains(point)));
        }
    }
    @Test void capsuleOccupancyMatchesExistingExactBoxKernel() {
        var box=box(Vec3d.ZERO,.5);var volume=ConvexVolume.box(box);var random=new Random(815);
        for(int i=0;i<2000;i++) {
            var capsule=new CharacterCapsule(new Vec3d(random.nextDouble()*4-2,random.nextDouble()*4-2,random.nextDouble()*4-2),
                    new Vec3d(random.nextDouble()+.1,random.nextDouble()+.1,random.nextDouble()+.1),.3,.6);
            assertEquals(CollisionNarrowPhase.intersects(capsule,box),volume.intersects(capsule));
        }
    }
    @Test void clippedGeometryDoesNotUseItsEnclosingBoxAsOccupancy() {
        var pieces=ConvexVolume.box(box(Vec3d.ZERO,1)).subtract(box(Vec3d.ZERO,.5));
        var center=new CharacterCapsule(Vec3d.ZERO,new Vec3d(0,1,0),.2,.1);
        assertFalse(pieces.stream().anyMatch(p->p.intersects(center)));
        assertFalse(pieces.stream().anyMatch(p->p.intersects(box(Vec3d.ZERO,.2))));
        var edge=new CharacterCapsule(new Vec3d(.7,0,0),new Vec3d(0,1,0),.1,.1);
        assertTrue(pieces.stream().anyMatch(p->p.intersects(edge)));
    }
}
