package codex.command;

import codex.type.Iconified;

/**
 * Интерфейс команд над объектами.
 * @param <V> Тип элемента контекста для которого вызывается команда.
 * @param <C> Тип контекста для которого вызывается команда.
 */
public interface ICommand<V, C> extends Iconified {
    
    /**
     * Актуализация состояния доступности команды.
     */
    public void activate();
    
    /**
     * Установка контекста исполнения команды. При запуске, команда последовательно 
     * исполняется для каждого объекта из контекста.
     * @param context Группа из одного или более объектов контекста.
     */
    public void setContext(C context);
    
    /**
     * @return Набор объектов, установленных в качестве контекста команды.
     */
    public C getContext();
    
    /**
     * Метод исполнения команды, содержащий непосредственно код, реализующий 
     * назначение команды. Требуется перекрытие в классах-наследниках {@link ICommand}.
     * @param context Элемент набора объектов, установленных в качестве контекста команды.
     */
    public void execute(V context);

    /**
     * Добавляет слушатель событий команды.
     * @param listener Ссылка на слушатель.
     */
    void addListener(ICommandListener<V> listener);

    /**
     * Удаляет слушатель событий команды.
     * @param listener Ссылка на слушатель.
     */
    void removeListener(ICommandListener<V> listener);
    
    /**
     * Возвращает признак блокируется ли команда при блокировке контекстного объекта.
     */
    default boolean disableWithContext () {
        return true;
    }
    
    /**
     * Возвращает признак разрешено ли выполнение команды над множеством контекстных
     * объектов.
     */
    default boolean multiContextAllowed() {
        return false;
    }
}
