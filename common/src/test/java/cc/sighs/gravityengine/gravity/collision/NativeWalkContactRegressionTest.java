package cc.sighs.gravityengine.gravity.collision;
import cc.sighs.gravityengine.api.math.Vec3d;
import cc.sighs.gravityengine.gravity.GravityFrame;
import cc.sighs.gravityengine.gravity.kinematic.*;
import cc.sighs.gravityengine.gravity.kinematic.geometry.OrientedBox;
import cc.sighs.gravityengine.math.Quatd;
import cc.sighs.gravityengine.math.geometry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeWalkContactRegressionTest {
 @Test void nativeAlmostYawingFloorAllowsContinuousTangentWalk() {
  var q0=new Quatd(-6.897042435992935E-9,.15421371532344333,2.6146540810277163E-9,.9880375144730791);
  var q1=new Quatd(-6.905663676344233E-9,.15668057568932492,2.6342431290125476E-9,.987649329064553);
  var delta=q1.multiply(q0.conjugate()); double len=Math.sqrt(delta.x()*delta.x()+delta.y()*delta.y()+delta.z()*delta.z());
  var angular=new Vec3d(delta.x(),delta.y(),delta.z()).multiply(2*Math.atan2(len,delta.w())/len);
  var p0=new Vec3d(21.610254952694017,302.5148925693875,20.676154641280686);
  var p1=new Vec3d(21.628626740412184,302.5213622959347,20.679298103529803);
  var move=p1.subtract(p0);
  var motion=new RigidMotionSnapshot(new RigidPose(p0,BodyOrientation3d.frame(q0)),move.x(),move.y(),move.z(),angular.x(),angular.y(),angular.z(),592,1,1,1,true);
  var actor=new OrientedBox(new Vec3d(21.63584963467005,303.5148925965621+.8999999761581421,22.310573272363882),new Vec3d(.30000001192092896,.8999999761581421,.30000001192092896),OrthonormalFrame3d.IDENTITY);
  List<DynamicCollisionObstacleSnapshot> floors=new ArrayList<>();
  for(int x=-2;x<=1;x++)for(int z=0;z<=3;z++)floors.add(new DynamicCollisionObstacleSnapshot(1,floors.size(),new OrientedBox(new Vec3d(x+.5,.5,z+.5),.5,OrthonormalFrame3d.IDENTITY),motion));
  for(int direction=0;direction<16;direction++) {
   var tracker=new CollisionWorkTracker(CollisionWorkBudget.defaults());var context=new ObbQueryContext();context.setWorkTracker(tracker);
   var bounds=new Aabb3d(0,290,0,50,320,50);
   var scene=new CapturedCollisionScene(new CollisionCaptureDomain(bounds,bounds),592,1,KinematicStepContext.fullTick(592,0),tracker,
    new WorldBorderCollisionSnapshot(-1000,-1000,1000,1000),List.of(),floors,List.of(),List.of(),Map.of(),0);
   var requested=new Vec3d(.12*Math.cos(direction*Math.PI/8),move.y(),.12*Math.sin(direction*Math.PI/8));
   var result=GravityCharacterRoute.resolve(actor,new KinematicMoveRequest(requested,OwnedMotion.ZERO,KinematicMoveRequest.Channel.SELF),
    GravityFrame.downOnly(new Vec3d(0,-1,0)),scene,context,StepUpIntent.disabled());
   assertFalse(result.indeterminate(),"direction="+direction+" reason="+result.indeterminateReason()+" result="+result.resolvedMovement());
   assertEquals(requested.x(),result.resolvedMovement().x(),1e-6);
   assertEquals(requested.z(),result.resolvedMovement().z(),1e-6);
  }
 }
}
