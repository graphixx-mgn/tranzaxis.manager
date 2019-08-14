package codex.task;

import java.util.concurrent.ExecutorService;

/**
 * Типы пулов потоков.
 */
enum ThreadPoolKind {
    /**
     * Очередь фонового выполнения.
     */
    Queued(),
    /**
     * Очередь незамедлительного выполнения.
     */
    Demand;

    private final ExecutorService executorService;

    ThreadPoolKind() {
        executorService = new TaskExecutor(this);
    }

    public ExecutorService getExecutor() {
        return executorService;
    }
}
