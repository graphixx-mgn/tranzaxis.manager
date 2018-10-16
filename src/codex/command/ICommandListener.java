package codex.command;

import java.util.List;

/**
 * Интерфейс слушателя событий команды.
 * @param <T> Тип контекста команды.
 */
@FunctionalInterface
public interface ICommandListener<T> {
    
    /**
     * Событие смены контекста команды.
     * @param context Новый контекст.
     */
    void contextChanged(List<T> context);
    
}