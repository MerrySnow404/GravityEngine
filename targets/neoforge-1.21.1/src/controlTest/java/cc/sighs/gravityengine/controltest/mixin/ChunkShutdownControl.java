package cc.sighs.gravityengine.controltest.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read native generation/save work before terminating a control server. */
@Mixin(ChunkMap.class)
public interface ChunkShutdownControl {
    @Accessor("updatingChunkMap") Long2ObjectLinkedOpenHashMap<ChunkHolder> gravityengine$updating();
    @Accessor("pendingUnloads") Long2ObjectLinkedOpenHashMap<ChunkHolder> gravityengine$unloading();
}
