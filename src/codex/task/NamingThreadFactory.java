package codex.task;

import codex.utils.Language;
import java.text.MessageFormat;
import java.util.concurrent.ThreadFactory;

/**
 * Фабрика создания потоков для менеджера задач.
 * Требуется для указания имен создаваемым потокам.
 */
class NamingThreadFactory implements ThreadFactory {
    
    public  static final String IDLE = Language.get(TaskMonitor.class.getSimpleName(), "idle");
    private static final String NAME_FORMAT = "{0}.{1}Thread #{2}: {3}";
            
    private final ThreadPoolKind threadKind;
    private Integer threadCount = 0;

    /**
     * Конструктор фабрики.
     * @param namePrefix Начальная часть имени потока.
     */
    NamingThreadFactory(ThreadPoolKind threadKind) {
        this.threadKind = threadKind;
    }

    /**
     * Создание нового потока.
     */
    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(
                runnable,
                MessageFormat.format(
                        NAME_FORMAT, 
                        TaskManager.class.getSimpleName(),
                        threadKind, threadCount, IDLE
                )
        );
        thread.setPriority(threadKind == ThreadPoolKind.Demand ? Thread.MAX_PRIORITY : Thread.NORM_PRIORITY);
        thread.setDaemon(true);
        threadCount++;
        return thread;
    }

}
