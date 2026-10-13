package cc.sighs.gravityengine.gravity.collision;

import cc.sighs.gravityengine.api.math.Vec3d;
import cc.sighs.gravityengine.gravity.kinematic.geometry.CharacterCapsule;
import cc.sighs.gravityengine.gravity.kinematic.geometry.OrientedBox;
import cc.sighs.gravityengine.math.geometry.OrthonormalFrame3d;
import cc.sighs.gravityengine.math.geometry.RigidPose;
import cc.sighs.gravityengine.math.geometry.BodyOrientation3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RotatingSupportClearanceTest {
    @Test void subEnteringThresholdCannotAccumulateShallowPenetration() {
        var floor = new EntityObstacle(new DynamicCollisionObstacleSnapshot(1, 0,
                new OrientedBox(Vec3d.ZERO, new Vec3d(3, .5, 3), OrthonormalFrame3d.IDENTITY),
                new RigidMotionSnapshot(new RigidPose(new Vec3d(20, 300, 20), OrthonormalFrame3d.IDENTITY),
                        new Vec3d(0, .00647, 0), new Vec3d(0, .005, 0), 1, 1, 1, 1)));
        var actor = new OrientedBox(new Vec3d(20, 301.4 - 9e-8, 20),
                new Vec3d(.3, .9, .3), OrthonormalFrame3d.IDENTITY);
        var result = RigidObstacleSweep.sweep(actor, new Vec3d(.01, .00647 - 7e-10, 0),
                floor, 0, 1, new ObbQueryContext());
        assertFalse(result.indeterminate());
        assertTrue(result.hasBlockingContacts(), "repeated sub-nanometre entry must not spend the touching band");
    }
    @Test void touchingBandDoesNotAuthorizeCrossingARealGap() {
        var floor = new EntityObstacle(new DynamicCollisionObstacleSnapshot(1, 0,
                new OrientedBox(Vec3d.ZERO, new Vec3d(3, .5, 3), OrthonormalFrame3d.IDENTITY),
                new RigidMotionSnapshot(new RigidPose(new Vec3d(20, 300, 20), OrthonormalFrame3d.IDENTITY),
                        new Vec3d(0, .00647, 0), new Vec3d(0, .005, 0), 1, 1, 1, 1)));
        for (double gap : new double[]{5e-8, -5e-8}) {
            var actor = new OrientedBox(new Vec3d(20, 301.4 + gap, 20),
                    new Vec3d(.3, .9, .3), OrthonormalFrame3d.IDENTITY);
            var result = RigidObstacleSweep.sweep(actor, new Vec3d(.01, .00647 - 4e-8, 0),
                    floor, 0, 1, new ObbQueryContext());
            assertFalse(result.indeterminate());
            if (gap > 0) {
                // This move stays on the clear side; the longer one crosses it.
                assertFalse(result.hasBlockingContacts());
                result = RigidObstacleSweep.sweep(actor, new Vec3d(.01, .00647 - 8e-8, 0),
                        floor, 0, 1, new ObbQueryContext());
            }
            assertFalse(result.indeterminate());
            assertTrue(result.hasBlockingContacts(), "touching classification cannot spend an unused penetration band: " + gap);
        }
    }
    @Test void nativeTinyRotationKeepsARealPositiveGapClear() {
        var parent=new RigidMotionSnapshot(new RigidPose(new Vec3d(20.999996185302734,301.53978801209104,21.000003814697266),
                BodyOrientation3d.frame(new cc.sighs.gravityengine.math.Quatd(-3.5946777446415165e-9,-1.7794253722919388e-20,8.986694361603791e-10,1))),
                0,.006470040215845074,0,-3.020930071291784e-11,-1.4954275309050914e-22,7.55232517822946e-12,447,444,1,1,true);
        var basis=new RigidMotionSnapshot(new RigidPose(new Vec3d(-.4999999962747097,-.5,-.4999999850988388),OrthonormalFrame3d.IDENTITY),
                Vec3d.ZERO,Vec3d.ZERO,447,444,1,1);
        var floor=new EntityObstacle(new DynamicCollisionObstacleSnapshot(1,29,
                new OrientedBox(new Vec3d(-1.5,.5,.5),new Vec3d(.5,.5,.5),OrthonormalFrame3d.IDENTITY),
                new ComposedRigidTrajectory(parent,basis,444,1)));
        var actor=new OrientedBox(new Vec3d(18.999996188560395,302.93978808735596,21.000003827659885),
                new Vec3d(.30000001192092896,.8999999761581421,.30000001192092896),OrthonormalFrame3d.IDENTITY);
        var movement=new Vec3d(-4.418273827517838e-15,.006470040200724725,-1.7673095011326673e-14);
        var result=RigidObstacleSweep.sweep(actor,movement,floor,0,1,new ObbQueryContext());
        assertFalse(result.indeterminate());assertFalse(result.hasBlockingContacts());
        assertTrue(RigidObstacleSweep.sweep(actor,movement.add(0,-.001,0),floor,0,1,new ObbQueryContext()).hasBlockingContacts());
    }
    @Test void nativeAlmostYawingFloorProvesNearContactClearance() {
        var parent = new RigidMotionSnapshot(new RigidPose(new Vec3d(20.999996185302734,302.12783665059595,21.000003814697266),
                BodyOrientation3d.frame(new cc.sighs.gravityengine.math.Quatd(-4.375138974239901e-9,.33415519517357867,2.8637898060011974e-9,.9425180664255235))),
                0,.006465992020650901,0,-2.0984121011525737e-11,-.019965753224312495,2.6661442053331957e-11,482,650,1,1,true);
        var basis = new RigidMotionSnapshot(new RigidPose(new Vec3d(-.4999999962747097,-.5,-.4999999850988388),OrthonormalFrame3d.IDENTITY),
                Vec3d.ZERO,Vec3d.ZERO,482,650,1,1);
        var motion = new ComposedRigidTrajectory(parent,basis,650,1);
        var floor = new EntityObstacle(new DynamicCollisionObstacleSnapshot(1,61,
                new OrientedBox(new Vec3d(2.5,.5,.5),new Vec3d(.5,.5,.5),OrthonormalFrame3d.IDENTITY),motion));
        var actor = new OrientedBox(new Vec3d(22.582715435303086,303.52783673624083,19.72328406244916),
                new Vec3d(.30000001192092896,.8999999761581421,.30000001192092896),OrthonormalFrame3d.IDENTITY);
        var movement = new Vec3d(.012666259529588357,.0032329960148787234,.015863445575462265);
        var result=RigidObstacleSweep.sweep(actor,movement,floor,0,.5,new ObbQueryContext());
        assertFalse(result.indeterminate());assertFalse(result.hasBlockingContacts());
        var entering=RigidObstacleSweep.sweep(actor,movement.add(0,-.001,0),floor,0,.5,new ObbQueryContext());
        assertFalse(entering.indeterminate());assertTrue(entering.hasBlockingContacts());
    }
    @Test void landingOnBarelyRotatingFloorDoesNotConsumeThePenetrationBand() {
        var floor = new EntityObstacle(new DynamicCollisionObstacleSnapshot(1, 0,
                new OrientedBox(Vec3d.ZERO, new Vec3d(3, .5, 3), OrthonormalFrame3d.IDENTITY),
                new RigidMotionSnapshot(new RigidPose(new Vec3d(20, 300, 20), OrthonormalFrame3d.IDENTITY),
                        new Vec3d(0, .00647, 0), new Vec3d(-3e-11, 0, 8e-12), 1, 1, 1, 1)));
        var actor = new OrientedBox(new Vec3d(20, 301.45, 20), new Vec3d(.3, .9, .3), OrthonormalFrame3d.IDENTITY);
        var movement = new Vec3d(0, -.08, 0);
        var result = RigidObstacleSweep.sweep(actor, movement, floor, 0, 1, new ObbQueryContext());
        assertFalse(result.indeterminate());assertTrue(result.hasBlockingContacts());
        double time=result.contacts().get(0).timeOfImpact();
        var at=actor.move(movement.multiply(time));var surface=floor.bodyAt(time);
        assertTrue(at.center().y()-.9 >= surface.center().y()+.5-1e-10,
                "a first impact must not deliberately start inside the allowed touching band");
    }
    @Test void tangentYawClearanceDoesNotExemptEnteringMotion() {
        var box = new OrientedBox(new Vec3d(2, 300.9, 0), new Vec3d(.3, .9, .3), OrthonormalFrame3d.IDENTITY);
        var capsule = new CharacterCapsule(box.center(), new Vec3d(0, 1, 0), .3, .6);
        var floor = new EntityObstacle(new DynamicCollisionObstacleSnapshot(1, 0,
                new OrientedBox(Vec3d.ZERO, new Vec3d(3, 1, 3), OrthonormalFrame3d.IDENTITY),
                new RigidMotionSnapshot(new RigidPose(new Vec3d(0, 299, 0), OrthonormalFrame3d.IDENTITY),
                        Vec3d.ZERO, new Vec3d(0, .1, 0), 1, 1, 1, 1)));
        var tangent = new Vec3d(-.01, 0, -.2);
        var obbClear = RigidObstacleSweep.sweep(box, tangent, floor, 0, 1, new ObbQueryContext());
        var capsuleClear = CapsuleRigidObstacleSweep.sweep(capsule, tangent, floor, 0, 1);
        assertFalse(obbClear.indeterminate());
        assertFalse(obbClear.hasBlockingContacts());
        assertFalse(capsuleClear.indeterminate());
        assertFalse(capsuleClear.hasBlockingContacts());
        var entering = tangent.add(0, -.01, 0);
        assertTrue(RigidObstacleSweep.sweep(box, entering, floor, 0, 1, new ObbQueryContext()).hasBlockingContacts());
        assertTrue(CapsuleRigidObstacleSweep.sweep(capsule, entering, floor, 0, 1).hasBlockingContacts());
    }
    @Test void translatingFloorEndpointTouchIsClearButCrossingStillBlocks() {
        var capsule = new CharacterCapsule(new Vec3d(0, 301, 0), new Vec3d(0, 1, 0), .3, .6);
        var floor = new EntityObstacle(new DynamicCollisionObstacleSnapshot(1, 0,
                new OrientedBox(Vec3d.ZERO, new Vec3d(3, 1, 3), OrthonormalFrame3d.IDENTITY),
                new RigidMotionSnapshot(new RigidPose(new Vec3d(0, 299, 0), OrthonormalFrame3d.IDENTITY),
                        new Vec3d(0, .1, 0), Vec3d.ZERO, 1, 1, 1, 1)));
        var tangent = new Vec3d(.025, 0, .035);
        var clear = CollisionNarrowPhase.sweptContactResult(capsule, tangent, floor);
        assertFalse(clear.indeterminate());
        assertFalse(clear.hasBlockingContacts());
        assertEquals(SweepInitialState.SEPARATED, clear.initialState());
        assertTrue(CollisionNarrowPhase.sweptContactResult(capsule, tangent.add(0, -.02, 0), floor).hasBlockingContacts());
    }
}
