package plugin.command;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.log.Logger;
import codex.model.Entity;
import codex.command.CommandButton;
import codex.command.IGroupButtonFactory;
import codex.command.IGroupCommandButton;
import codex.service.ServiceRegistry;
import codex.type.IComplexType;
import codex.utils.Language;
import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CommandLauncher extends EntityCommand<Entity> {

    CommandLauncher() {
        super(
                "external",
                Language.get(CommandPlugin.class, "launcher@title"),
                CommandPlugin.COMMAND_ICON,
                Language.get(CommandPlugin.class, "launcher@title"),
                null
        );
        activator = entities -> new CommandStatus(
            entities.stream()
                    .map(entity -> entity.getCommands().stream()
                            .filter(command -> command.getClass().getAnnotation(Definition.class).parentCommand().equals(CommandLauncher.class))
                    )
                    .flatMap(x -> x)
                    .peek(command -> command.setContext(entities))
                    .map(EntityCommand::isActive)
                    .anyMatch(Boolean.TRUE::equals)
        );
    }


    @Override
    public Kind getKind() {
        return Kind.Admin;
    }

    @Override
    public IGroupButtonFactory groupButtonFactory() {
        return LaunchButton::new;
    }

    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        final DefaultListModel<EntityCommand<? extends Entity>> commandListModel = new DefaultListModel<>();
        getContext().get(0).getCommands().stream()
                .filter(command -> command.getClass().getAnnotation(Definition.class).parentCommand().equals(CommandLauncher.class))
                .forEach(commandListModel::addElement);
        CommandSelector selector = new CommandSelector(commandListModel);
        EntityCommand<? extends Entity> chosenCommand = selector.select();
        if (chosenCommand != null) {
            List<? extends Entity> commandContext = chosenCommand.getContext();
            Logger.getLogger().debug("Perform command [{0}]. Context: {1}", chosenCommand.getName(), commandContext);
            commandContext.forEach(entity -> ((EntityCommand) chosenCommand).execute(entity, params));
        }
    }


    private final class LaunchButton extends CommandButton implements IGroupCommandButton {

        LaunchButton(EntityCommand<Entity> command) {
            super(command, false);
        }

        @Override
        public void addChildCommand(EntityCommand<Entity> command) {
            // Do nothing
        }

        @Override
        protected void executeCommand(EntityCommand<Entity> command) {
            command.execute(null, null);
        }
    }

}
