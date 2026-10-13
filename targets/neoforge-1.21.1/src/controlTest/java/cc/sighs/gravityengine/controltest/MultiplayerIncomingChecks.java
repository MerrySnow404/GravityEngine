package cc.sighs.gravityengine.controltest;

import cc.sighs.gravityengine.gravity.GravityState;
import cc.sighs.gravityengine.gravity.integration.GravityApplicationCoordinator;
import cc.sighs.gravityengine.network.GravitySyncService;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.joml.Vector3d;

/** Stationary real clients hit by an independently moving native physics wall. */
final class MultiplayerIncomingChecks {
    private static ServerSubLevel wall;
    private static int stage,ticks,clientReady;
    private static double initialX;
    private static final java.util.List<String> samples=new java.util.ArrayList<>();
    static boolean server(MinecraftServer server,List<ServerPlayer> players) {
        var level=server.overworld();var container=SubLevelContainer.getContainer(level);
        if(stage==0) {
            for(int x=50;x<=72;x++) for(int z=54;z<=70;z++) level.setBlock(new BlockPos(x,300,z),Blocks.STONE.defaultBlockState(),18);
            var pose=new Pose3d();pose.position().set(55,302.55,62);
            wall=(ServerSubLevel)container.allocateNewSubLevel(pose);
            var plot=wall.getPlot();plot.newEmptyChunk(plot.getCenterChunk());
            for(int y=0;y<3;y++) for(int z=-4;z<=4;z++) plot.getEmbeddedLevelAccessor().setBlock(new BlockPos(0,y,z),Blocks.STONE.defaultBlockState(),3);
            container.addForceLoadTicket(wall,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
            stage=1;ticks=0;return false;
        }
        if(stage<4) {
            var handle=container.physicsSystem().getPhysicsHandle(wall);
            double speed=stage==3 && wall.boundingBox().maxX()<initialX+1.2?10:0;
            handle.addLinearAndAngularVelocity(new Vector3d(speed,0,0).sub(handle.getLinearVelocity(new Vector3d())),
                    handle.getAngularVelocity(new Vector3d()).negate());
        }
        if(stage==1) {
            if(++ticks<20) return false;
            if(ticks==20) {
                // Assembly rebases the native COM. Align the completed collider,
                // not an assumed allocation origin, using the native teleport.
                var handle=container.physicsSystem().getPhysicsHandle(wall);
                handle.teleport(new Vector3d(wall.logicalPose().position()).add(0,301.05-wall.boundingBox().minY(),0),
                        wall.logicalPose().orientation());
                return false;
            }
            ContactBehaviorChecks.check(wall.boundingBox().minY()>301 && wall.boundingBox().minY()<301.1,
                    "incoming wall clears floor but intersects standing character height: minY="+wall.boundingBox().minY());
            initialX=wall.boundingBox().maxX()+2;
            MultiplayerChecks.write("incoming-source.txt",wall.getUniqueId()+"\n"+initialX);
            for(int i=0;i<players.size();i++) {
                var player=players.get(i);
                GravityApplicationCoordinator.applyDirectAssignment(player,GravityState.DEFAULT);GravitySyncService.syncPlayer(player);
                player.teleportTo(level,initialX,301,(wall.boundingBox().minZ()+wall.boundingBox().maxZ())*.5+(i==0?-2:2),0,0);player.setDeltaMovement(Vec3.ZERO);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            }
            stage=2;ticks=0;MultiplayerChecks.write("incoming-stage.txt","2");return false;
        }
        if(stage==2) {
            if(++ticks>240) throw new AssertionError("incoming wall clients did not establish stationary native ground");
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-incoming-ready.txt")) return false;
            stage=3;ticks=0;MultiplayerChecks.write("incoming-stage.txt","3");return false;
        }
        if(stage==3) {
            samples.add("tick="+level.getGameTime()+" source="+wall.getUniqueId()+" pose="+wall.logicalPose()+
                    " bounds="+wall.boundingBox().minX()+","+wall.boundingBox().minY()+","+wall.boundingBox().minZ()+":"+
                    wall.boundingBox().maxX()+","+wall.boundingBox().maxY()+","+wall.boundingBox().maxZ()+
                    " players="+players.stream().map(p->p.getGameProfile().getName()+":"+p.position()).toList());
            MultiplayerChecks.write("incoming-server-samples.txt",String.join("\n",samples));
            if(++ticks>240) throw new AssertionError("native incoming contact was not observed by both clients");
            for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-incoming-hit.txt")) return false;
            container.removeForceLoadTicket(wall,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
            container.removeSubLevel(wall,SubLevelRemovalReason.REMOVED);container.processSubLevelRemovals();
            stage=4;ticks=0;MultiplayerChecks.write("incoming-stage.txt","4");return false;
        }
        if(++ticks>240) throw new AssertionError("incoming native source removal was not delivered");
        for(String name:MultiplayerChecks.NAMES) if(!MultiplayerChecks.exists(name+"-incoming-removed.txt")) return false;
        return true;
    }
    static void client(Player actor) {
        MultiplayerClientChecks.platformInput(false,false);MultiplayerClientChecks.platformDismount(false);
        String name=actor.getGameProfile().getName();
        if(!MultiplayerChecks.exists("incoming-stage.txt")) return;
        try {
            var source=Files.readAllLines(MultiplayerChecks.ROOT.resolve("incoming-source.txt"));
            var body=SubLevelContainer.getContainer(actor.level()).getSubLevel(UUID.fromString(source.getFirst()));
            double start=Double.parseDouble(source.get(1));
            int phase=Integer.parseInt(Files.readString(MultiplayerChecks.ROOT.resolve("incoming-stage.txt")).trim());
            if(phase==4) {
                if(body==null) MultiplayerChecks.write(name+"-incoming-removed.txt","PASS native incoming source retired");
                return;
            }
            if(body==null) return;
            if(phase==2) {
                if(Math.abs(actor.getX()-start)>.01 || Math.abs(actor.getY()-301)>.01 || !actor.onGround()
                        || actor.getDeltaMovement().horizontalDistance()>.001) {clientReady=0;return;}
                if(++clientReady>=10) MultiplayerChecks.write(name+"-incoming-ready.txt","PASS stationary ground with native incoming source received");
                return;
            }
            samples.add("tick="+actor.level().getGameTime()+" source="+body.getUniqueId()+" pose="+body.logicalPose()+
                    " position="+actor.position()+" velocity="+actor.getDeltaMovement()+" players="+
                    actor.level().players().stream().map(p->p.getGameProfile().getName()+":"+p.position()).toList());
            MultiplayerChecks.write(name+"-incoming-samples.txt",String.join("\n",samples));
            boolean hit=actor.getX()>start+.25;
            for(String other:MultiplayerChecks.NAMES) {
                var player=actor.level().players().stream().filter(p->p.getGameProfile().getName().equals(other)).findFirst().orElse(null);
                hit &= player!=null && player.getX()>start+.25;
            }
            if(hit) {
                var extension=(dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)actor;
                if(!(extension.sable$getCollisionInfo() instanceof cc.sighs.gravityengine.gravity.integration.compat.sable.SableCollisionOwnership.EngineCollisionInfo))
                    throw new AssertionError("incoming character used a second native collision owner");
                MultiplayerChecks.write(name+"-incoming-hit.txt","PASS no-input native moving wall pushes self and observer displacement="+(actor.getX()-start));
            }
        } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
    }
}
