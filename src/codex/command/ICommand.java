package codex.command;

import codex.component.button.IButton;

/**
 * Интерфейс команд над объектами. Вызывается при нажатии на кнопку, которая создается 
 * в классе конкретной реализации метода {@link #getButton()} интерфейса.
 * @param <T> Тип объекта для которого вызывается команда.
 */
public interface ICommand<T> {
    
    /**
     * @return Экземпляр кнопки, вызывающей исполнение команды над объектом.
     */
    public IButton getButton();
    
    /**
     * Актуализация состояния доступности команды.
     */
    public void activate();
    
    /**
     * Установка контекста исполнения команды. При запуске, команды последовательно 
     * исполняется для каждого объекта из набора.
     * @param context Группа из одного или более объектов.
     */
    public void setContext(T... context);
    
    /**
     * @return Набор объектов, установленных в качестве контекста команды.
     */
    public T[] getContext();
    
    /**
     * Метод команды.
     * @param context Элемент набора объектов, установленных в качестве контекста команды.
     */
    public void execute(T context);
    
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
