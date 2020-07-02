package codex.task;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Пул потоков-исполнителей задач.
 */
class TaskExecutor extends ThreadPoolExecutor {
    
    private final static int QUEUE_SIZE  = 10;
    private final static int DEMAND_SIZE = 25;
    
    TaskExecutor(ThreadPoolKind kind) {
        super(
                kind == ThreadPoolKind.Queued ? QUEUE_SIZE : DEMAND_SIZE,
                kind == ThreadPoolKind.Queued ? QUEUE_SIZE : DEMAND_SIZE,
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new NamingThreadFactory(kind)
        );
    }
}
