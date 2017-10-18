package codex.task;

/**
 * Интерфейс слушателя событий изменения состояния исполнения задачи {@link ITask}
 */
public interface ITaskListener {
    
    /**
     * Вызывается при смене статуса задачи.
     * @see Status
     * @param task Ссылка на задачу.
     * @param status Новый статус.
     */
    public void statusChanged(ITask task, Status status);
    /**
     * Вызывается при изменении прогресса исполнения.
     * @param task Ссылка на задачу.
     * @param percent Значение прогресса в процентах.
     * @param description Описание текущего состояния, может меняться при росте 
     * прогресса.
     */
    public void progressChanged(ITask task,  int percent, String description);
    
}
