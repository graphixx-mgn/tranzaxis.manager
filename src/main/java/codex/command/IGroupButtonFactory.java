package codex.command;

import codex.model.Entity;

@FunctionalInterface
public interface IGroupButtonFactory {
    IGroupCommandButton newInstance(EntityCommand<Entity> command);
}
