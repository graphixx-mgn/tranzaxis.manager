/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package codex.task;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Пул потоков-исполнителей задач.
 */
class TaskExecutor extends ThreadPoolExecutor {
    
    private final static int QUEUE_SIZE  = 10;
    private final static int DEMAND_SIZE = 25;
    
    public TaskExecutor(ThreadPoolKind kind) {
        super(
                kind == ThreadPoolKind.Queued ? QUEUE_SIZE : 0, 
                kind == ThreadPoolKind.Queued ? QUEUE_SIZE : DEMAND_SIZE,
                30L, TimeUnit.SECONDS,
                kind == ThreadPoolKind.Queued ? new LinkedBlockingQueue<>() : new SynchronousQueue<>(),
                new NamingThreadFactory(kind)
        );
    }
}
