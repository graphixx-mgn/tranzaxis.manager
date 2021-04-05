package plugin.command;

import codex.command.*;
import codex.log.Logger;
import codex.model.Entity;
import codex.type.IComplexType;
import codex.utils.Language;
import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CommandLauncher extends EntityGroupCommand<Entity> {

    CommandLauncher() {
        super(
                "select command",
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
        return Kind.System;
    }

    @Override
    public IGroupButtonFactory groupButtonFactory() {
        return LaunchButton::new;
    }

    @Override
    public void execute(List<Entity> context, Map<String, IComplexType> params) {
        final DefaultListModel<EntityCommand<? extends Entity>> commandListModel = new DefaultListModel<>();
        getContext().get(0).getCommands().stream()
                .filter(command -> command.getClass().getAnnotation(Definition.class).parentCommand().equals(CommandLauncher.class))
                .forEach(commandListModel::addElement);
        CommandSelector selector = new CommandSelector(commandListModel);
        EntityCommand<? extends Entity> chosenCommand = selector.select();
        if (chosenCommand != null) {
            List<? extends Entity> commandContext = chosenCommand.getContext();
            Logger.getLogger().debug(
                    "Perform command [{0}]. Context: {1}",
                    getName(),
                    context.size() == 1 ?
                            context.get(0) :
                            context.stream()
                                    .map(entity -> "\n * "+entity.model.getQualifiedName()).collect(Collectors.joining())
            );
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
        protected void executeCommand() {
            CommandLauncher.this.process();
        }
    }

}
