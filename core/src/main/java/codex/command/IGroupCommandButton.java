package codex.command;

import codex.model.Entity;

/**
 * Интерфейс кнопки запуска основной (в группе) команды сущности. Поскольку все остальные (второстепенные команды группы)
 * так или иначе связаны с основной - класс должен реализовать данную связь.
 */
@FunctionalInterface
public interface IGroupCommandButton {

    /**
     * Добавление дочерней команды фактически предполагает реализачию механизма доступа к элементам GUI для её запуска.
     * @param command Ссылка на дочернюю команду.
     */
    void addChildCommand(EntityCommand<Entity> command);
}
