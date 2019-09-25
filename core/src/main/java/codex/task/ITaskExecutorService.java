package codex.task;

import codex.service.IService;

/**
 * Интерфейс сервиса исполнения задач {@link ITask}.
 */
public interface ITaskExecutorService extends IService {

    @Override
    default String getTitle() {
        return "Task Executor Service";
    }

    /**
     * Незамедлительное исполнение задачи и регистрация в модальном диалоге.
     * При закрытии диалога, все задачи перемещаются в очередь.
     * @param task Задача.
     */
    default void executeTask(ITask task) {}
    
    /**
     * Добавление задачи в очередь на исполнение и регистрация в окне просмотра
     * задач.
     * @param task Задача.
     */
    default void enqueueTask(ITask task) {}
    
    /**
     * Незамедлительное исполнение задачи без регистрации в модальном диалоге.
     * @param task Задача.
     */
    default void quietTask(ITask task) {}

    Accessor getAccessor();
    abstract class Accessor {
        abstract void attachMonitor(ThreadPoolKind kind, ITaskMonitor monitor);
    }
}
