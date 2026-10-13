package cc.sighs.gravityengine.controltest;

import cc.sighs.gravityengine.gravity.minecraft.GravityFrameAccess;
import cc.sighs.gravityengine.gravity.minecraft.geometry.GravityEntityGeometry;
import io.netty.channel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber(modid="gravityengine_control_tests",value=Dist.CLIENT)
public final class MultiplayerClientChecks {
    static void platformInput(boolean walk,boolean jump) {
        var options=Minecraft.getInstance().options;
        options.keyUp.setDown(walk);options.keyJump.setDown(jump);
    }
    static void platformDismount(boolean pressed) { Minecraft.getInstance().options.keyShift.setDown(pressed); }
    public static void dismountApplied(net.minecraft.world.entity.player.Player actor,net.minecraft.world.phys.Vec3 before,net.minecraft.world.entity.Pose pose) {
        boolean retained=actor.position().equals(before) && actor.getPose()==pose;
        boolean clear=cc.sighs.gravityengine.gravity.integration.CharacterPlacementValidation.clearAt(actor,actor.position());
        if(!retained && !clear) throw new AssertionError("native removal introduced an unvalidated anchor/pose: "+before+" -> "+actor.position());
        MultiplayerChecks.write(actor.getGameProfile().getName()+"-platform-dismount-commit.txt",
                "PASS native removal, retained="+retained+" clear="+clear+" before="+before+" after="+actor.position());
    }
    public static void placementApplied(ClientboundPlayerPositionPacket packet) {
        if(MultiplayerChecks.ENABLED && Minecraft.getInstance().player!=null)
            MultiplayerPlatformChecks.placementApplied(Minecraft.getInstance().player,packet);
    }
    private static boolean connecting,finished,reconnected,respawned,environmentCoverageChecked;
    private static int current,stable,offlineTicks,observations;
    private static long began;
    private static Connection observed;
    private static final AtomicInteger delayed=new AtomicInteger();
    private static final AtomicInteger udpDelivered=new AtomicInteger();
    private static final int delayMillis=Integer.getInteger("gravityengine.multiplayerDelayMillis",100);
    private static net.minecraft.world.phys.Vec3 previousAnchor,previousVelocity;
    private static int zeroMomentumChecks,unavailableFreezes;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!MultiplayerChecks.ENABLED||finished) return;
        var mc=Minecraft.getInstance();
        var name=mc.getUser().getName();
        if(began==0) began=System.nanoTime();
        try {
            if(System.nanoTime()-began>420_000_000_000L) throw new AssertionError("client deadline phase="+current+" player="+(mc.player==null?null:mc.player.position()));
            int phase=MultiplayerChecks.phase();
            if(phase==9) {
                if(delayed.get()==0) throw new AssertionError("no real inbound TCP packets observed");
                if(udpDelivered.get()==0) throw new AssertionError("no real inbound Sable UDP datagrams observed");
                MultiplayerChecks.write(name+"-PASS.txt","PASS "+(MultiplayerPlatformChecks.ENABLED?"moving-platform":"phases=8")+
                        " inboundDelayMillis="+delayMillis+" tcpDelivered="+delayed.get()+" udpDelivered="+udpDelivered.get());
                finished=true;mc.stop();return;
            }
            if(phase==6&&!reconnected&&mc.player!=null) {
                reconnected=true;mc.disconnect(new TitleScreen());connecting=false;offlineTicks=40;return;
            }
            if(offlineTicks>0) {offlineTicks--;return;}
            if(mc.player==null) {
                if(!connecting&&mc.getOverlay()==null&&mc.screen!=null) {
                    connecting=true;
                    String address="127.0.0.1:"+Integer.getInteger("gravityengine.multiplayerPort");
                    ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),
                            new ServerData("GE verification",address,ServerData.Type.OTHER),false,null);
                }
                return;
            }
            mc.options.pauseOnLostFocus=false;
            mc.setWindowActive(true);
            if(!environmentCoverageChecked) {
                var probe=new net.minecraft.world.entity.decoration.ArmorStand(mc.level,1_000_000,304,1_000_000);
                boolean refused=false;
                try {
                    cc.sighs.gravityengine.gravity.integration.MovementEnvironmentCapture.capture(probe,
                            new cc.sighs.gravityengine.gravity.collision.CollisionWorkTracker(
                                    cc.sighs.gravityengine.gravity.collision.CollisionWorkBudget.defaults()));
                } catch(cc.sighs.gravityengine.gravity.collision.CollisionSceneCoverageException expected) {
                    refused=expected.reason()==cc.sighs.gravityengine.gravity.collision.CollisionSceneCoverageException.Reason.UNAVAILABLE_BLOCK_DATA;
                } finally {probe.discard();}
                if(!refused) throw new AssertionError("unloaded client environment must explicitly reject coverage");
                environmentCoverageChecked=true;
                MultiplayerChecks.write(name+"-environment-PASS.txt","PASS actual client chunk-source absence, no empty-fluid success");
            }
            if(phase==0 && mc.player.tickCount>40 && mc.level.hasChunkAt(mc.player.blockPosition()))
                MultiplayerChecks.write(name+"-ready.txt","native client spawn and current chunk loaded");
            if(mc.player.isDeadOrDying()) {respawned=true;mc.player.respawn();return;}
            if(observed!=mc.player.connection.getConnection()) {
                observed=mc.player.connection.getConnection();
                observed.channel().pipeline().addBefore("packet_handler","ge_multiplayer_delay",
                        new OrderedInboundDelay(delayMillis,delayed));
            }
            var udp=((dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension)observed).sable$getUDPChannel();
            if(udp!=null) udp.eventLoop().execute(()-> {
                if(udp.isActive() && udp.pipeline().get("ge_multiplayer_udp_delay")==null)
                    udp.pipeline().addFirst("ge_multiplayer_udp_delay",new OrderedInboundDelay(delayMillis,udpDelivered));
            });
            if(MultiplayerPlatformChecks.ENABLED) {
                MultiplayerPlatformChecks.client(mc.player);
                var anchor=mc.player.position();var body=GravityEntityGeometry.body(mc.player);
                mc.gameRenderer.getMainCamera().setup(mc.level,mc.player,false,false,.5f);
                if(!anchor.equals(mc.player.position())||!body.equals(GravityEntityGeometry.body(mc.player)))
                    throw new AssertionError("platform camera mutated physical pose");
                return;
            }
            if(phase==0||phase>8) return;
            if(current!=phase) {current=phase;stable=0;}
            if(Boolean.getBoolean("gravityengine.multiplayerConcurrentAssignment") && observations<50)
                System.out.println("MULTIPLAYER_EARLY phase="+phase+" tick="+mc.player.tickCount+
                        " id="+mc.player.getId()+" p="+mc.player.position()+" v="+mc.player.getDeltaMovement()+
                        " frame="+GravityFrameAccess.authoritativeFrame(mc.player));
            if(++observations%100==0) {
                String status="phase="+phase+" dimension="+mc.level.dimension().location()+" players="+
                        mc.level.players().stream().map(p->p.getGameProfile().getName()+" position="+p.position()+
                                " velocity="+p.getDeltaMovement()+" frame="+GravityFrameAccess.authoritativeFrame(p)).toList();
                MultiplayerChecks.write(name+"-status.txt",status);
                System.out.println("MULTIPLAYER_STATUS "+status);
            }
            if(phase==5&&!respawned||phase==6&&!reconnected) return;
            if(!mc.level.dimension().equals(phase==4?net.minecraft.world.level.Level.NETHER:net.minecraft.world.level.Level.OVERWORLD)) {stable=0;return;}
            var ownFrame=GravityFrameAccess.authoritativeFrame(mc.player);
            if(ownFrame.strength()==0 && ownFrame.down().distance(MultiplayerChecks.down(phase))<1e-6)
                MultiplayerChecks.write(name+"-frame-"+phase+".txt","native assignment installed before rest fixture");
            boolean concurrent=phase==1 && Boolean.getBoolean("gravityengine.multiplayerConcurrentAssignment");
            if(concurrent) {
                // Assignment at ServerTick.Post is pending until the native
                // connection handoff, which waits for the teleport ACK. The
                // old committed gravity may accelerate in that interval.
                // Once zero gravity commits, preserve that momentum exactly;
                // only native airborne drag and its small-speed cutoff apply.
                if(ownFrame.strength()==0 && previousAnchor!=null) {
                    var bounds=mc.player.getBoundingBox().expandTowards(previousVelocity).inflate(.001);
                    boolean available=true;
                    for(int x=net.minecraft.util.Mth.floor(bounds.minX)>>4;x<=net.minecraft.util.Mth.floor(bounds.maxX)>>4;x++)
                        for(int z=net.minecraft.util.Mth.floor(bounds.minZ)>>4;z<=net.minecraft.util.Mth.floor(bounds.maxZ)>>4;z++)
                            available&=mc.level.getChunkSource().hasChunk(x,z);
                    var step=previousVelocity;
                    if(Math.abs(step.y)<.003) step=new net.minecraft.world.phys.Vec3(step.x,0,step.z);
                    var expectedPosition=previousAnchor.add(step);
                    var expectedVelocity=step.multiply(.91,.98,.91);
                    if(!available) {
                        boolean refused=false;
                        try {
                            cc.sighs.gravityengine.gravity.integration.MovementEnvironmentCapture.capture(mc.player,
                                    new cc.sighs.gravityengine.gravity.collision.CollisionWorkTracker(
                                            cc.sighs.gravityengine.gravity.collision.CollisionWorkBudget.defaults()));
                        } catch(cc.sighs.gravityengine.gravity.collision.CollisionSceneCoverageException expected) {refused=true;}
                        if(!refused) throw new AssertionError("unloaded client environment became complete empty air");
                        if(mc.player.position().distanceTo(previousAnchor)>1e-9
                                ||mc.player.getDeltaMovement().distanceTo(previousVelocity)>1e-9)
                            throw new AssertionError("unavailable collision data changed position or momentum");
                        unavailableFreezes++;
                    } else if(mc.player.position().distanceTo(expectedPosition)>1e-6
                            ||mc.player.getDeltaMovement().distanceTo(expectedVelocity)>1e-6)
                        throw new AssertionError("zero-gravity handoff changed world momentum: p="+mc.player.position()+
                                " expected="+expectedPosition+" v="+mc.player.getDeltaMovement()+" expectedV="+expectedVelocity);
                    else zeroMomentumChecks++;
                }
                previousAnchor=mc.player.getY()>200?mc.player.position():null;
                previousVelocity=mc.player.getDeltaMovement();
                if(zeroMomentumChecks<30) {stable=0;return;}
            }
            for(int i=0;i<MultiplayerChecks.NAMES.length;i++) {
                String expectedName=MultiplayerChecks.NAMES[i];
                var player=mc.level.players().stream().filter(p->p.getGameProfile().getName().equals(expectedName)).findFirst().orElse(null);
                if(player==null||!concurrent && player.position().distanceTo(MultiplayerChecks.position(phase,i))>.02
                        ||GravityFrameAccess.authoritativeFrame(player).down().distance(MultiplayerChecks.down(phase))>1e-6) {stable=0;return;}
                var anchor=player.position();var body=GravityEntityGeometry.body(player);
                mc.gameRenderer.getMainCamera().setup(mc.level,player,false,false,.5f);
                if(!anchor.equals(player.position())||!body.equals(GravityEntityGeometry.body(player))) throw new AssertionError("camera mutated physical pose");
            }
            if(++stable==30) {
                MultiplayerChecks.write(name+"-phase-"+phase+".txt","PASS own/observer gravity, camera phase="+phase+
                        (concurrent?" deferred handoff momentum checks="+zeroMomentumChecks+" unavailableFreezes="+unavailableFreezes:" stationary pose"));
                System.out.println("MULTIPLAYER_CLIENT_PHASE_PASSED "+name+" phase="+phase);
            }
        } catch(Throwable failure) {
            failure.printStackTrace();MultiplayerChecks.write(name+"-FAIL.txt",failure.toString());finished=true;mc.stop();
        }
    }
}
