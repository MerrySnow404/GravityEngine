package cc.sighs.gravityengine.controltest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only bounded FIFO. Owns retained messages until delivery or disconnect. */
final class OrderedInboundDelay extends ChannelInboundHandlerAdapter {
    private record Pending(Object message, long due) {}
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final int millis;
    private final AtomicInteger delivered;
    private io.netty.util.concurrent.ScheduledFuture<?> wakeup;

    OrderedInboundDelay(int millis, AtomicInteger delivered) {
        if (millis < 0 || millis > 1000) throw new IllegalArgumentException("delay outside 0..1000 ms");
        this.millis = millis;
        this.delivered = delivered;
    }

    @Override public void channelRead(ChannelHandlerContext context, Object message) {
        if (pending.size() >= 8192) {
            ReferenceCountUtil.release(message);
            discard();
            context.fireExceptionCaught(new AssertionError("verification inbound delay budget exhausted"));
            context.close();
            return;
        }
        pending.addLast(new Pending(message, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)));
        if (wakeup == null) schedule(context);
    }

    private void schedule(ChannelHandlerContext context) {
        if (pending.isEmpty()) return;
        wakeup = context.executor().schedule(() -> drain(context),
                Math.max(0, pending.getFirst().due() - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    private void drain(ChannelHandlerContext context) {
        wakeup = null;
        if (!context.channel().isActive()) { discard(); return; }
        boolean read = false;
        while (!pending.isEmpty() && pending.getFirst().due() <= System.nanoTime()) {
            var entry = pending.removeFirst();
            delivered.incrementAndGet();
            context.fireChannelRead(entry.message());
            read = true;
        }
        if (read) context.fireChannelReadComplete();
        schedule(context);
    }

    @Override public void channelReadComplete(ChannelHandlerContext context) {}
    @Override public void channelInactive(ChannelHandlerContext context) throws Exception {
        discard();
        super.channelInactive(context);
    }
    @Override public void handlerRemoved(ChannelHandlerContext context) { discard(); }
    private void discard() {
        if (wakeup != null) { wakeup.cancel(false); wakeup = null; }
        while (!pending.isEmpty()) ReferenceCountUtil.release(pending.removeFirst().message());
    }

    static void verify() {
        var count = new AtomicInteger();
        var channel = new io.netty.channel.embedded.EmbeddedChannel(new OrderedInboundDelay(0, count));
        try {
            channel.writeInbound("first", "second");
            channel.runScheduledPendingTasks();
            if (!"first".equals(channel.readInbound()) || !"second".equals(channel.readInbound()) || count.get() != 2)
                throw new AssertionError("inbound delay must preserve FIFO delivery");
        } finally { channel.finishAndReleaseAll(); }
        count.set(0);
        channel = new io.netty.channel.embedded.EmbeddedChannel(new OrderedInboundDelay(1000, count));
        var packet = io.netty.buffer.Unpooled.buffer().writeByte(42);
        try {
            channel.writeInbound(packet);
            if (channel.readInbound() != null || count.get() != 0 || packet.refCnt() != 1)
                throw new AssertionError("message escaped its delay or lost ownership");
            channel.close();
            if (packet.refCnt() != 0 || count.get() != 0)
                throw new AssertionError("disconnect must release pending datagrams without delivery");
        } finally { channel.finishAndReleaseAll(); }
        System.out.println("ORDERED_INBOUND_DELAY_PASSED fifo deadline disconnect-release");
    }
}
