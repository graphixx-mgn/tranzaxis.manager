package codex.presentation;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.model.*;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class ShowExtraProps extends EntityCommand<Entity> {

    private final static ImageIcon IMAGE_WARN = ImageUtils.resize(ImageUtils.getByPath("/images/warn.png"), .7f);

    ShowExtraProps() {
        super(
                "extra",null,
                ImageUtils.getByPath("/images/param.png"),
                Language.get(EditorPresentation.class, "command@extra"),
                null
        );
        Function<List<Entity>, CommandStatus> defaultActivator = activator;
        activator = entities -> {
            Entity context = entities.get(0);
            boolean hasInvalidProp = context.model.getProperties(Access.Any).stream()
                    .filter(context.model::isPropertyExtra)
                    .anyMatch(propName -> !context.model.getProperty(propName).isValid());
            return new CommandStatus(
                    defaultActivator.apply(entities).isActive(),
                    hasInvalidProp ? ImageUtils.combine(getIcon(), IMAGE_WARN, SwingConstants.SOUTH_EAST) : getIcon()
            );
        };
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        new PropSetEditor(
                ImageUtils.getByPath("/images/param.png"),
                Language.get(EditorPresentation.class, "extender@title"),
                context,
                context.model::isPropertyExtra
        ).open();
    }

    @Override
    public boolean disableWithContext() {
        return false;
    }
}
