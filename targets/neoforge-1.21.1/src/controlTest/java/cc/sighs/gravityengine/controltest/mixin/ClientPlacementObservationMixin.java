package cc.sighs.gravityengine.controltest.mixin;

import cc.sighs.gravityengine.controltest.MultiplayerClientChecks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test observation of the actual game-thread completion, never a placement writer. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPlacementObservationMixin {
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="handleSetEntityPassengersPacket",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;ejectPassengers()V"),require=1)
    private void gravityengine$observeRemoval(net.minecraft.world.entity.Entity vehicle,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        var actor=net.minecraft.client.Minecraft.getInstance().player;
        boolean observe=Boolean.getBoolean("gravityengine.multiplayerChildPlatforms") && actor!=null && actor.getVehicle()==vehicle;
        var before=observe?actor.position():null;var pose=observe?actor.getPose():null;
        original.call(vehicle);
        if(observe && !actor.isPassenger()) MultiplayerClientChecks.dismountApplied(actor,before,pose);
    }

    @Inject(method="handleMovePlayer",at=@At("RETURN"),require=1)
    private void gravityengine$placementApplied(ClientboundPlayerPositionPacket packet,CallbackInfo ci) {
        MultiplayerClientChecks.placementApplied(packet);
    }
}
