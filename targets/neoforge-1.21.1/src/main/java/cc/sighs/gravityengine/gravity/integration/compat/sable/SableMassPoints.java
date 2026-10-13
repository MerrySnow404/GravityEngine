package cc.sighs.gravityengine.gravity.integration.compat.sable;

import cc.sighs.gravityengine.api.math.Vec3d;
import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Transient evidence attached to the native mass tracker. Native build/edit
 * calls are the only producers; no second world scan or mass authority exists. */
public final class SableMassPoints {
    public static final int MAX_POINTS = 65_536;
    public interface Access {
        SableMassPoints gravityengine$massPoints();
        void gravityengine$massPoints(SableMassPoints points);
    }
    public record Point(Vec3d position, double mass) {}
    // First moments are cell-relative: plot coordinates can be very large.
    // Native replacement adds the new material before subtracting the old one.
    private record Moment(double mass, Vec3d first) {}
    private final Map<BlockPos, Moment> points = new HashMap<>();
    private List<Point> snapshot;
    private boolean complete = true;
    private int editing;
    public void beginEdit() { editing++; snapshot = null; }
    public void endEdit(boolean succeeded) {
        if (editing <= 0) throw new IllegalStateException("unbalanced native mass edit");
        editing--;
        if (!succeeded) incomplete();
    }

    public void add(BlockPos position, Vec3d center, double deltaMass) {
        snapshot = null;
        if (!complete) return;
        if (!center.isFinite() || !Double.isFinite(deltaMass)) { incomplete(); return; }
        if (deltaMass == 0) return;
        var previous = points.get(position);
        double mass = (previous == null ? 0 : previous.mass()) + deltaMass;
        var local = center.subtract(new Vec3d(position.getX(), position.getY(), position.getZ()));
        var first = (previous == null ? Vec3d.ZERO : previous.first()).add(local.multiply(deltaMass));
        if (!Double.isFinite(mass) || mass < 0 || !first.isFinite()) {
            incomplete(); return;
        }
        if (mass == 0) {
            if (first.lengthSquared() > 1e-16) { incomplete(); return; }
            points.remove(position); return;
        }
        if (previous == null && points.size() >= MAX_POINTS) { incomplete(); return; }
        points.put(position.immutable(), new Moment(mass, first));
    }
    private void incomplete() {
        if(complete) org.slf4j.LoggerFactory.getLogger(SableMassPoints.class).warn(
                "Native mass evidence rejected; distributed gravity waits for the next native tracker rebuild");
        complete = false; points.clear(); snapshot=null;
    }
    /** Null is explicitly incomplete evidence, never an empty successful capture. */
    public List<Point> snapshot() {
        if (!complete || editing != 0) return null;
        if (snapshot == null) snapshot = points.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new Point(entry.getValue().first().multiply(1 / entry.getValue().mass())
                        .add(new Vec3d(entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ())),
                        entry.getValue().mass())).toList();
        return snapshot;
    }
}
