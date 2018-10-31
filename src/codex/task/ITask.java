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
     * Установить состояние прогресса исполнения
     * @param percent Процент, от 0 до 100
     * @param description Описание к текущему состоянию
     */
    void setProgress(int percent, String description);
    
    /**
     * Возвращает расчитанное на основе состояния описание задачи.
     */
    String  getDescription();
    
    /**
     * Задача может быть приостановлена.
     */
    boolean isPauseable();
    
    /**
     * Проверка флага приостановки задачи.
     */
    void checkPaused();
    
    
    /**
     * Код исполнения.
     */
    T execute() throws Exception;
    
    /**
     * Код пост-исполнения.
     * @param result Результат иполнения метода {@link ITask#execute()}.
     */
    void finished(T result);
    
    /**
     * Создание виджета задачи для отображения в GUI.
     * @param cancelAction Действие по нажатии кнопми отмены.
     */
    AbstractTaskView createView(Consumer<ITask> cancelAction);
    
    /**
     * Добавить слушатель событий задачи.
     * @param listener Ссылка на слущатель.
     */
    void addListener(ITaskListener listener);
    
}
