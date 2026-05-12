package com.gtnewhorizons.galaxia.compat.structure.util;

import java.util.Arrays;

public final class IntQueue {

    private static final int INITIAL_QUEUE_SIZE = 4096;

    private int[] queue = new int[INITIAL_QUEUE_SIZE];
    private int head;
    private int tail;

    public void enqueue(int v) {
        if (tail == queue.length) {
            queue = Arrays.copyOf(queue, queue.length * 2);
        }
        queue[tail++] = v;
    }

    public int dequeue() {
        return queue[head++];
    }

    public boolean isEmpty() {
        return head >= tail;
    }

    public void reset() {
        head = 0;
        tail = 0;
    }

    public void clear() {
        reset();
        queue = new int[INITIAL_QUEUE_SIZE];
    }
}
