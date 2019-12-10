package codex.command;

import codex.model.Entity;

@FunctionalInterface
public interface IGroupCommandButton {
    void addChildCommand(EntityCommand<Entity> command);
}
