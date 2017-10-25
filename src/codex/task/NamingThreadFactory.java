package codex.task;

import java.text.MessageFormat;
import java.util.concurrent.ThreadFactory;

/**
 * Фабрика создания потоков для менеджера задач.
 * Требуется для указания имен создаваемым потокам.
 */
class NamingThreadFactory implements ThreadFactory {
    
    private static final String NAME_FORMAT = "{0}.{1}Thread #{2}: <idle>";
            
    private final ThreadPoolKind threadKind;
    private Integer threadCount = 0;

    /**
     * Конструктор фабрики.
     * @param namePrefix Начальная часть имени потока.
     */
    public NamingThreadFactory(ThreadPoolKind threadKind) {
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
                        threadKind,
                        threadCount
                )
        );
        thread.setPriority(threadKind == ThreadPoolKind.Demand ? Thread.MAX_PRIORITY : Thread.NORM_PRIORITY);
        thread.setDaemon(true);
        threadCount++;
        return thread;
    }

}
