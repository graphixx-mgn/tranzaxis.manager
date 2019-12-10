package codex.command;

import codex.model.Entity;

/**
 * Фабрика для производства кнопок {@link IGroupCommandButton}
 */
@FunctionalInterface
public interface IGroupButtonFactory {
    IGroupCommandButton newInstance(EntityCommand<Entity> command);
}
