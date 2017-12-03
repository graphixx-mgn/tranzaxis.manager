package codex.task;

import java.util.concurrent.RunnableFuture;
import java.util.function.Consumer;

/**
 * Интерфейс исполняемых задач в модуле {@link TaskManager}.
 * @param <T> Тип результата возвращаемого методом {@link ITask#execute()}
 */
public interface ITask<T> extends RunnableFuture<T> {

    /**
     * Возвращает наименование задачи.
     */
    String  getTitle();
    /**
     * Возвращает текущее состояние исполнения задачи.
     * @see Status
     */
    Status  getStatus();
    /**
     * Возвращает прогресс исполнения задачи в процентах.
     */
    Integer getProgress();
    /**
     * Возвращает расчитанное на основе состояния описание задачи.
     */
    String  getDescription();
    /**
     * Код исполнения.
     */
    T execute() throws Exception;
    /**
     * Код пост-исполнения.
     */
    void finished(T result);
    /**
     * Создание виджета задачи для отображения в GUI.
     * @param cancelAction Действие по нажатии кнопми отмены.
     */
    AbstractTaskView createView(Consumer<ITask> cancelAction);
    /**
     * Добавить слушатель событий задачи.
     */
    void addListener(ITaskListener listener);
    
}
