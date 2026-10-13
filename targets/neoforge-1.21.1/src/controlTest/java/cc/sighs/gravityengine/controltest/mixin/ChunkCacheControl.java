package cc.sighs.gravityengine.controltest.mixin;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Restores the normal tick cache boundary between same-tick plot fixtures. */
@Mixin(ServerChunkCache.class)
public interface ChunkCacheControl {
    @Invoker("clearCache") void gravityengine$clearCache();
}
