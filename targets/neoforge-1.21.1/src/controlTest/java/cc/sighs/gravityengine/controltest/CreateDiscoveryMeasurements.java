package cc.sighs.gravityengine.controltest;

import cc.sighs.gravityengine.gravity.collision.provider.*;
import cc.sighs.gravityengine.gravity.collision.RigidPublicationCollector;
import cc.sighs.gravityengine.gravity.collision.CollisionWorkTracker;
import cc.sighs.gravityengine.gravity.collision.CollisionWorkBudget;
import cc.sighs.gravityengine.api.math.Vec3d;
import cc.sighs.gravityengine.gravity.integration.collision.MinecraftCollisionSubject;
import cc.sighs.gravityengine.gravity.integration.compat.create.CreateRigidCollisionProvider;
import cc.sighs.gravityengine.gravity.kinematic.KinematicStepContext;
import cc.sighs.gravityengine.math.geometry.Aabb3d;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Capture-only measurements, including invalidation/index rebuild. Not a
 * whole-game benchmark or a substitute for native controller/physics checks. */
final class CreateDiscoveryMeasurements {
    private static long trajectoryReads;
    private static final class Geometry extends BearingContraption {
        Geometry(boolean sparse) {
            super(false,Direction.UP);
            blocks.put(BlockPos.ZERO,new StructureTemplate.StructureBlockInfo(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),null));
            if(sparse) {
                var far=new BlockPos(511,0,511);
                blocks.put(far,new StructureTemplate.StructureBlockInfo(far,Blocks.STONE.defaultBlockState(),null));
            }
            bounds=new AABB(0,0,0,sparse?512:1,1,sparse?512:1);anchor=BlockPos.ZERO;
        }
    }
    private static final class Vehicle extends ControlledContraptionEntity {
        Vehicle(ServerLevel level,double x,boolean sparse) {
            super(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("create","stationary_contraption")),level);
            setContraption(new Geometry(sparse));setPos(x,300,8);
            xo=getX();yo=getY();zo=getZ();setRotationAxis(Direction.Axis.Y);
        }
        @Override public Vec3 getPrevAnchorVec() {trajectoryReads++;return super.getPrevAnchorVec();}
        @Override protected void tickContraption() {} // captured input, no physics benchmark claim
    }
    static void verifyRawMovement(ServerLevel level) {
        var vehicle=new Vehicle(level,4096,false);
        CreateRigidCollisionProvider.joined(vehicle);
        try {
            var provider=RigidCollisionPublicationRegistry.providers(level).stream()
                    .filter(p->p.id().equals(CreateRigidCollisionProvider.ID)).findFirst().orElseThrow();
            var bounds=new Aabb3d(0,296,0,16,312,16);
            var query=new ExternalRigidCollisionQuery(bounds,bounds,KinematicStepContext.fullTick(level.getGameTime(),0));
            ContactBehaviorChecks.check(!provider.mayAffect(query),"far Create source is spatially excluded");
            vehicle.setPosRaw(8,300,8);
            ContactBehaviorChecks.check(provider.mayAffect(query),"native raw relocation invalidates discovery in the same tick");
            System.out.println("CREATE_DISCOVERY_PASSED far-exclusion raw-relocation parent-invalidation");
        } finally {CreateRigidCollisionProvider.left(vehicle);vehicle.discard();}
    }
    static void run(ServerLevel level) {
        if(!Boolean.getBoolean("gravityengine.createDiscoveryMeasurements")) return;
        var output=new ArrayList<String>();
        output.add("scenario,vehicles,queriesPerFrame,frames,medianNanos,p95Nanos,allocatedBytes,trajectoryReads,emittedPrimitives");
        for(var scenario:List.of("empty","far64","mixed64","near16","sparse512"))
            measure(level,scenario,output);
        try {
            java.nio.file.Files.write(java.nio.file.Path.of("create-discovery-measurements.csv"),output);
            java.nio.file.Files.writeString(java.nio.file.Path.of("create-discovery-environment.txt"),
                    System.getProperty("java.runtime.version")+"\n"+System.getProperty("os.name")+" "+System.getProperty("os.version")+
                    "\nprocessors="+Runtime.getRuntime().availableProcessors()+"\nheap="+Runtime.getRuntime().maxMemory()+
                    "\nCapture-only; 40 warmup frames, 160 measured frames, 16 operation subjects per frame; every frame mutates all source angles.\n");
        } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
        System.out.println("CREATE_DISCOVERY_MEASUREMENTS_PASSED "+String.join(" | ",output));
    }
    /** Matched native physics/FIELD plus sixteen captured character domains.
     * This measures server work, not rendering, network latency or full travel. */
    static void nativeWorkload(ServerLevel level,dev.ryanhcode.sable.sublevel.ServerSubLevel parent,
            ControlledContraptionEntity child,
            com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor,
            com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity bearing) {
        if(!Boolean.getBoolean("gravityengine.createDiscoveryMeasurements")) return;
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        var system=container.physicsSystem();
        int oldSteps=system.getConfig().substepsPerTick;
        var actors=new ArrayList<ContactBehaviorChecks.Actor>();
        var distant=new ArrayList<Vehicle>();
        long[] fieldQueries={0},emitted={0};
        var output=new ArrayList<String>();
        output.add("substeps,subjects,distantChildren,frames,medianNanos,p95Nanos,allocatedBytes,fieldQueries,emittedPrimitives");
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("allocation measurement unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        try(var lease=cc.sighs.gravityengine.api.GravityEngineApi.publish(level,com.example.examplemod.gravity.ProviderFixture.ID,
                cc.sighs.gravityengine.api.GravityFieldDefinition.block(ResourceLocation.fromNamespaceAndPath("gravityengine","native_measurement"),
                        BlockPos.ZERO,query->{fieldQueries[0]++;return new cc.sighs.gravityengine.api.field.GravityFieldSample(Vec3d.ZERO);},
                        cc.sighs.gravityengine.api.field.GravityFields.infiniteInfluence(),
                        cc.sighs.gravityengine.api.field.GravityFieldCompositionMode.OVERRIDE,1))) {
            for(int i=0;i<16;i++) actors.add(new ContactBehaviorChecks.Actor(level));
            var origin=parent.logicalPose().position();
            for(int x=((int)origin.x-16)>>4;x<=((int)origin.x+16)>>4;x++)
                for(int z=((int)origin.z-16)>>4;z<=((int)origin.z+16)>>4;z++) level.getChunk(x,z);
            for(int i=0;i<64;i++) {var vehicle=new Vehicle(level,4096+i*64,false);CreateRigidCollisionProvider.joined(vehicle);distant.add(vehicle);}
            for(int steps:new int[]{2,8}) {
                system.getConfig().substepsPerTick=steps;
                long[] timings=new long[160];long bytes=0;
                for(int frame=-40;frame<160;frame++) {
                    if(frame==0) {fieldQueries[0]=0;emitted[0]=0;bytes=bean.getThreadAllocatedBytes(Thread.currentThread().threadId());}
                    long began=System.nanoTime();
                    ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
                    var handle=system.getPhysicsHandle(parent);
                    handle.addLinearAndAngularVelocity(new org.joml.Vector3d().sub(handle.getLinearVelocity(new org.joml.Vector3d())),
                            new org.joml.Vector3d(0,.1,0).sub(handle.getAngularVelocity(new org.joml.Vector3d())));
                    child.tick();motor.tick();bearing.tick();system.tick(container);parent.updateBoundingBox();
                    for(var vehicle:distant) vehicle.setAngle((frame+40)*.1f);
                    var center=parent.logicalPose().position();
                    var bounds=new Aabb3d(center.x-8,center.y-8,center.z-8,center.x+8,center.y+8,center.z+8);
                    var query=new ExternalRigidCollisionQuery(bounds,bounds,KinematicStepContext.fullTick(level.getGameTime(),0));
                    for(var actor:actors) {
                        actor.setPos(center.x,center.y+3,center.z);
                        try(var subject=MinecraftCollisionSubject.open(actor)) {
                            RigidCollisionPublicationRegistry.capture(level,query,obstacle->emitted[0]++);
                        }
                        cc.sighs.gravityengine.gravity.integration.MovementEnvironmentCapture.capture(actor,
                                new CollisionWorkTracker(CollisionWorkBudget.defaults()));
                    }
                    if(frame>=0) timings[frame]=System.nanoTime()-began;
                }
                bytes=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-bytes;
                ContactBehaviorChecks.check(fieldQueries[0]>0&&emitted[0]>0,"native workload executes FIELD and collision capture");
                Arrays.sort(timings);
                output.add(steps+",16,64,160,"+timings[80]+","+timings[151]+","+bytes+","+fieldQueries[0]+","+emitted[0]);
            }
            java.nio.file.Files.write(java.nio.file.Path.of("native-workload-measurements.csv"),output);
            System.out.println("NATIVE_WORKLOAD_MEASUREMENTS_PASSED "+String.join(" | ",output));
        } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
        finally {
            system.getConfig().substepsPerTick=oldSteps;
            for(var vehicle:distant) {CreateRigidCollisionProvider.left(vehicle);vehicle.discard();}
            for(var actor:actors) actor.discard();
        }
    }
    private static void measure(ServerLevel level,String scenario,List<String> output) {
        int count=switch(scenario) {case "empty"->0;case "near16"->16;case "sparse512"->1;default->64;};
        var vehicles=new ArrayList<Vehicle>();
        var actors=new ArrayList<ContactBehaviorChecks.Actor>();
        try {
            for(int i=0;i<count;i++) {
                boolean near=scenario.equals("near16") || scenario.equals("sparse512") || scenario.equals("mixed64") && i==0;
                var vehicle=new Vehicle(level,near?8:4096+i*64,scenario.equals("sparse512"));
                // Isolate publication capture work from chunk generation/entity ticking.
                CreateRigidCollisionProvider.joined(vehicle);vehicles.add(vehicle);
            }
            for(int i=0;i<16;i++) {var actor=new ContactBehaviorChecks.Actor(level);actor.setPos(8+i*.01,301,8);actors.add(actor);}
            var provider=RigidCollisionPublicationRegistry.providers(level).stream()
                    .filter(p->p.id().equals(CreateRigidCollisionProvider.ID)).findFirst().orElseThrow();
            var bounds=new Aabb3d(0,296,0,16,312,16);
            var query=new ExternalRigidCollisionQuery(bounds,bounds,KinematicStepContext.fullTick(level.getGameTime(),0));
            long[] emitted={0};RigidPublicationCollector collector=obstacle->emitted[0]++;
            var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
            if(!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("allocation measurement unavailable");
            bean.setThreadAllocatedMemoryEnabled(true);
            long[] timings=new long[160];long allocated=0,reads=0;
            for(int frame=-40;frame<160;frame++) {
                if(frame==0) {emitted[0]=0;reads=trajectoryReads;allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId());}
                long started=System.nanoTime();
                for(var vehicle:vehicles) vehicle.setAngle((frame+40)*.1f);
                for(var actor:actors) try(var subject=MinecraftCollisionSubject.open(actor)) {
                    if(provider.mayAffect(query)) provider.capture(query,collector);
                }
                if(frame>=0) timings[frame]=System.nanoTime()-started;
            }
            allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocated;
            reads=trajectoryReads-reads;
            if(scenario.equals("empty") || scenario.equals("far64"))
                ContactBehaviorChecks.check(emitted[0]==0,"far/empty discovery emits no primitives");
            else ContactBehaviorChecks.check(emitted[0]>0,"relevant discovery retains collision work");
            Arrays.sort(timings);
            output.add(scenario+","+count+",16,160,"+timings[80]+","+timings[151]+","+allocated+","+reads+","+emitted[0]);
        } finally {
            for(var vehicle:vehicles) {CreateRigidCollisionProvider.left(vehicle);vehicle.discard();}
            for(var actor:actors) actor.discard();
        }
    }
}
