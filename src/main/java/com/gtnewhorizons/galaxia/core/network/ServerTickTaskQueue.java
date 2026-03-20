package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ServerTickTaskQueue {

    private static final Queue<Runnable> pending = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private static final Queue<PendingTask> conditional = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static void schedule(Runnable task) {
        pending.add(task);
    }

    public static void scheduleWhen(Supplier<Boolean> condition, Runnable task) {
        conditional.add(new PendingTask(condition, task));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Runnable task;
        while ((task = pending.poll()) != null) {
            task.run();
        }

        List<PendingTask> notReady = new ArrayList<>();
        PendingTask pending;
        while ((pending = conditional.poll()) != null) {
            if (pending.condition.get()) {
                pending.task.run();
            } else {
                notReady.add(pending);
            }
        }
        conditional.addAll(notReady);
    }

    @Desugar
    private record PendingTask(Supplier<Boolean> condition, Runnable task) {}
}
