package codex.task;

/**
 * Интерфейс слушателя событий изменения состояния исполнения задачи {@link ITask}
 */
public interface ITaskListener {
    
    /**
     * Вызывается при смене статуса задачи.
     * @see Status
     * @param task Ссылка на задачу.
     * @param prevStatus Старый статус.
     * @param nextStatus Новый статус.
     */
    default void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
        // Do nothing
    }
    /**
     * Вызывается при изменении прогресса исполнения.
     * @param task Ссылка на задачу.
     * @param percent Значение прогресса в процентах.
     * @param description Описание текущего состояния, может меняться при росте 
     * прогресса.
     */
    default void progressChanged(ITask task,  int percent, String description) {
        // Do nothing
    }
    
    /**
     * Вызывается перед исполнением метода {@link ITask#execute()}.
     * @param task Ссылка на задачу.
     */
    default void beforeExecute(ITask task) {
        // Do nothing
    }
    
    /**
     * Вызывается после исполнения метода {@link ITask#execute()}.
     * @param task Ссылка на задачу.
     */
    default void afterExecute(ITask task) {
        // Do nothing
    }
}
