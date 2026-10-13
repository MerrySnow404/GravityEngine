package cc.sighs.gravityengine.controltest;

import cc.sighs.gravityengine.api.GravityEngineApi;
import cc.sighs.gravityengine.api.GravityFieldDefinition;
import cc.sighs.gravityengine.api.field.*;
import cc.sighs.gravityengine.gravity.GravityState;
import cc.sighs.gravityengine.gravity.integration.GravityApplicationCoordinator;
import cc.sighs.gravityengine.gravity.minecraft.access.GravityEntityAccess;
import cc.sighs.gravityengine.gravity.minecraft.geometry.GravityEntityGeometry;
import cc.sighs.gravityengine.network.GravitySyncService;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.*;

/** Actual networked native rigid-body motion. Files coordinate test phases only;
 * poses, geometry, support and player state travel through production protocols. */
final class MultiplayerPlatformChecks {
    static final boolean ENABLED=Boolean.getBoolean("gravityengine.multiplayerPlatforms");
    static final boolean CHILDREN=Boolean.getBoolean("gravityengine.multiplayerChildPlatforms");
    private static ServerSubLevel serverBody;
    private static int ticks,stage,observed;
    private static Vec3 initialLocal;
    private static Vec3 walkingStart;
    private static int actionTicks,waypoint,mountedTicks,retrackedTicks;
    private static Vec3 walkingPrevious;
    private static double walkingDistance;
    private static SubLevel originalClientBody;
    private static net.minecraft.world.entity.Entity originalClientChild;
    private static final List<String> samples=new ArrayList<>();
    static boolean server(MinecraftServer server,List<ServerPlayer> players) {
        var level=server.overworld();var container=SubLevelContainer.getContainer(level);
        if(serverBody==null) {
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-ready.txt")) return false;
            var pose=new Pose3d();pose.position().set(16,300,16);
            serverBody=(ServerSubLevel)container.allocateNewSubLevel(pose);
            var plot=serverBody.getPlot();plot.newEmptyChunk(plot.getCenterChunk());
            for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++)
                plot.getEmbeddedLevelAccessor().setBlock(new BlockPos(x,0,z),Blocks.STONE.defaultBlockState(),3);
            if(CHILDREN) CreateMultiplayerPlatform.assemble(serverBody);
            GravityEngineApi.publish(level,com.example.examplemod.gravity.ProviderFixture.ID,
                    GravityFieldDefinition.block(ResourceLocation.fromNamespaceAndPath("gravityengine","multiplayer_platform"),
                            BlockPos.ZERO,GravityFields.zeroGravity(),GravityFields.infiniteInfluence(),GravityFieldCompositionMode.OVERRIDE,1));
            container.addForceLoadTicket(serverBody,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,
                    net.minecraft.util.Unit.INSTANCE);
            MultiplayerChecks.write("platform-source.txt",serverBody.getUniqueId().toString());
            for(var player:players) {
                player.getAbilities().invulnerable=true;player.getAbilities().flying=false;player.getAbilities().mayfly=false;
                player.onUpdateAbilities();
                GravityApplicationCoordinator.applyDirectAssignment(player,GravityState.DEFAULT);
                GravitySyncService.syncPlayer(player);
                // Fresh-world spawn may lie outside native entity tracking
                // range. Enter the platform neighborhood before awaiting its
                // replicated children; do not manufacture tracking readiness.
                player.teleportTo(level,16+players.indexOf(player)*4,310,16,0,0);
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            }
            stage=1;ticks=0;MultiplayerChecks.write("phase.txt","1");
            return false;
        }
        if(stage>=2 && stage!=5 && stage!=10) {
            var time=cc.sighs.gravityengine.gravity.kinematic.KinematicStepContext.fullTick(level.getGameTime(),0);
            var parentMotion=cc.sighs.gravityengine.gravity.integration.compat.sable.SableRigidCollisionProvider.captureMotion(serverBody,time);
            samples.add("server stage="+stage+" source="+serverBody.getUniqueId()+" "+describe(parentMotion)+
                    " players="+players.stream().map(MultiplayerPlatformChecks::actorFacts).toList()+
                    (CHILDREN?CreateMultiplayerPlatform.serverMotion(time):""));
            MultiplayerChecks.write("platform-server-samples.txt",String.join("\n",samples));
        }
        // Use the same native controller during setup and measurement. Starting
        // it only after landing abruptly brakes residual vertical motion and
        // correctly releases a character retaining its upward world momentum.
        if(stage<3) drive(container.physicsSystem().getPhysicsHandle(serverBody),0,0);
        if(stage==1) {
            if(++ticks>600) throw new AssertionError("native platform sources not received within 600 ticks");
            if(ticks<60) return false;
            cc.sighs.gravityengine.gravity.integration.compat.sable.SableRigidCollisionProvider.captureMotion(serverBody,
                    cc.sighs.gravityengine.gravity.kinematic.KinematicStepContext.fullTick(level.getGameTime(),0));
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-seen.txt")) return false;
            var box=serverBody.boundingBox();
            for(int i=0;i<players.size();i++) {
                var player=players.get(i);
                var target=CHILDREN?CreateMultiplayerPlatform.placement(serverBody,i):new Vec3(
                        (box.minX()+box.maxX())*.5+(i==0?-2:2),box.maxY()+.05,(box.minZ()+box.maxZ())*.5);
                MultiplayerChecks.write(player.getGameProfile().getName()+"-platform-placement.txt",
                        target.x+","+target.y+","+target.z);
                player.teleportTo(level,target.x,target.y,target.z,0,0);
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            }
            stage=2;ticks=0;MultiplayerChecks.write("platform-stage.txt","2");return false;
        }
        if(stage==2) {
            if(++ticks>600) throw new AssertionError("clients did not establish platform support within 600 ticks");
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-grounded.txt")) return false;
            stage=3;ticks=0;MultiplayerChecks.write("platform-stage.txt","3");
        }
        if(stage==4) {
            drive(container.physicsSystem().getPhysicsHandle(serverBody),.4,.1);
            if(++ticks>360) throw new AssertionError("clients did not finish native walking/jump/edit checks");
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-jump.txt")) return false;
            if(CHILDREN) {
                CreateMultiplayerPlatform.board(players);
                stage=6;ticks=0;MultiplayerChecks.write("platform-stage.txt","6");return false;
            }
            leaveTracking(level,players);return false;
        }
        if(stage==6) {
            drive(container.physicsSystem().getPhysicsHandle(serverBody),.4,.1);
            if(++ticks>240) throw new AssertionError("native mounted transforms did not converge on both clients");
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-seat.txt")) return false;
            stage=7;ticks=0;MultiplayerChecks.write("platform-stage.txt","7");return false;
        }
        if(stage==7) {
            MultiplayerChecks.write("platform-server-dismount-state.txt",players.stream().map(p->p.getGameProfile().getName()+" vehicle="+p.getVehicle()+" position="+p.position()).toList().toString());
            drive(container.physicsSystem().getPhysicsHandle(serverBody),0,0);
            if(++ticks>240) throw new AssertionError("native client Shift dismount did not finish");
            for(var player:players) if(player.isPassenger()) return false;
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-dismount.txt")) return false;
            leaveTracking(level,players);return false;
        }
        if(stage==8) {
            drive(container.physicsSystem().getPhysicsHandle(serverBody),0,0);
            if(++ticks>360) throw new AssertionError("native source did not leave client tracking");
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-untracked.txt")) return false;
            for(int i=0;i<players.size();i++) {
                var player=players.get(i);var position=serverBody.logicalPose().position();
                player.teleportTo(level,position.x+i*4,position.y+10,position.z,0,0);
                player.setDeltaMovement(Vec3.ZERO);player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            }
            stage=11;ticks=0;MultiplayerChecks.write("platform-stage.txt","11");return false;
        }
        if(stage==11) {
            drive(container.physicsSystem().getPhysicsHandle(serverBody),0,0);
            if(++ticks>360) throw new AssertionError("native source did not return to tracking");
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-received-again.txt")) return false;
            for(int i=0;i<players.size();i++) {
                var player=players.get(i);var box=serverBody.boundingBox();
                var target=CHILDREN?CreateMultiplayerPlatform.placement(serverBody,i):new Vec3(
                        (box.minX()+box.maxX())*.5+(i==0?-2:2),box.maxY()+.05,(box.minZ()+box.maxZ())*.5);
                // Server and client native prediction need not share an exact
                // surface pose on arrival. Start a real fall clearly above it.
                target=target.add(0,2,0);
                MultiplayerChecks.write(player.getGameProfile().getName()+"-platform-retrack-placement.txt","target="+target+" parent="+serverBody.logicalPose()+
                        " plot="+level.getBlockState(serverBody.getPlot().getCenterBlock().offset(i==0?-2:4,3,0)));
                player.teleportTo(level,target.x,target.y,target.z,0,0);
                GravityApplicationCoordinator.applyDirectAssignment(player,GravityState.DEFAULT);GravitySyncService.syncPlayer(player);
                player.setDeltaMovement(Vec3.ZERO);player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            }
            stage=9;ticks=0;MultiplayerChecks.write("platform-stage.txt","9");return false;
        }
        if(stage==9) {
            drive(container.physicsSystem().getPhysicsHandle(serverBody),0,0);
            if(++ticks>360) throw new AssertionError("replacement native source did not restore support");
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-retracked.txt")) return false;
            if(CHILDREN) CreateMultiplayerPlatform.disassemble(serverBody);
            container.removeForceLoadTicket(serverBody,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,
                    net.minecraft.util.Unit.INSTANCE);
            container.removeSubLevel(serverBody,dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            container.processSubLevelRemovals();
            for(var player:players) {
                GravityApplicationCoordinator.applyDirectAssignment(player,new GravityState(GravityState.DEFAULT_DOWN,0));
                GravitySyncService.syncPlayer(player);
            }
            stage=5;ticks=0;MultiplayerChecks.write("platform-stage.txt","5");return false;
        }
        if(stage==5) {
            if(++ticks>240) throw new AssertionError("clients did not retire removed native sources");
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-retired.txt")) return false;
            stage=10;ticks=0;MultiplayerChecks.write("platform-stage.txt","10");return false;
        }
        if(stage==10) {
            if(!MultiplayerIncomingChecks.server(server,players)) return false;
            MultiplayerChecks.write("server-PASS.txt","PASS native motion: reversal rotation edits continuous-walk jump"+(CHILDREN?" seat":"")+" tracking-replacement removal incoming-contact, two clients");
            MultiplayerChecks.write("phase.txt","9");return true;
        }
        int frame=ticks++;
        double speed=frame<60?2:frame<120?-2:0;
        double angular=frame>=180&&frame<240?.4:frame>=240&&frame<300?-.4:0;
        var handle=container.physicsSystem().getPhysicsHandle(serverBody);
        drive(handle,speed,angular);
        MultiplayerChecks.write("platform-frame.txt",Integer.toString(frame));
        if(frame<340) return false;
        for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-platform-PASS.txt")) return false;
        if(CHILDREN) CreateMultiplayerPlatform.edit();
        else serverBody.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO,Blocks.GOLD_BLOCK.defaultBlockState(),3);
        stage=4;ticks=0;MultiplayerChecks.write("platform-stage.txt","4");return false;
    }
    private static void leaveTracking(net.minecraft.server.level.ServerLevel level,List<ServerPlayer> players) {
        for(int i=0;i<players.size();i++) {
            var player=players.get(i);
            GravityApplicationCoordinator.applyDirectAssignment(player,new GravityState(GravityState.DEFAULT_DOWN,0));
            GravitySyncService.syncPlayer(player);
            player.teleportTo(level,4096+i*4,310,4096,0,0);player.setDeltaMovement(Vec3.ZERO);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        }
        stage=8;ticks=0;MultiplayerChecks.write("platform-stage.txt","8");
    }
    private static void drive(dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle,double speed,double angular) {
        handle.addLinearAndAngularVelocity(new Vector3d(speed,0,0).sub(handle.getLinearVelocity(new Vector3d())),
                new Vector3d(0,angular,0).sub(handle.getAngularVelocity(new Vector3d())));
    }
    static void placementApplied(Player actor,net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet) {
        if(!ENABLED) return;
        String name=actor.getGameProfile().getName();
        MultiplayerChecks.write(name+"-platform-teleport-observed.txt",packet.getId()+","+packet.getX()+","+packet.getY()+","+packet.getZ()+" actual="+actor.position());
        var path=MultiplayerChecks.ROOT.resolve(name+"-platform-placement.txt");
        try {
            if(!java.nio.file.Files.exists(path)) return;
            var target=java.nio.file.Files.readString(path).trim().split(",");
            var expected=new Vec3(Double.parseDouble(target[0]),Double.parseDouble(target[1]),Double.parseDouble(target[2]));
            if(actor.position().distanceTo(expected)<1e-5)
                MultiplayerChecks.write(name+"-platform-placement-ack.txt","native teleport applied id="+packet.getId());
        } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
    }
    static String describe(cc.sighs.gravityengine.gravity.collision.RigidTrajectory motion) {
        return "tick="+motion.tick()+" interval="+motion.intervalTicks()+" revision="+motion.revision()+" continuity="+motion.continuityEpoch()+
                " pose0="+motion.poseAt(0).center()+" q0="+cc.sighs.gravityengine.math.geometry.BodyOrientation3d.quaternion(motion.poseAt(0).orientation())+
                " pose1="+motion.poseAt(1).center()+" q1="+cc.sighs.gravityengine.math.geometry.BodyOrientation3d.quaternion(motion.poseAt(1).orientation());
    }
    private static String actorFacts(Player actor) {
        var runtime=GravityEntityAccess.cast(actor).gravityengine$gravityComponent().operationState();
        return actor.getGameProfile().getName()+" entityTick="+actor.tickCount+" position="+actor.position()+" velocity="+actor.getDeltaMovement()+
                " credit="+runtime.supportVelocityContribution()+" contact="+runtime.restingContactSnapshot();
    }
    static void client(Player actor) {
        String name=actor.getGameProfile().getName();
        if(!MultiplayerChecks.exists("platform-source.txt")) return;
        try {
            var id=UUID.fromString(java.nio.file.Files.readString(MultiplayerChecks.ROOT.resolve("platform-source.txt")).trim());
            var body=SubLevelContainer.getContainer(actor.level()).getSubLevel(id);
            int phase=MultiplayerChecks.exists("platform-stage.txt")?Integer.parseInt(
                    java.nio.file.Files.readString(MultiplayerChecks.ROOT.resolve("platform-stage.txt")).trim()):0;
            if(phase==10) {MultiplayerIncomingChecks.client(actor);return;}
            if(phase==8) {
                MultiplayerClientChecks.platformInput(false,false);MultiplayerClientChecks.platformDismount(false);
                if(body==null && (!CHILDREN || CreateMultiplayerPlatform.child(actor)==null)
                        && GravityEntityAccess.cast(actor).gravityengine$gravityComponent().operationState().persistentSupportState()==null)
                    MultiplayerChecks.write(name+"-platform-untracked.txt","PASS native source and support absent outside tracking range");
                return;
            }
            if(phase==5) {
                MultiplayerClientChecks.platformInput(false,false);
                var runtime=GravityEntityAccess.cast(actor).gravityengine$gravityComponent().operationState();
                if(body==null && (!CHILDREN || CreateMultiplayerPlatform.child(actor)==null)
                        && runtime.persistentSupportState()==null
                        && cc.sighs.gravityengine.gravity.minecraft.GravityFrameAccess.authoritativeFrame(actor).strength()==0)
                    MultiplayerChecks.write(name+"-platform-retired.txt","PASS native removal and fresh zero DIRECT assignment, no stale support");
                return;
            }
            if(body==null) return;
            if(phase==11) {
                MultiplayerClientChecks.platformInput(false,false);MultiplayerClientChecks.platformDismount(false);
                if(body!=originalClientBody && (!CHILDREN || CreateMultiplayerPlatform.child(actor)!=null
                        && CreateMultiplayerPlatform.child(actor)!=originalClientChild))
                    MultiplayerChecks.write(name+"-platform-received-again.txt","native source objects replaced and received while zero gravity preserves fixture position");
                return;
            }
            if(phase==9) MultiplayerChecks.write(name+"-platform-retrack-state.txt","position="+actor.position()+" frame="+
                    cc.sighs.gravityengine.gravity.minecraft.GravityFrameAccess.authoritativeFrame(actor)+" body="+body.getUniqueId()+
                    " parentPose="+body.logicalPose()+" child="+(CHILDREN?CreateMultiplayerPlatform.child(actor):null)+
                    " expected="+(CHILDREN&&CreateMultiplayerPlatform.child(actor)!=null?CreateMultiplayerPlatform.world(actor,body,new Vec3(1.5,1.05,.5)):null)+
                    " canCollide="+(CHILDREN&&CreateMultiplayerPlatform.child(actor)!=null?CreateMultiplayerPlatform.child(actor).canCollideWith(actor):false)+
                    " plot="+actor.level().getBlockState(body.getPlot().getCenterBlock().offset(name.equals(MultiplayerChecks.NAMES[0])?-2:4,3,0))+" support="+
                    GravityEntityAccess.cast(actor).gravityengine$gravityComponent().operationState().persistentSupportState());
            if(!cc.sighs.gravityengine.gravity.minecraft.GravityFrameAccess.authoritativeFrame(actor).isDefault()) return;
            if(CHILDREN&&CreateMultiplayerPlatform.child(actor)==null) return;
            MultiplayerChecks.write(name+"-platform-seen.txt","native source received "+id);
            if(phase<2) return;
            var runtime=GravityEntityAccess.cast(actor).gravityengine$gravityComponent().operationState();
            var local=CHILDREN?CreateMultiplayerPlatform.local(actor,body):body.logicalPose().transformPositionInverse(actor.position());
            if(originalClientBody==null) {originalClientBody=body;originalClientChild=CHILDREN?CreateMultiplayerPlatform.child(actor):null;}
            if(phase==6) {
                MultiplayerClientChecks.platformInput(false,false);
                boolean valid=true;
                for(String other:MultiplayerChecks.NAMES) {
                    var player=actor.level().players().stream().filter(p->p.getGameProfile().getName().equals(other)).findFirst().orElse(null);
                    valid &= player!=null && CreateMultiplayerPlatform.nativeMountedPosition(player,body);
                }
                if(!valid) {mountedTicks=0;return;}
                if(++mountedTicks>=40) MultiplayerChecks.write(name+"-platform-seat.txt","PASS native mounted transforms for self and observer, parent and child moving");
                return;
            }
            if(phase==7) {
                MultiplayerClientChecks.platformInput(false,false);MultiplayerClientChecks.platformDismount(actor.isPassenger());
                boolean clear=!actor.isPassenger() && cc.sighs.gravityengine.gravity.integration.CharacterPlacementValidation.clearAt(actor,actor.position());
                MultiplayerChecks.write(name+"-platform-dismount-state.txt","vehicle="+actor.getVehicle()+" position="+actor.position()+
                        " pose="+actor.getPose()+" shift="+actor.isShiftKeyDown()+" clear="+clear+" local="+local+" support="+runtime.persistentSupportState());
                if(!actor.isPassenger() && MultiplayerChecks.exists(name+"-platform-dismount-commit.txt"))
                    MultiplayerChecks.write(name+"-platform-dismount.txt","PASS native Shift removal and validated-or-retained placement; currentClear="+clear);
                return;
            }
            if(phase==9) {
                var time=cc.sighs.gravityengine.gravity.kinematic.KinematicStepContext.fullTick(actor.level().getGameTime(),0);
                var trajectory=CHILDREN?cc.sighs.gravityengine.gravity.integration.compat.create.CreateRigidCollisionProvider.trajectory(CreateMultiplayerPlatform.child(actor),time,0,0)
                        :cc.sighs.gravityengine.gravity.integration.compat.sable.SableRigidCollisionProvider.captureMotion(body,time);
                samples.add("retrack,"+actor.tickCount+","+time.gameTick()+",position="+actor.position()+",velocity="+actor.getDeltaMovement()+
                        ",credit="+runtime.supportVelocityContribution()+",support="+runtime.persistentSupportState()+",start="+trajectory.poseAt(0)+",end="+trajectory.poseAt(1));
                MultiplayerChecks.write(name+"-platform-samples.txt",String.join("\n",samples));
                MultiplayerClientChecks.platformDismount(false);MultiplayerClientChecks.platformInput(false,false);
                if(body==originalClientBody || CHILDREN&&CreateMultiplayerPlatform.child(actor)==originalClientChild)
                    throw new AssertionError("native tracking did not replace source objects");
                if(runtime.movementMode()!=cc.sighs.gravityengine.gravity.integration.MovementModeIntegration.desired(actor))
                    throw new AssertionError("local travel retained stale native locomotion mode after dismount/retracking: "+runtime.movementMode());
                if(runtime.persistentSupportState()==null || !runtime.persistentSupportState().identity().providerNamespace()
                        .equals(CHILDREN?"gravityengine:create":"gravityengine:sable")) {retrackedTicks=0;return;}
                if(++retrackedTicks>=30) MultiplayerChecks.write(name+"-platform-retracked.txt","PASS fresh native body/chunks/child restore current support");
                return;
            }
            if(phase==4) {
                if(CHILDREN && !CreateMultiplayerPlatform.edited(actor)) return;
                if(!CHILDREN && !body.getLevel().getBlockState(body.getPlot().getCenterBlock()).is(Blocks.GOLD_BLOCK)) return;
                if(walkingStart==null) walkingStart=local;
                int action=actionTicks++;
                samples.add("walk="+action+" local="+local+" "+actorFacts(actor)+" support="+runtime.persistentSupportState()+
                        " parent="+describe(cc.sighs.gravityengine.gravity.integration.compat.sable.SableRigidCollisionProvider.captureMotion(body,
                                cc.sighs.gravityengine.gravity.kinematic.KinematicStepContext.fullTick(actor.level().getGameTime(),0))));
                MultiplayerChecks.write(name+"-platform-samples.txt",String.join("\n",samples));
                if(walkingPrevious!=null) walkingDistance+=local.distanceTo(walkingPrevious);
                walkingPrevious=local;
                if(action<80) {
                    Vec3[] targets={new Vec3(.3,local.y,.5),new Vec3(.3,local.y,1.3),
                            new Vec3(1.3,local.y,1.3),new Vec3(1.3,local.y,.3)};
                    if(!CHILDREN) targets=new Vec3[]{walkingStart.add(1,0,0),walkingStart.add(1,0,1),walkingStart.add(0,0,1),walkingStart};
                    var target=targets[waypoint%targets.length];
                    if(local.distanceTo(target)<.25) target=targets[++waypoint%targets.length];
                    var world=CHILDREN?CreateMultiplayerPlatform.world(actor,body,target):body.logicalPose().transformPosition(target);
                    var direction=world.subtract(actor.position());
                    actor.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
                }
                MultiplayerClientChecks.platformInput(action<80,action==101);
                if(action==100) {
                    if(walkingDistance<2 || runtime.persistentSupportState()==null)
                        throw new AssertionError("continuous native walk must retain edited moving support distance="+walkingDistance+" local="+local);
                    MultiplayerChecks.write(name+"-platform-walk.txt","PASS 80 input ticks relative path="+walkingDistance+" parent and child motion");
                }
                if(action>101 && actor.getDeltaMovement().y>.1 && runtime.persistentSupportState()==null)
                    MultiplayerChecks.write(name+"-platform-jump.txt","PASS native jump releases support velocity="+actor.getDeltaMovement());
                if(action>180 && !MultiplayerChecks.exists(name+"-platform-jump.txt")) throw new AssertionError("native jump did not release support");
                return;
            }
            if(phase==2) {
                MultiplayerChecks.write(name+"-platform-setup.txt","position="+actor.position()+" velocity="+actor.getDeltaMovement()+
                        " local="+local+" parent="+body.logicalPose()+" support="+runtime.persistentSupportState()+
                        (CHILDREN?" child="+CreateMultiplayerPlatform.child(actor).position():""));
                // Observe native teleport acknowledgement; a rotating child
                // may move between server placement and client landing.
                if(MultiplayerChecks.exists(name+"-platform-placement-ack.txt") && runtime.persistentSupportState()!=null
                        && runtime.persistentSupportState().identity().providerNamespace().equals(CHILDREN?"gravityengine:create":"gravityengine:sable")) {
                    if(CHILDREN && !MultiplayerChecks.exists(name+"-deferred-dismount.txt")) CreateMultiplayerPlatform.deferredPlacementCheck(actor,body);
                    initialLocal=local;
                    MultiplayerChecks.write(name+"-platform-grounded.txt","native landing support="+runtime.persistentSupportState());
                }
                return;
            }
            if(initialLocal==null) throw new AssertionError("platform motion began without local support");
            int frame=MultiplayerChecks.exists("platform-frame.txt")?Integer.parseInt(
                    java.nio.file.Files.readString(MultiplayerChecks.ROOT.resolve("platform-frame.txt")).trim()):0;
            var motion=cc.sighs.gravityengine.gravity.integration.compat.sable.SableRigidCollisionProvider.captureMotion(body,
                    cc.sighs.gravityengine.gravity.kinematic.KinematicStepContext.fullTick(actor.level().getGameTime(),0));
            samples.add("frame="+frame+" parent="+body.getUniqueId()+" "+describe(motion)+" "+actorFacts(actor)+" local="+local+
                    " support="+runtime.persistentSupportState()+(CHILDREN?" child="+CreateMultiplayerPlatform.child(actor).getUUID()+" "+describe(
                    cc.sighs.gravityengine.gravity.integration.compat.create.CreateRigidCollisionProvider.trajectory(CreateMultiplayerPlatform.child(actor),
                            cc.sighs.gravityengine.gravity.kinematic.KinematicStepContext.fullTick(actor.level().getGameTime(),0),0,0)):""));
            MultiplayerChecks.write(name+"-platform-samples.txt",String.join("\n",samples));
            if(runtime.persistentSupportState()==null) throw new AssertionError("moving platform lost support at frame "+frame);
            if(local.distanceTo(initialLocal)>.1) throw new AssertionError("moving platform local drift at frame "+frame+
                    " local="+local+" initial="+initialLocal);
            if(++observed>=260&&frame>=330)
                MultiplayerChecks.write(name+"-platform-PASS.txt","PASS observed="+observed+" relative drift <= .1 block");
        } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
    }
}
