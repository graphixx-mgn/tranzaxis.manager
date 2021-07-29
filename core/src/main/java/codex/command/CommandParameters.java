package codex.command;

import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.type.EntityRef;
import codex.type.Str;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandParameters extends Catalog {

    private final static String PROP_ENTITY_CLASS = "entityClass";
    private final static String PROP_PARAMETERS   = "parameters";

    public static void create(EntityCommand<? extends Entity> command, Class<? extends Entity> entityClass) {
        CommandParameters store = prepare(command);
        if (store != null) {
            store.model.setValue(PROP_ENTITY_CLASS, entityClass.getTypeName());
            try {
                store.model.commit(false);
            } catch (Exception ignore) {}
        }
    }

    public static void update(EntityCommand<? extends Entity> command) {
        CommandParameters store = prepare(command);
        if (store != null) {
            Map<String, String> values = command.getParameterProperties().stream()
                    .filter(propertyHolder -> !propertyHolder.getPropValue().isEmpty())
                    .collect(Collectors.toMap(
                            PropertyHolder::getName,
                            propertyHolder -> propertyHolder.getPropValue().toString()
                    ));
            store.model.setValue(PROP_PARAMETERS, values);
            try {
                store.model.commit(false);
            } catch (Exception ignore) {}
        }
    }

    public static Map<String, String> read(EntityCommand<? extends Entity> command) {
        CommandParameters store = prepare(command);
        return store == null ? Collections.emptyMap() : store.getParameters();
    }

    public static void delete(EntityCommand<? extends Entity> command) {
        CommandParameters store = prepare(command);
        if (store != null) store.model.remove();
    }

    private static CommandParameters prepare(EntityCommand<? extends Entity> command) {
        return command.getKind() == EntityCommand.Kind.Action && command.hasParameters() ?
               Entity.newInstance(CommandParameters.class, null, command.getClass().getTypeName()) : null;
    }


    private CommandParameters(EntityRef owner, String title) {
        super(null, null, title, null);

        // Properties
        PropertyHolder<codex.type.Map<String, String>, Map<String, String>> propertiesHolder = new PropertyHolder<>(
                PROP_PARAMETERS,
                new codex.type.Map<>(new Str(), new Str(), new HashMap<>()),
                false
        );
        model.addUserProp(PROP_ENTITY_CLASS, new Str(), true, Access.Any);
        model.addUserProp(propertiesHolder, Access.Any);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getParameters() {
        return (Map<String, String>) (model.getUnsavedValue(PROP_PARAMETERS));
    }
}
