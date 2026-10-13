package cc.sighs.gravityengine.controltest;

import cc.sighs.gravityengine.api.GravityEngineApi;
import cc.sighs.gravityengine.api.GravityFieldDefinition;
import cc.sighs.gravityengine.api.field.*;
import cc.sighs.gravityengine.api.math.Vec3d;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;

/** Executes the real native force methods. Reflection keeps Aeronautics out of absent-mod test loading. */
final class AeronauticsRuntimeChecks {
    static java.util.Set<String> run(ServerLevel level) throws ReflectiveOperationException {
        var container = SubLevelContainer.getContainer(level);
        var pose = new Pose3d(); pose.position().set(80, 150, 80); pose.orientation().rotateZ(.4);
        var body = (ServerSubLevel)container.allocateSubLevel(java.util.UUID.randomUUID(), 48, 0, pose);
        var plot = body.getPlot();
        try {
            plot.newEmptyChunk(plot.getCenterChunk());
            plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
            plot.getEmbeddedLevelAccessor().setBlock(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            body.buildMassTracker(); body.updateMergedMassData(1);
            ContactBehaviorChecks.check(body.getMassTracker().getCenterOfMass() != null, "lift fixture native mass initialized");
            body.updateLastPose(); body.updateBoundingBox();
            var type = Class.forName("dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon");
            var graphType = Class.forName("dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.graph.BalloonLayerGraph");
            var graph = graphType.getConstructor(int.class).newInstance(0);
            var balloon = type.getConstructors()[0].newInstance(level,
                    new dev.ryanhcode.sable.util.LevelAccelerator(level), plot.getCenterBlock(), graph,
                    new it.unimi.dsi.fastutil.objects.ObjectArrayList<>());
            // A one-volume, already-filled native balloon fixture; gas thermodynamics stay upstream.
            field(type.getSuperclass(), "capacity").setInt(balloon, 1);
            field(type, "totalFilledVolume").setDouble(balloon, 1);
            field(type, "totalLift").setDouble(balloon, 2);
            ((Vector3d)field(type, "averagePosition").get(balloon)).set(.25, .5, .5);
            var apply = type.getMethod("applyForces", double.class);
            var system=container.physicsSystem();var handle=system.getPhysicsHandle(body);
            var nativeGravity=dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level);
            for(var acceleration:new Vec3d[]{new Vec3d(.03,0,0),new Vec3d(0,.03,0),new Vec3d(.01,-.02,.03),Vec3d.ZERO}) {
                try(var publication=GravityEngineApi.publish(level,com.example.examplemod.gravity.ProviderFixture.ID,
                        GravityFieldDefinition.block(ResourceLocation.fromNamespaceAndPath("gravityengine","lift_test"),
                                BlockPos.ZERO,query->new GravityFieldSample(acceleration),GravityFields.infiniteInfluence(),
                                GravityFieldCompositionMode.OVERRIDE,1))) {
                    for(int mode=0;mode<3;mode++) {
                        int coverageMode=mode;
                        var calls=new java.util.concurrent.atomic.AtomicInteger();
                        com.example.examplemod.gravity.ProviderFixture.secondCoverage(level,query->
                                coverageMode==1 || coverageMode==2 && calls.incrementAndGet()==3
                                        ?FieldCoverage.INCOMPLETE:FieldCoverage.COMPLETE);
                        handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(),
                                handle.getAngularVelocity(new Vector3d()).negate());
                        body.prePhysicsTickBegin();body.prePhysicsTick(system,handle,.025);
                        apply.invoke(balloon,.025);
                        var before=handle.getLinearVelocity(new Vector3d());
                        var angularBefore=handle.getAngularVelocity(new Vector3d());
                        // Apply native queued forces first, so the comparison isolates
                        // GE's single commit and proves native forces are retained.
                        var groups=body.getQueuedForceGroups();
                        if(groups!=null) for(var group:groups.values()) handle.applyForcesAndReset(group.getForceTotal());
                        var nativeLinear=handle.getLinearVelocity(new Vector3d());
                        var nativeAngular=handle.getAngularVelocity(new Vector3d());
                        var worldCenter=(Vector3d)field(type,"worldCenter").get(null);
                        double pressure=dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getAirPressure(level,worldCenter);
                        var localDifference=body.logicalPose().orientation().transformInverse(
                                new Vector3d(acceleration.x(),acceleration.y(),acceleration.z()).mul(400).sub(nativeGravity));
                        var balloonForce=new Vector3d(localDifference).mul(-2);
                        var lever=new Vector3d((Vector3d)field(type,"localAveragePosition").get(null)).mul(pressure)
                                .add((Vector3d)field(type,"gradient").get(null));
                        var expectedAngular=lever.cross(balloonForce,new Vector3d()).mul(.025);
                        body.getMassTracker().getInverseInertiaTensor().transform(expectedAngular);
                        body.logicalPose().orientation().transform(expectedAngular);
                        var expectedLinear=body.logicalPose().orientation().transform(balloonForce.mul(pressure*.025/body.getMassTracker().getMass()))
                                .add(new Vector3d(acceleration.x(),acceleration.y(),acceleration.z()).mul(400).sub(nativeGravity).mul(.025));
                        cc.sighs.gravityengine.gravity.integration.compat.sable.SableGravityBridge.apply(body,system,handle,.025);
                        if(mode!=0) {expectedLinear.zero();expectedAngular.zero();}
                        ContactBehaviorChecks.check(handle.getLinearVelocity(new Vector3d()).sub(nativeLinear).distance(expectedLinear)<1e-6,
                                "atomic mass/balloon linear correction mode="+mode+" gravity="+acceleration);
                        ContactBehaviorChecks.check(handle.getAngularVelocity(new Vector3d()).sub(nativeAngular).distance(expectedAngular)<1e-6,
                                "atomic mass/balloon torque correction mode="+mode+" gravity="+acceleration);
                        if(mode==2) ContactBehaviorChecks.check(calls.get()==3,"last mass query rejects previously staged complete balloon correction");
                        ContactBehaviorChecks.check(!cc.sighs.gravityengine.gravity.integration.compat.sable.SableGravityBridge.hasStep(body),
                                "force evidence retired after commit or refusal");
                    }
                }
            }
            com.example.examplemod.gravity.ProviderFixture.secondCoverage(level,query->FieldCoverage.COMPLETE);
            floatingParity(body);
            System.out.println("AERONAUTICS_LIFT_CHECKS_PASSED native-balloon native-floating complete incomplete late-incomplete zero oblique inverted");
            return java.util.Set.of("balloon-gravity", "floating-gravity", "incomplete-native");
        } finally {
            com.example.examplemod.gravity.ProviderFixture.secondCoverage(level, query -> FieldCoverage.COMPLETE);
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            container.processSubLevelRemovals();
        }
    }
    private static java.lang.reflect.Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void floatingParity(ServerSubLevel body) throws ReflectiveOperationException {
        var controller=body.getFloatingBlockController();
        var nativeContainer=(dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer)
                field(controller.getClass(),"sublevelContainer").get(controller);
        ((Vector3d)field(controller.getClass(),"previousCenterOfMass").get(controller)).set(body.getMassTracker().getCenterOfMass());
        var nativeLift=controller.getClass().getDeclaredMethod("applyLift",Vector3d.class,Vector3d.class,Vector3d.class,double.class);
        nativeLift.setAccessible(true);
        var nativeFriction=controller.getClass().getDeclaredMethod("applyFriction",
                dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer.class,
                dev.ryanhcode.sable.physics.floating_block.FloatingBlockCluster.class,
                org.joml.Vector3dc.class,org.joml.Vector3dc.class,org.joml.Vector3dc.class,Vector3d.class,Vector3d.class);
        nativeFriction.setAccessible(true);
        var level=body.getLevel();var system=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level);
        var handle=system.getPhysicsHandle(body);
        for(boolean capped:new boolean[]{false,true}) {
            nativeContainer.clusters.clear();
            for(double x:new double[]{-1,1}) {
                var material=new dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial(capped,false,false,
                        body.getMassTracker().getMass()*2,0,0,0,0,0);
                var cluster=new dev.ryanhcode.sable.physics.floating_block.FloatingBlockCluster(material);
                cluster.getBlockData().addFloatingBlock(new Vector3d(x,0,0),1);
                nativeContainer.clusters.add(cluster);
            }
            for(var acceleration:new Vec3d[]{new Vec3d(.03,0,0),new Vec3d(0,.03,0),new Vec3d(.01,-.02,.03),Vec3d.ZERO}) {
                try(var publication=GravityEngineApi.publish(level,com.example.examplemod.gravity.ProviderFixture.ID,
                        GravityFieldDefinition.block(ResourceLocation.fromNamespaceAndPath("gravityengine","floating_parity"),BlockPos.ZERO,
                                query->new GravityFieldSample(acceleration),GravityFields.infiniteInfluence(),GravityFieldCompositionMode.OVERRIDE,1))) {
                    var nativeLinear=new Vector3d();var nativeAngular=new Vector3d();
                    handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(),handle.getAngularVelocity(new Vector3d()).negate());
                    cc.sighs.gravityengine.gravity.integration.compat.sable.SableGravityBridge.beginStep(body,.025);
                    controller.physicsTick(1,.025,new Vector3d(),new Vector3d(),nativeLinear,nativeAngular);
                    var desiredGravity=body.logicalPose().orientation().transformInverse(new Vector3d(acceleration.x(),acceleration.y(),acceleration.z()).mul(400));
                    var referenceLinear=new Vector3d();var referenceAngular=new Vector3d();
                    if(desiredGravity.lengthSquared()>0) nativeLift.invoke(controller,desiredGravity,referenceLinear,referenceAngular,.025);
                    var nativeGravityLocal=body.logicalPose().orientation().transformInverse(
                            dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level));
                    var prediction=nativeGravityLocal.sub(desiredGravity).mul(.025/2.1);
                    for(var cluster:nativeContainer.clusters) {
                        var drag=new Vector3d();var torque=new Vector3d();
                        nativeFriction.invoke(controller,nativeContainer,cluster,desiredGravity,prediction,new Vector3d(),drag,torque);
                        referenceLinear.fma(.025,drag);referenceAngular.fma(.025,torque);
                    }
                    var expectedLinear=body.logicalPose().orientation().transform(referenceLinear.sub(nativeLinear).div(body.getMassTracker().getMass()));
                    expectedLinear.add(new Vector3d(acceleration.x(),acceleration.y(),acceleration.z()).mul(400)
                            .sub(dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level)).mul(.025));
                    var expectedAngular=body.getMassTracker().getInverseInertiaTensor().transform(referenceAngular.sub(nativeAngular));
                    body.logicalPose().orientation().transform(expectedAngular);
                    cc.sighs.gravityengine.gravity.integration.compat.sable.SableGravityBridge.apply(body,system,handle,.025);
                    ContactBehaviorChecks.check(handle.getLinearVelocity(new Vector3d()).distance(expectedLinear)<1e-6,
                            "native floating uniform lift parity cap="+capped+" gravity="+acceleration+
                                    " expected="+expectedLinear+" actual="+handle.getLinearVelocity(new Vector3d()));
                    ContactBehaviorChecks.check(handle.getAngularVelocity(new Vector3d()).distance(expectedAngular)<1e-6,
                            "native floating uniform torque parity cap="+capped+" gravity="+acceleration);
                }
            }
        }
        nativeContainer.clusters.clear();
        double strength=body.getMassTracker().getMass()*2;
        for(double x:new double[]{-1,1}) {
            var cluster=new dev.ryanhcode.sable.physics.floating_block.FloatingBlockCluster(
                    new dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial(false,false,false,strength,0,0,0,0,0));
            cluster.getBlockData().addFloatingBlock(new Vector3d(x,0,0),1);nativeContainer.clusters.add(cluster);
        }
        try(var publication=GravityEngineApi.publish(level,com.example.examplemod.gravity.ProviderFixture.ID,
                GravityFieldDefinition.block(ResourceLocation.fromNamespaceAndPath("gravityengine","floating_gradient"),BlockPos.ZERO,
                        query->{
                            var local=body.logicalPose().transformPositionInverse(new Vector3d(query.position().x(),query.position().y(),query.position().z()))
                                    .sub(body.getMassTracker().getCenterOfMass());
                            var world=body.logicalPose().orientation().transform(new Vector3d(0,-8+4*local.x,0)).div(400);
                            return new GravityFieldSample(new Vec3d(world.x,world.y,world.z));
                        },GravityFields.infiniteInfluence(),GravityFieldCompositionMode.OVERRIDE,1))) {
            handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(),handle.getAngularVelocity(new Vector3d()).negate());
            cc.sighs.gravityengine.gravity.integration.compat.sable.SableGravityBridge.beginStep(body,.025);
            var nativeLinear=new Vector3d();var nativeAngular=new Vector3d();
            controller.physicsTick(1,.025,new Vector3d(),new Vector3d(),nativeLinear,nativeAngular);
            var expectedForce=new Vector3d(0,16*strength,0);
            var expectedTorque=new Vector3d(0,0,-8*strength);
            var points=((cc.sighs.gravityengine.gravity.integration.compat.sable.SableMassPoints.Access)body.getSelfMassTracker())
                    .gravityengine$massPoints().snapshot();
            var nativeLocal=body.logicalPose().orientation().transformInverse(
                    dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level));
            for(var point:points) {
                var radius=new Vector3d(point.position().x(),point.position().y(),point.position().z()).sub(body.getMassTracker().getCenterOfMass());
                var force=new Vector3d(0,-8+4*radius.x,0).sub(nativeLocal).mul(point.mass());
                expectedForce.add(force);expectedTorque.add(radius.cross(force));
            }
            expectedForce.mul(.025).sub(nativeLinear).div(body.getMassTracker().getMass());
            expectedTorque.mul(.025).sub(nativeAngular);
            body.logicalPose().orientation().transform(expectedForce);
            body.getMassTracker().getInverseInertiaTensor().transform(expectedTorque);
            body.logicalPose().orientation().transform(expectedTorque);
            cc.sighs.gravityengine.gravity.integration.compat.sable.SableGravityBridge.apply(body,system,handle,.025);
            ContactBehaviorChecks.check(handle.getLinearVelocity(new Vector3d()).distance(expectedForce)<1e-6,
                    "cluster gradient net force matches analytic sum");
            ContactBehaviorChecks.check(handle.getAngularVelocity(new Vector3d()).distance(expectedTorque)<1e-6,
                    "cluster gradient torque retains spatially separated lift contributions");
        } finally {nativeContainer.clusters.clear();}
    }
}
