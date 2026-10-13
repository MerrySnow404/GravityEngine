package cc.sighs.gravityengine.controltest;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;

/** Two real powered bearings, one passenger walking surface per client. */
final class CreateMultiplayerPlatform {
    private static final ControlledContraptionEntity[] CHILDREN=new ControlledContraptionEntity[2];
    static void assemble(ServerSubLevel parent) {
        var plot=parent.getPlot();var blocks=plot.getEmbeddedLevelAccessor();
        for(int i=0;i<2;i++) {
            int x=i==0?-3:3;
            var bearingState=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","mechanical_bearing"))
                    .defaultBlockState().setValue(BearingBlock.FACING,Direction.UP);
            var motorState=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","creative_motor"))
                    .defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP);
            blocks.setBlock(new BlockPos(x,1,0),motorState,3);
            blocks.setBlock(new BlockPos(x,2,0),bearingState,3);
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++)
                blocks.setBlock(new BlockPos(x+dx,3,dz),(dx==1&&dz==0?Blocks.STONE:Blocks.HONEY_BLOCK).defaultBlockState(),3);
            blocks.setBlock(new BlockPos(x-1,4,-1),BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("create","white_seat")).defaultBlockState(),3);
            var motor=(CreativeMotorBlockEntity)parent.getLevel().getBlockEntity(plot.getCenterBlock().offset(x,1,0));
            var bearing=(MechanicalBearingBlockEntity)parent.getLevel().getBlockEntity(plot.getCenterBlock().offset(x,2,0));
            ContactBehaviorChecks.check(motor!=null&&bearing!=null,"native multiplayer bearing block entities");
            motor.initialize();bearing.initialize();motor.generatedSpeed.setValue(i==0?8:-8);motor.updateGeneratedRotation();
            bearing.assemble();CHILDREN[i]=bearing.getMovedContraption();
            ContactBehaviorChecks.check(CHILDREN[i]!=null&&bearing.isRunning(),"native multiplayer bearing assembly");
            ContactBehaviorChecks.check(CHILDREN[i].getContraption().getBlocks().size()==10
                    && CHILDREN[i].getContraption().getSeats().size()==1,"native sticky deck assembly includes a real seat");
            MultiplayerChecks.write(MultiplayerChecks.NAMES[i]+"-child.txt",CHILDREN[i].getId()+"\n"+CHILDREN[i].getUUID());
        }
    }
    static String serverMotion(cc.sighs.gravityengine.gravity.kinematic.KinematicStepContext time) {
        var result=new StringBuilder();
        for(var child:CHILDREN) result.append(" child=").append(child.getUUID()).append(' ').append(MultiplayerPlatformChecks.describe(
                cc.sighs.gravityengine.gravity.integration.compat.create.CreateRigidCollisionProvider.trajectory(child,time,0,0)));
        return result.toString();
    }
    static Vec3 placement(ServerSubLevel parent,int index) {
        return parent.logicalPose().transformPosition(CHILDREN[index].toGlobalVector(new Vec3(1.5,1.05,.5),1));
    }
    static void edit() {
        for(var child:CHILDREN) {
            // Native honey joins the deck; edits make its walking surface level.
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
                var position=new BlockPos(x,0,z);
                child.setBlock(position,
                        new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                                position,Blocks.STONE.defaultBlockState(),null));
            }
            child.setBlock(new BlockPos(1,0,0),
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                            new BlockPos(1,0,0),Blocks.GOLD_BLOCK.defaultBlockState(),null));
        }
    }
    static boolean edited(Player actor) {
        var child=child(actor);
        if(child==null) return false;
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            var expected=x==1&&z==0?Blocks.GOLD_BLOCK:Blocks.STONE;
            if(!child.getContraption().getBlocks().get(new BlockPos(x,0,z)).state().is(expected)) return false;
        }
        return true;
    }
    static void deferredPlacementCheck(Player actor,SubLevel parent) {
        Player probe=new Player(actor.level(),BlockPos.ZERO,0,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"deferred-seat")) {
            @Override public boolean isSpectator(){return false;}
            @Override public boolean isCreative(){return false;}
        };
        try {
            probe.setPos(actor.position());
            var initial=probe.position();var pose=probe.getPose();
            var blocked=parent.logicalPose().transformPositionInverse(initial.subtract(0,.5,0));
            probe.getPersistentData().put("ContraptionDismountLocation",net.createmod.catnip.math.VecHelper.writeNBT(blocked));
            com.simibubi.create.content.contraptions.ContraptionHandler.entitiesWhoJustDismountedGetSentToTheRightLocation(probe,probe.level());
            ContactBehaviorChecks.check(probe.position().equals(initial) && probe.getPose()==pose,
                    "deferred native dismount must reject blocked location without moving actor: "+probe.position());
            ContactBehaviorChecks.check(!probe.getPersistentData().contains("ContraptionDismountLocation"),
                    "native delayed dismount data is consumed even on rejection");
            var clear=initial.add(0,2,0);
            probe.getPersistentData().put("ContraptionDismountLocation",net.createmod.catnip.math.VecHelper.writeNBT(
                    parent.logicalPose().transformPositionInverse(clear)));
            com.simibubi.create.content.contraptions.ContraptionHandler.entitiesWhoJustDismountedGetSentToTheRightLocation(probe,probe.level());
            ContactBehaviorChecks.check(probe.position().distanceTo(clear)<1e-7 && !probe.isPassenger()
                    && !probe.getPersistentData().contains("ContraptionDismountLocation"),
                    "deferred native dismount projects one accepted location and consumes data once");
            MultiplayerChecks.write(actor.getGameProfile().getName()+"-deferred-dismount.txt","PASS real client Create handler blocked/clear native plot positions, data consumed once");
        } finally {probe.discard();}
    }
    static void board(java.util.List<net.minecraft.server.level.ServerPlayer> players) {
        for(int i=0;i<players.size();i++) CHILDREN[i].addSittingPassenger(players.get(i),0);
    }
    static Vec3 world(Player player,SubLevel parent,Vec3 local) {
        return parent.logicalPose().transformPosition(child(player).toGlobalVector(local,1));
    }
    static boolean nativeMountedPosition(Player player,SubLevel parent) {
        var vehicle=child(player);
        if(vehicle==null || player.getVehicle()!=vehicle) return false;
        var nativePosition=vehicle.getPassengerPosition(player,1).add(0,
                com.simibubi.create.content.contraptions.actors.seat.SeatEntity.getCustomEntitySeatOffset(player)-.125,0);
        var eyeOffset=player.getEyePosition().subtract(player.position());
        var expected=parent.logicalPose().transformPosition(nativePosition.add(eyeOffset)).subtract(eyeOffset);
        return player.position().distanceTo(expected)<.05;
    }
    static void disassemble(ServerSubLevel parent) {
        for(int i=0;i<2;i++) {
            var bearing=(MechanicalBearingBlockEntity)parent.getLevel().getBlockEntity(
                    parent.getPlot().getCenterBlock().offset(i==0?-3:3,2,0));
            bearing.disassemble();
            ContactBehaviorChecks.check(CHILDREN[i].isRemoved(),"multiplayer native bearing disassembly removes child");
        }
    }
    static AbstractContraptionEntity child(Player player) {
        try {
            var file=MultiplayerChecks.ROOT.resolve(player.getGameProfile().getName()+"-child.txt");
            if(!Files.exists(file)) return null;
            var lines=Files.readAllLines(file);
            var entity=player.level().getEntity(Integer.parseInt(lines.getFirst()));
            return entity instanceof AbstractContraptionEntity child&&child.getUUID().toString().equals(lines.get(1))?child:null;
        } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);}
    }
    static Vec3 local(Player player,SubLevel parent) {
        var child=child(player);
        return child==null?null:child.toLocalVector(parent.logicalPose().transformPositionInverse(player.position()),1);
    }
}
