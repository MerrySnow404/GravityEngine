package cc.sighs.gravityengine.gravity.collision;

import cc.sighs.gravityengine.api.math.Vec3d;
import cc.sighs.gravityengine.gravity.kinematic.geometry.OrientedBox;
import cc.sighs.gravityengine.math.Quatd;
import cc.sighs.gravityengine.math.geometry.BodyOrientation3d;
import cc.sighs.gravityengine.math.geometry.OrthonormalFrame3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupportMaterialPointTest {
    @Test void finiteFaceClippingDoesNotShiftAnInteriorFootAnchor() {
        var face = new OrientedBox(Vec3d.ZERO, new Vec3d(.5,.5,.5),
                BodyOrientation3d.frame(Quatd.fromAxisAngle(Vec3d.Y,.7)));
        var center = face.localPointToWorld(new Vec3d(.25,1.4,.05));
        var actor = new OrientedBox(center,new Vec3d(.3,.9,.3),OrthonormalFrame3d.IDENTITY);
        var support = SupportRegionPolicy.footFaceIntersection(actor,face,1,1,Vec3d.Y,0);
        assertNotNull(support);
        assertEquals(center.x(),support.witness().x(),1e-12);
        assertEquals(.5,support.witness().y(),1e-12);
        assertEquals(center.z(),support.witness().z(),1e-12);
    }
    @Test void overhangingFootStillUsesARealFiniteWitness() {
        var face = new OrientedBox(Vec3d.ZERO,new Vec3d(.5,.5,.5),OrthonormalFrame3d.IDENTITY);
        var actor = new OrientedBox(new Vec3d(.65,1.4,0),new Vec3d(.3,.9,.3),OrthonormalFrame3d.IDENTITY);
        var support = SupportRegionPolicy.footFaceIntersection(actor,face,1,1,Vec3d.Y,0);
        assertNotNull(support);
        assertTrue(support.witness().x() <= .5);
        assertTrue(support.witness().x() >= .35-GravityGroundProbe.PROBE_DISTANCE);
    }
}
