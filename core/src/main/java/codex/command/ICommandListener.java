package codex.command;

import javax.swing.*;
import java.util.List;

/**
 * Интерфейс слушателя событий команды.
 * @param <T> Тип контекста команды.
 */
public interface ICommandListener<T> {
    
    /**
     * Событие смены контекста команды.
     * @param context Новый контекст.
     */
    default void contextChanged(List<T> context) {}

    /**
     * Событие смены статуса активности команды.
     * @param active Признак активномти команды.
     */
    default void commandStatusChanged(boolean active) {}

    /**
     * Смена иконки представления команды.
     * @param icon Новая иконка команды.
     */
    default void commandIconChanged(ImageIcon icon) {}
    
}