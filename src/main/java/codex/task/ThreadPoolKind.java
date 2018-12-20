package codex.task;

/**
 * Типы пулов потоков.
 */
enum ThreadPoolKind {
    /**
     * Очередь фонового выполнения.
     */
    Queued,
    /**
     * Очередь незамедлительного выполнения.
     */
    Demand;
}
