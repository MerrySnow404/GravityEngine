package cc.sighs.gravityengine.controltest;

import cc.sighs.gravityengine.api.math.Vec3d;
import cc.sighs.gravityengine.gravity.collision.DynamicCollisionObstacleSnapshot;
import cc.sighs.gravityengine.gravity.collision.provider.ExternalRigidCollisionQuery;
import cc.sighs.gravityengine.gravity.collision.provider.RigidCollisionPublicationRegistry;
import cc.sighs.gravityengine.gravity.integration.collision.MinecraftCollisionSubject;
import cc.sighs.gravityengine.gravity.integration.compat.create.CreateRigidCollisionProvider;
import cc.sighs.gravityengine.gravity.kinematic.KinematicStepContext;
import cc.sighs.gravityengine.math.geometry.Aabb3d;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;

/** Real transformed Create entities and Sable plots. Tests prescribe motion,
 * then compare captured geometry with the native transform at each endpoint. */
final class CreateRuntimeChecks {
    static java.util.Set<String> run(ServerLevel level) {
        nativeBearing(level);
        nativeCart(level);
        var container = SubLevelContainer.getContainer(level);
        var pose = new Pose3d(); pose.position().set(32, 300, 32);
        var parent = container.allocateNewSubLevel(pose);
        var plot = parent.getPlot(); plot.newEmptyChunk(plot.getCenterChunk());
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
        parent.updateLastPose(); parent.updateBoundingBox();
        class Geometry extends BearingContraption {
            Geometry() {
                super(false, Direction.UP);
                blocks.put(BlockPos.ZERO, new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, Blocks.ICE.defaultBlockState(), null));
                blocks.put(new BlockPos(2, 0, 0), new StructureTemplate.StructureBlockInfo(new BlockPos(2, 0, 0), Blocks.STONE.defaultBlockState(), null));
                bounds = new AABB(0, 0, 0, 3, 1, 1); anchor = BlockPos.ZERO;
                seats.add(new BlockPos(1,1,0));
            }
        }
        class Vehicle extends ControlledContraptionEntity {
            Vehicle() {
                super(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("create", "stationary_contraption")), level);
                setContraption(new Geometry());
            }
            @Override protected void tickContraption() {} // fixture prescribes bearing motion
        }
        var vehicle = new Vehicle();
        var center = plot.getCenterBlock();
        vehicle.setPos(center.getX()+3, center.getY(), center.getZ());
        vehicle.xo = vehicle.getX(); vehicle.yo = vehicle.getY(); vehicle.zo = vehicle.getZ();
        var actor = new ContactBehaviorChecks.Actor(level); actor.setPos(32, 300, 32);
        try {
            ContactBehaviorChecks.check(level.addFreshEntity(vehicle), "native Create entity join");
            vehicle.tick(); // Native Sable initialization builds and attaches the contraption mass.
            ContactBehaviorChecks.check(CreateRigidCollisionProvider.publishes(vehicle), "native join registers Create source");
            vehicle.setAngle(45);
            parent.logicalPose().position().add(.3, .1, 0);
            parent.logicalPose().orientation().rotateY(.2);
            parent.updateBoundingBox();
            var time = KinematicStepContext.fullTick(level.getGameTime(), 0);
            var domain = new Aabb3d(16, 284, 16, 48, 316, 48);
            var captured = new ArrayList<DynamicCollisionObstacleSnapshot>();
            try (var subject = MinecraftCollisionSubject.open(actor)) {
                RigidCollisionPublicationRegistry.capture(level, new ExternalRigidCollisionQuery(domain, domain, time), captured::add);
            }
            var children = captured.stream().filter(p -> p.providerNamespace().equals(CreateRigidCollisionProvider.ID)).toList();
            ContactBehaviorChecks.check(children.size() == 2, "Create collision publishes both blocks separately");
            var localCenter = new Vec3(.5, .5, .5);
            var expected = parent.logicalPose().transformPosition(vehicle.toGlobalVector(localCenter, 1));
            var first = children.stream().filter(p -> p.localBody().center().lengthSquared() == 0).findFirst().orElseThrow();
            ContactBehaviorChecks.check(first.bodyAt(1).center().distanceSquared(new Vec3d(expected.x, expected.y, expected.z)) < 1e-7,
                    "Create child pose composes with native SubLevel endpoint");
            var point = first.motion().poseAt(.5).transformPoint(new Vec3d(1, .2, .3));
            var derivative = first.motion().poseAt(.500001).transformPoint(new Vec3d(1, .2, .3))
                    .subtract(first.motion().poseAt(.499999).transformPoint(new Vec3d(1, .2, .3))).divide(.000002);
            ContactBehaviorChecks.check(first.motion().velocityAt(point, .5).distanceSquared(derivative) < 1e-12,
                    "Create material velocity includes both motions once");
            var originalPosition=new org.joml.Vector3d(parent.logicalPose().position());
            parent.logicalPose().position().add(256,0,0);parent.updateLastPose();parent.updateBoundingBox();
            var shifted=parent.logicalPose().transformPosition(vehicle.toGlobalVector(localCenter,1));
            var shiftedBounds=new Aabb3d(shifted.x-4,shifted.y-4,shifted.z-4,shifted.x+4,shifted.y+4,shifted.z+4);
            var createProvider=RigidCollisionPublicationRegistry.providers(level).stream()
                    .filter(p->p.id().equals(CreateRigidCollisionProvider.ID)).findFirst().orElseThrow();
            ContactBehaviorChecks.check(createProvider.mayAffect(new ExternalRigidCollisionQuery(shiftedBounds,shiftedBounds,time)),
                    "same-tick native parent bounds mutation invalidates child discovery");
            parent.logicalPose().position().set(originalPosition);parent.updateLastPose();parent.updateBoundingBox();
            attachedMass(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)parent,vehicle);
            seat(level,parent,vehicle,actor);
            landingCallbackWorld(level,parent,vehicle);
            try (var subject = MinecraftCollisionSubject.open(actor)) {
                ContactBehaviorChecks.check(RigidCollisionPublicationRegistry.resolve(level, first.identity(), time).isEmpty(),
                        "attached block replacement retires the pre-edit identity");
                var edited=new ArrayList<DynamicCollisionObstacleSnapshot>();
                RigidCollisionPublicationRegistry.capture(level,new ExternalRigidCollisionQuery(domain,domain,time),edited::add);
                var current=edited.stream().filter(p->p.providerNamespace().equals(CreateRigidCollisionProvider.ID)).findFirst().orElseThrow();
                ContactBehaviorChecks.check(RigidCollisionPublicationRegistry.resolve(level, current.identity(), time).isPresent(),
                        "Create identity resolves before removal");
                vehicle.discard();
                ContactBehaviorChecks.check(RigidCollisionPublicationRegistry.resolve(level, current.identity(), time).isEmpty(),
                        "native removal retires Create source");
                ContactBehaviorChecks.check(first.bodyAt(1).center().distanceSquared(new Vec3d(expected.x, expected.y, expected.z)) < 1e-7,
                        "completed capture survives native removal");
            }
            System.out.println("CREATE_RUNTIME_PASSED geometry composed-motion removal");
            CreateDiscoveryMeasurements.verifyRawMovement(level);
            CreateDiscoveryMeasurements.run(level);
            return java.util.Set.of("geometry", "composed-motion", "removal", "attached-mass", "attached-block-edit", "seat", "native-bearing", "native-piston", "native-cart", "discovery");
        } finally {
            vehicle.discard(); actor.discard();
            container.removeSubLevel(parent, SubLevelRemovalReason.REMOVED); container.processSubLevelRemovals();
        }
    }
    /** Assembly, controller rotation and disassembly all run Create's own code;
     * the parent is advanced by Sable's real physics system. */
    private static void nativeBearing(ServerLevel level) {
        // These controls remove and reuse native plot slots within one game tick.
        // Vanilla normally clears its four-entry chunk cache at the tick boundary.
        ((cc.sighs.gravityengine.controltest.mixin.ChunkCacheControl)(Object)level.getChunkSource()).gravityengine$clearCache();
        var container=SubLevelContainer.getContainer(level);
        var system=container.physicsSystem();
        var pose=new Pose3d();pose.position().set(48,300,48);
        var parent=(dev.ryanhcode.sable.sublevel.ServerSubLevel)container.allocateNewSubLevel(pose);
        var plot=parent.getPlot();plot.newEmptyChunk(plot.getCenterChunk());
        var bearingState=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","mechanical_bearing")).defaultBlockState()
                .setValue(com.simibubi.create.content.contraptions.bearing.BearingBlock.FACING,Direction.EAST);
        plot.getEmbeddedLevelAccessor().setBlock(new BlockPos(2,0,0),bearingState,3);
        plot.getEmbeddedLevelAccessor().setBlock(new BlockPos(3,0,0),Blocks.STONE.defaultBlockState(),3);
        var motorState=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","creative_motor")).defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST);
        plot.getEmbeddedLevelAccessor().setBlock(new BlockPos(1,0,0),motorState,3);
        var bearing=(com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity)
                level.getBlockEntity(plot.getCenterBlock().offset(2,0,0));
        var motor=(com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)
                level.getBlockEntity(plot.getCenterBlock().offset(1,0,0));
        long originalTime=level.getGameTime();
        ControlledContraptionEntity child=null;
        container.addForceLoadTicket(parent,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
        try {
            ContactBehaviorChecks.check(bearing!=null,"native bearing block entity exists in plot");
            bearing.initialize();motor.initialize();motor.generatedSpeed.setValue(64);motor.updateGeneratedRotation();
            bearing.assemble();
            child=bearing.getMovedContraption();
            ContactBehaviorChecks.check(child!=null && bearing.isRunning(),"native bearing assembles real blocks: "+bearing.getLastAssemblyException());
            child.tick();
            ContactBehaviorChecks.check(level.getBlockState(plot.getCenterBlock().offset(3,0,0)).isAir(),"assembly removes plot block");
            for(int i=0;i<3;i++) system.tick(container);
            var handle=system.getPhysicsHandle(parent);
            handle.addLinearAndAngularVelocity(new org.joml.Vector3d(2,0,0).sub(handle.getLinearVelocity(new org.joml.Vector3d())),
                    new org.joml.Vector3d(0,.4,0).sub(handle.getAngularVelocity(new org.joml.Vector3d())));
            var actor=new ContactBehaviorChecks.Actor(level);actor.setPos(48,300,48);
            DynamicCollisionObstacleSnapshot captured=null;
            for(int step=0;step<3;step++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
                child.tick();motor.tick();bearing.tick();system.tick(container);parent.updateBoundingBox();
                ContactBehaviorChecks.check(Math.abs(child.getAngle(1)-child.getAngle(0))>0,"native controller advances child angle speed="+
                        bearing.getSpeed()+" running="+bearing.isRunning()+" stalled="+child.isStalled());
                var domain=new Aabb3d(32,280,32,64,320,64);
                var obstacles=new ArrayList<DynamicCollisionObstacleSnapshot>();
                try(var subject=MinecraftCollisionSubject.open(actor)) {
                    RigidCollisionPublicationRegistry.capture(level,new ExternalRigidCollisionQuery(domain,domain,
                            KinematicStepContext.fullTick(level.getGameTime(),0)),obstacles::add);
                }
                captured=obstacles.stream().filter(p->p.providerNamespace().equals(CreateRigidCollisionProvider.ID)).findFirst().orElseThrow();
                var expected=parent.logicalPose().transformPosition(child.toGlobalVector(new Vec3(.5,.5,.5),1));
                ContactBehaviorChecks.check(captured.bodyAt(1).center().distance(new Vec3d(expected.x,expected.y,expected.z))<1e-6,
                        "native bearing and physics parent share captured endpoint");
            }
            ContactBehaviorChecks.check(captured!=null,"native controller produced immutable collision evidence");
            CreateDiscoveryMeasurements.nativeWorkload(level,parent,child,motor,bearing);
            var identity=captured.identity();
            bearing.disassemble();
            ContactBehaviorChecks.check(child.isRemoved() && !bearing.isRunning(),"native bearing disassembly removes child");
            try(var subject=MinecraftCollisionSubject.open(actor)) {
                ContactBehaviorChecks.check(RigidCollisionPublicationRegistry.resolve(level,identity,
                        KinematicStepContext.fullTick(level.getGameTime(),0)).isEmpty(),"disassembly retires collision identity");
            }
            System.out.println("CREATE_NATIVE_BEARING_PASSED assembly controller physics-parent disassembly identity-retirement");
            nativePiston(level,parent);
        } finally {
            if(child!=null && !child.isRemoved()) child.discard();
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(originalTime);
            container.removeForceLoadTicket(parent,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
            if (!parent.isRemoved()) container.removeSubLevel(parent,SubLevelRemovalReason.REMOVED);
            container.processSubLevelRemovals();
        }
    }
    private static void nativePiston(ServerLevel level,dev.ryanhcode.sable.sublevel.ServerSubLevel parent) {
        var plot=parent.getPlot();var blocks=plot.getEmbeddedLevelAccessor();
        var position=new BlockPos(4,0,4);
        var state=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","mechanical_piston"))
                .defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST);
        var pole=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","piston_extension_pole"))
                .defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST);
        blocks.setBlock(position,state,3);
        for(int i=1;i<=3;i++) blocks.setBlock(position.relative(Direction.WEST,i),pole,3);
        blocks.setBlock(position.east(),Blocks.STONE.defaultBlockState(),3);
        var axis=((com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock)state.getBlock()).getRotationAxis(state);
        var driveDirection=Direction.fromAxisAndDirection(axis,Direction.AxisDirection.POSITIVE);
        var motorPosition=position.relative(driveDirection.getOpposite());
        var motorState=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","creative_motor")).defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,driveDirection);
        blocks.setBlock(motorPosition,motorState,3);
        var piston=(com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity)level.getBlockEntity(plot.getCenterBlock().offset(position));
        var motor=(com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(plot.getCenterBlock().offset(motorPosition));
        var actor=new ContactBehaviorChecks.Actor(level);actor.setPos(48,300,48);
        com.simibubi.create.content.contraptions.AbstractContraptionEntity child=null;
        try {
            ContactBehaviorChecks.check(piston!=null && motor!=null,"native piston and motor exist");
            piston.initialize();motor.initialize();motor.generatedSpeed.setValue(-64);motor.updateGeneratedRotation();
            piston.assemble();child=piston.movedContraption;
            ContactBehaviorChecks.check(child!=null && piston.running,"native piston assembles actual pole and stone: "+piston.getLastAssemblyException());
            child.tick();
            var container=SubLevelContainer.getContainer(level);
            var system=container.physicsSystem();
            var before=child.position();DynamicCollisionObstacleSnapshot captured=null;
            for(int step=0;step<3;step++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
                child.tick();motor.tick();piston.tick();system.tick(container);parent.updateBoundingBox();
                var domain=new Aabb3d(24,275,24,88,325,80);
                var obstacles=new ArrayList<DynamicCollisionObstacleSnapshot>();
                try(var subject=MinecraftCollisionSubject.open(actor)) {
                    RigidCollisionPublicationRegistry.capture(level,new ExternalRigidCollisionQuery(domain,domain,
                            KinematicStepContext.fullTick(level.getGameTime(),0)),obstacles::add);
                }
                var children=obstacles.stream().filter(p->p.providerNamespace().equals(CreateRigidCollisionProvider.ID)).toList();
                ContactBehaviorChecks.check(!children.isEmpty(),"running piston publishes collision geometry");
                for(var obstacle:children) {
                    var local=obstacle.localBody().center();
                    var expected=parent.logicalPose().transformPosition(child.toGlobalVector(new Vec3(local.x()+.5,local.y()+.5,local.z()+.5),1));
                    ContactBehaviorChecks.check(obstacle.bodyAt(1).center().distance(new Vec3d(expected.x,expected.y,expected.z))<1e-6,
                            "native piston and physical parent share captured endpoint");
                }
                captured=children.getFirst();
            }
            ContactBehaviorChecks.check(child.position().distanceTo(before)>.01,"motor-driven piston actually translates child");
            var identity=captured.identity();piston.disassemble();
            ContactBehaviorChecks.check(child.isRemoved() && !piston.running,"native piston disassembly retires controller child");
            try(var subject=MinecraftCollisionSubject.open(actor)) {
                ContactBehaviorChecks.check(RigidCollisionPublicationRegistry.resolve(level,identity,
                        KinematicStepContext.fullTick(level.getGameTime(),0)).isEmpty(),"piston disassembly retires collision identity");
            }
            System.out.println("CREATE_NATIVE_PISTON_PASSED assembly controller physics-parent disassembly identity-retirement");
        } catch(com.simibubi.create.content.contraptions.AssemblyException failure) {
            throw new AssertionError("native piston assembly failed",failure);
        } finally { if(child!=null && !child.isRemoved()) child.discard();actor.discard(); }
    }
    private static void nativeCart(ServerLevel level) {
        var origin=new BlockPos(72,300,72);
        var states=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var actor=new ContactBehaviorChecks.Actor(level);actor.setPos(72,302,72);
        var cart=new net.minecraft.world.entity.vehicle.Minecart(level,72.5,300.0625,72.5);
        com.simibubi.create.content.contraptions.OrientedContraptionEntity child=null;
        long originalTime=level.getGameTime();
        try {
            for(int x=-1;x<=8;x++) for(int y=-1;y<=1;y++) {
                var position=origin.offset(x,y,0);states.put(position,level.getBlockState(position));
                level.setBlock(position,y==-1?Blocks.STONE.defaultBlockState():y==0?Blocks.RAIL.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.RailBlock.SHAPE,net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST)
                        :Blocks.AIR.defaultBlockState(),18);
            }
            var state=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","cart_assembler")).defaultBlockState()
                    .setValue(com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock.RAIL_SHAPE,net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST)
                    .setValue(com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock.POWERED,true);
            level.setBlock(origin.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            level.setBlock(origin,state,3);level.setBlock(origin.above(),Blocks.STONE.defaultBlockState(),18);
            var assembler=(com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockEntity)level.getBlockEntity(origin);
            ContactBehaviorChecks.check(assembler!=null,"native cart assembler exists");
            level.addFreshEntity(cart);assembler.initialize();assembler.tryAssemble(cart);
            child=cart.getPassengers().stream().filter(com.simibubi.create.content.contraptions.OrientedContraptionEntity.class::isInstance)
                    .map(com.simibubi.create.content.contraptions.OrientedContraptionEntity.class::cast).findFirst().orElse(null);
            ContactBehaviorChecks.check(child!=null,"native cart assembly state="+level.getBlockState(origin)+" error="+assembler.getLastAssemblyException());
            ContactBehaviorChecks.check(child.isPassenger() && level.getBlockState(origin.above()).isAir(),"native cart assembly owns passenger linkage and block removal");
            var before=cart.position();cart.setDeltaMovement(.2,0,0);
            DynamicCollisionObstacleSnapshot captured=null;
            for(int step=0;step<5;step++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
                cart.tick();child.rideTick();
                var domain=new Aabb3d(64,296,64,88,312,80);var obstacles=new ArrayList<DynamicCollisionObstacleSnapshot>();
                try(var subject=MinecraftCollisionSubject.open(actor)) {
                    RigidCollisionPublicationRegistry.capture(level,new ExternalRigidCollisionQuery(domain,domain,
                            KinematicStepContext.fullTick(level.getGameTime(),0)),obstacles::add);
                }
                var children=obstacles.stream().filter(p->p.providerNamespace().equals(CreateRigidCollisionProvider.ID)).toList();
                ContactBehaviorChecks.check(!children.isEmpty(),"native oriented cart publishes collision geometry");
                for(var obstacle:children) {
                    var local=obstacle.localBody().center();
                    var expected=child.toGlobalVector(new Vec3(local.x()+.5,local.y()+.5,local.z()+.5),1);
                    ContactBehaviorChecks.check(obstacle.bodyAt(1).center().distance(new Vec3d(expected.x,expected.y,expected.z))<1e-6,
                            "native cart translation and oriented rotation share collision endpoint");
                }
                captured=children.getFirst();
            }
            ContactBehaviorChecks.check(cart.position().distanceTo(before)>.1,"native rail/cart controller actually moves");
            var identity=captured.identity();child.disassemble();
            ContactBehaviorChecks.check(child.isRemoved() && cart.getPassengers().isEmpty(),"native oriented disassembly clears passenger and child");
            try(var subject=MinecraftCollisionSubject.open(actor)) {
                ContactBehaviorChecks.check(RigidCollisionPublicationRegistry.resolve(level,identity,
                        KinematicStepContext.fullTick(level.getGameTime(),0)).isEmpty(),"oriented removal retires collision identity");
            }
            System.out.println("CREATE_NATIVE_CART_PASSED assembly rail-controller oriented-passenger disassembly identity-retirement");
        } finally {
            if(child!=null && !child.isRemoved()) child.discard();cart.discard();actor.discard();
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(originalTime);
            states.forEach((position,state)->level.setBlock(position,state,18));
        }
    }
    private static void landingCallbackWorld(ServerLevel level,dev.ryanhcode.sable.sublevel.SubLevel parent,
                                             ControlledContraptionEntity vehicle) {
        var block=ControlBoundaryChecks.CONTACT_BLOCK.get();block.reset();
        var position=new BlockPos(2,0,0);
        vehicle.setBlock(position,new StructureTemplate.StructureBlockInfo(position,block.defaultBlockState(),null));
        vehicle.setAngle(0);vehicle.tick();parent.updateLastPose();parent.updateBoundingBox();
        var center=parent.logicalPose().transformPosition(vehicle.toGlobalVector(new Vec3(2.5,.5,.5),1));
        var actor=new ContactBehaviorChecks.Actor(level);actor.setPos(center.x,center.y+.8,center.z);
        actor.fallDistance=3;actor.setDeltaMovement(0,-1,0);
        actor.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,-1,0));
        ContactBehaviorChecks.check(block.falls==1 && block.responses==1,"contraption landing runs native callbacks once");
        ContactBehaviorChecks.check(block.fallWorld==vehicle.getContraption().getContraptionWorld()
                && block.responseWorld==block.fallWorld,"contraption callbacks receive their actual material world");
        System.out.println("CREATE_LANDING_WORLD_PASSED fall response actual-storage-world exactly-once");
    }
    private static void seat(ServerLevel level,dev.ryanhcode.sable.sublevel.SubLevel parent,
                             ControlledContraptionEntity vehicle,ContactBehaviorChecks.Actor actor) {
        actor.setPos(40,300,40);
        cc.sighs.gravityengine.gravity.integration.GravityApplicationCoordinator.applyDirectAssignment(actor,
                new cc.sighs.gravityengine.gravity.GravityState(new Vec3d(1,0,0),.08));
        vehicle.addSittingPassenger(actor,0);
        ContactBehaviorChecks.check(actor.getVehicle()==vehicle,"native Create seat boarding");
        cc.sighs.gravityengine.gravity.integration.GravityApplicationCoordinator.updateBody(actor);
        for(int step=0;step<2;step++) {
            if(step==1) { parent.updateLastPose();parent.logicalPose().position().add(.2,.1,0);parent.logicalPose().orientation().rotateY(.1);parent.updateBoundingBox(); }
            var nativePosition=vehicle.getPassengerPosition(actor,1).add(0,
                    com.simibubi.create.content.contraptions.actors.seat.SeatEntity.getCustomEntitySeatOffset(actor)-.125,0);
            var eyeOffset=actor.getEyePosition().subtract(actor.position());
            var expected=parent.logicalPose().transformPosition(nativePosition.add(eyeOffset)).subtract(eyeOffset);
            vehicle.positionRider(actor);
            ContactBehaviorChecks.check(actor.position().distanceTo(expected)<1e-7,"native vehicle owns mounted transform once actual="+actor.position()+" expected="+expected);
            var first=actor.position();vehicle.positionRider(actor);
            ContactBehaviorChecks.check(actor.position().distanceTo(first)<1e-9,"repeated native rider placement adds no GE carry");
        }
        actor.stopRiding();
        ContactBehaviorChecks.check(!actor.isPassenger(),"native Create dismount lifecycle");
        ContactBehaviorChecks.check(cc.sighs.gravityengine.gravity.integration.CharacterPlacementValidation.clearAt(actor,actor.position()),
                "native Create dismount leaves installed geometry clear");
        System.out.println("CREATE_SEAT_PASSED boarding parent-motion no-duplicate-placement dismount-clearance");
    }
    private static void attachedMass(ServerLevel level,dev.ryanhcode.sable.sublevel.ServerSubLevel parent,
                                     ControlledContraptionEntity vehicle) {
        var child=(dev.ryanhcode.sable.api.sublevel.KinematicContraption)vehicle;
        ContactBehaviorChecks.check(child.sable$getMassTracker()!=null && parent.getPlot().getContraptions().contains(child),
                "native contraption tick owns attached mass initialization");
        var system=SubLevelContainer.getContainer(level).physicsSystem();
        parent.updateMergedMassData((float)system.getPartialPhysicsTick());
        var points=((cc.sighs.gravityengine.gravity.integration.compat.sable.SableMassPoints.Access)child.sable$getMassTracker())
                .gravityengine$massPoints().snapshot();
        ContactBehaviorChecks.check(points!=null && points.size()==2,"native attached sparse mass capture");
        double phase=(float)system.getPartialPhysicsTick();
        var localPose=child.sable$getLocalPose(new Pose3d(),phase);
        var expected=new ArrayList<Vec3d>();
        for(var p:points) {
            var local=new org.joml.Vector3d(p.position().x(),p.position().y(),p.position().z());
            var world=parent.logicalPose().transformPosition(localPose.transformPosition(local));
            expected.add(new Vec3d(world.x,world.y,world.z));
        }
        var sampled=new ArrayList<Vec3d>();
        try(var publication=cc.sighs.gravityengine.api.GravityEngineApi.publish(level,com.example.examplemod.gravity.ProviderFixture.ID,
                cc.sighs.gravityengine.api.GravityFieldDefinition.block(ResourceLocation.fromNamespaceAndPath("gravityengine","attached_mass"),
                        BlockPos.ZERO,query->{sampled.add(query.position());return new cc.sighs.gravityengine.api.field.GravityFieldSample(Vec3d.ZERO);},
                        cc.sighs.gravityengine.api.field.GravityFields.infiniteInfluence(),cc.sighs.gravityengine.api.field.GravityFieldCompositionMode.OVERRIDE,1))) {
            var handle=system.getPhysicsHandle(parent);var before=handle.getLinearVelocity(new org.joml.Vector3d());
            cc.sighs.gravityengine.gravity.integration.compat.sable.SableGravityBridge.apply(parent,system,handle,.025);
            ContactBehaviorChecks.check(sampled.size()==3 && expected.stream().allMatch(p->sampled.stream().anyMatch(q->q.distance(p)<1e-7)),
                    "own and attached mass sampled once at native merged phase: "+sampled);
            ContactBehaviorChecks.check(handle.getLinearVelocity(new org.joml.Vector3d()).distance(before)>0,
                    "validated merged mass commits one gravity correction");
            var angularBefore=handle.getAngularVelocity(new org.joml.Vector3d());
            before=handle.getLinearVelocity(new org.joml.Vector3d());
            var last=sampled.getLast();
            com.example.examplemod.gravity.ProviderFixture.secondCoverage(level,query->query.position().distance(last)<1e-7
                    ?cc.sighs.gravityengine.api.field.FieldCoverage.INCOMPLETE:cc.sighs.gravityengine.api.field.FieldCoverage.COMPLETE);
            try {
                sampled.clear();
                cc.sighs.gravityengine.gravity.integration.compat.sable.SableGravityBridge.apply(parent,system,handle,.025);
                ContactBehaviorChecks.check(sampled.size()==3,"late incomplete attached query follows preceding complete points");
                ContactBehaviorChecks.check(handle.getLinearVelocity(new org.joml.Vector3d()).equals(before)
                                &&handle.getAngularVelocity(new org.joml.Vector3d()).equals(angularBefore),
                        "late incomplete attached evidence commits neither linear nor angular correction");
            } finally {
                com.example.examplemod.gravity.ProviderFixture.secondCoverage(level,query->cc.sighs.gravityengine.api.field.FieldCoverage.COMPLETE);
            }
        }
        attachedTrackerEdits(level,parent,child);
        attachedBlockEdits(parent,vehicle);
        System.out.println("CREATE_ATTACHED_MASS_PASSED native-initialization sparse-points merged-phase force-commit late-incomplete native-tracker-edits");
    }

    private static void attachedBlockEdits(dev.ryanhcode.sable.sublevel.ServerSubLevel parent,
            ControlledContraptionEntity vehicle) {
        var child=(dev.ryanhcode.sable.api.sublevel.KinematicContraption)vehicle;
        var saved=new java.util.HashMap<>(vehicle.getContraption().getBlocks());
        var address=saved.keySet().iterator().next();
        var original=dev.ryanhcode.sable.api.physics.mass.MassTracker.BLOCK_CENTER_OF_MASS;
        try {
            for(var state:java.util.List.of(Blocks.STONE_SLAB.defaultBlockState(),Blocks.GOLD_BLOCK.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(),saved.get(address).state())) {
                var before=child.sable$getMassTracker();
                vehicle.setBlock(address,new StructureTemplate.StructureBlockInfo(address,state,null));
                ContactBehaviorChecks.check(child.sable$getMassTracker()!=before,"Create edit rebuilds native attached properties");
                SableRuntimeChecks.checkMassPoints(child.sable$getMassTracker());
            }
            var before=child.sable$getMassTracker();
            var oldBlock=vehicle.getContraption().getBlocks().get(address);
            var deliberate=new IllegalStateException("attached native build failure");
            dev.ryanhcode.sable.api.physics.mass.MassTracker.BLOCK_CENTER_OF_MASS=(world,state)->{
                if(state.is(Blocks.GOLD_BLOCK)) throw deliberate;
                return original.apply(world,state);
            };
            try {
                vehicle.setBlock(address,new StructureTemplate.StructureBlockInfo(address,Blocks.GOLD_BLOCK.defaultBlockState(),null));
                throw new AssertionError("native property failure must propagate");
            } catch(IllegalStateException failure) {
                ContactBehaviorChecks.check(failure==deliberate,"native property failure preserves original cause");
            } finally {dev.ryanhcode.sable.api.physics.mass.MassTracker.BLOCK_CENTER_OF_MASS=original;}
            ContactBehaviorChecks.check(child.sable$getMassTracker()==before
                            && vehicle.getContraption().getBlocks().get(address)==oldBlock
                            && parent.getPlot().getContraptions().contains(child),
                    "failed edit restores native properties and Create block before publication");
            for(var position:saved.keySet()) vehicle.setBlock(position,
                    new StructureTemplate.StructureBlockInfo(position,Blocks.AIR.defaultBlockState(),null));
            ContactBehaviorChecks.check(child.sable$getMassTracker()==null&&!parent.getPlot().getContraptions().contains(child),
                    "last attached block removal retires native mass and pipeline membership");
            for(var entry:saved.entrySet()) vehicle.setBlock(entry.getKey(),entry.getValue());
            ContactBehaviorChecks.check(child.sable$getMassTracker()!=null&&parent.getPlot().getContraptions().contains(child),
                    "native attached properties revive after empty edit");
            SableRuntimeChecks.checkMassPoints(child.sable$getMassTracker());
            System.out.println("CREATE_ATTACHED_BLOCK_EDITS_PASSED native-setBlock rebuild rollback empty revival");
        } finally {
            dev.ryanhcode.sable.api.physics.mass.MassTracker.BLOCK_CENTER_OF_MASS=original;
            for(var entry:saved.entrySet()) vehicle.setBlock(entry.getKey(),entry.getValue());
        }
    }

    /** Exercises the attached native tracker API; this is not a claim that
     * Create setBlock rebuilds Sable's independently owned mass properties. */
    private static void attachedTrackerEdits(ServerLevel level,dev.ryanhcode.sable.sublevel.ServerSubLevel parent,
            dev.ryanhcode.sable.api.sublevel.KinematicContraption child) {
        var tracker=child.sable$getMassTracker();
        var access=(cc.sighs.gravityengine.gravity.integration.compat.sable.SableMassPoints.Access)tracker;
        var derived=access.gravityengine$massPoints();
        var original=dev.ryanhcode.sable.api.physics.mass.MassTracker.BLOCK_CENTER_OF_MASS;
        var address=new BlockPos(2,0,0);
        var previous=Blocks.STONE.defaultBlockState();
        dev.ryanhcode.sable.api.physics.mass.MassTracker.BLOCK_CENTER_OF_MASS=(world,state)->
                state.is(Blocks.STONE_SLAB)?new org.joml.Vector3d(.5,.25,.5):original.apply(world,state);
        try {
            for(var next:java.util.List.of(Blocks.STONE_SLAB.defaultBlockState(),Blocks.GOLD_BLOCK.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState())) {
                double oldMass=previous.isAir()?0:dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(level,address,previous);
                double newMass=next.isAir()?0:dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(level,address,next);
                derived.beginEdit();
                boolean success=false;
                try {
                    if(newMass!=0) tracker.addBlockMass(level,next,address,newMass,
                            dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getInertia(level,address,next));
                    ContactBehaviorChecks.check(derived.snapshot()==null,"attached replacement intermediate evidence withheld");
                    if(oldMass!=0) tracker.addBlockMass(level,previous,address,-oldMass,
                            dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getInertia(level,address,previous));
                    success=true;
                } finally {derived.endEdit(success);}
                SableRuntimeChecks.checkMassPoints(tracker);
                previous=next;
            }
            var system=SubLevelContainer.getContainer(level).physicsSystem();
            parent.updateMergedMassData((float)system.getPartialPhysicsTick());
            try(var publication=cc.sighs.gravityengine.api.GravityEngineApi.publish(level,com.example.examplemod.gravity.ProviderFixture.ID,
                    cc.sighs.gravityengine.api.GravityFieldDefinition.block(ResourceLocation.fromNamespaceAndPath("gravityengine","attached_edit"),
                            BlockPos.ZERO,cc.sighs.gravityengine.api.field.GravityFields.zeroGravity(),
                            cc.sighs.gravityengine.api.field.GravityFields.infiniteInfluence(),cc.sighs.gravityengine.api.field.GravityFieldCompositionMode.OVERRIDE,1))) {
                var handle=system.getPhysicsHandle(parent);
                var before=handle.getLinearVelocity(new org.joml.Vector3d());
                cc.sighs.gravityengine.gravity.integration.compat.sable.SableGravityBridge.apply(parent,system,handle,.025);
                var expected=dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level).mul(-.025);
                ContactBehaviorChecks.check(handle.getLinearVelocity(new org.joml.Vector3d()).sub(before).distance(expected)<1e-6,
                        "attached native edits retain exactly one full-body gravity correction");
            }
        } finally {dev.ryanhcode.sable.api.physics.mass.MassTracker.BLOCK_CENTER_OF_MASS=original;}
    }
}
