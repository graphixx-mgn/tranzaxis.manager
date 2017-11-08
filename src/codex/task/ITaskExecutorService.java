package codex.task;

import codex.service.IService;

/**
 * Интерфейс сервиса исполнения задач {@link ITask}.
 */
public interface ITaskExecutorService extends IService {
    
    /**
     * Незамедлительное исполнение задачи и регистрация в модальном диалоге.
     * При закрытии диалога, все задачи перемещаются в очередь.
     */
    default void executeTask(ITask task) {};
    
    /**
     * Добавление задачи в очередь на исполнение и регистрация в окне просмотра
     * задач.
     */
    default void enqueueTask(ITask task) {};
    
}
