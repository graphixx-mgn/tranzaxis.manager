package codex.command;

import javax.swing.*;
import java.util.Collection;

/**
 * Интерфейс слушателя событий команды.
 * @param <T> Тип контекста команды.
 */
public interface ICommandListener<T> {
    
    /**
     * Событие смены контекста команды.
     * @param context Новый контекст.
     */
    default void contextChanged(Collection<T> context) {}

    /**
     * Событие смены статуса активности команды.
     * @param active Признак активномти команды.
     * @param hidden Переключить видимость кнопки команды (если не NULL).
     */
    default void commandStatusChanged(boolean active, Boolean hidden) {}

    /**
     * Смена иконки представления команды.
     * @param icon Новая иконка команды.
     */
    default void commandIconChanged(ImageIcon icon) {}
    
}