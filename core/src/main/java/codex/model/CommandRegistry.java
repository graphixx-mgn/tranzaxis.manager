package codex.model;

import codex.command.CommandParameters;
import codex.command.EntityCommand;
import codex.log.Logger;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.Predicate;

public final class CommandRegistry {

    private final static CommandRegistry INSTANCE = new CommandRegistry();
    public static CommandRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<
            Class<? extends Entity>,
            List<Map.Entry<
                    Predicate<? extends Entity>,
                    EntityCommand<? extends Entity>
            >>
    > REGISTRY = new HashMap<>();

    private CommandRegistry() {}

    public <E extends Entity> EntityCommand<E> registerCommand(Class<? extends EntityCommand<E>> commandClass) {
        return registerCommand(getCommandEntityClass(commandClass), commandClass);
    }

    public <E extends Entity> EntityCommand<E> registerCommand(Class<? extends E> entityClass, Class<? extends EntityCommand<E>> commandClass) {
        return registerCommand(entityClass, commandClass, entity -> true);
    }

    public <E extends Entity> EntityCommand<E> registerCommand(
            Class<? extends E> entityClass,
            Class<? extends EntityCommand<E>> commandClass,
            Predicate<E> condition
    ) {
        if (!REGISTRY.containsKey(entityClass)) {
            REGISTRY.put(entityClass, new LinkedList<>());
        }
        EntityCommand<E> command = getCommandInstance(commandClass);
        if (command != null) {
            REGISTRY.get(entityClass).add(new Map.Entry<Predicate<? extends Entity>, EntityCommand<? extends Entity>>() {
                @Override
                public Predicate<? extends Entity> getKey() {
                    return condition;
                }

                @Override
                public EntityCommand<? extends Entity> getValue() {
                    return command;
                }

                @Override
                public EntityCommand<? extends Entity> setValue(EntityCommand<? extends Entity> value) {
                    return value;
                }
            });
            CommandParameters.create(command, entityClass);
        }
        return command;
    }

    public <E extends Entity> void unregisterCommand(Class<? extends EntityCommand<E>> commandClass) {
        unregisterCommand(getCommandEntityClass(commandClass), commandClass);
    }

    public <E extends Entity> void unregisterCommand(Class<E> entityClass, Class<? extends EntityCommand<E>> commandClass) {
        if (REGISTRY.containsKey(entityClass)) {
            REGISTRY.get(entityClass).removeIf(commandEntry -> commandEntry.getValue().getClass().equals(commandClass));
        }
    }

    public <E extends Entity> List<EntityCommand<E>> getRegisteredCommands(Class<E> entityClass) {
        return getRegisteredCommands(null, entityClass);
    }

    @SuppressWarnings("unchecked")
    <E extends Entity> List<EntityCommand<E>> getRegisteredCommands(E entity) {
        return new LinkedList<>(getRegisteredCommands(entity, (Class<E>) entity.getClass()));
    }

    @SuppressWarnings("unchecked")
    <E extends Entity> List<EntityCommand<E>> getRegisteredCommands(E entity, Class<E> entityClass) {
        List<EntityCommand<E>> entityCommands = new LinkedList<>();
        REGISTRY.getOrDefault(entityClass, Collections.emptyList()).forEach(commandEntry -> {
            if (entity == null || ((Predicate<E>) commandEntry.getKey()).test(entity)) {
                entityCommands.add((EntityCommand<E>) commandEntry.getValue());
            }
        });
        Class<? extends Entity> superClass = entityClass.getSuperclass().asSubclass(Entity.class);
        if (Entity.class.isAssignableFrom(superClass) && !Modifier.isAbstract(superClass.getModifiers())) {
            entityCommands.addAll(getRegisteredCommands(entity, (Class<E>) superClass));
        }
        return entityCommands;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Entity> Class<E> getCommandEntityClass(Class<? extends EntityCommand<E>> commandClass) {
        try {
            return (Class<E>) ((ParameterizedType) commandClass.getGenericSuperclass()).getActualTypeArguments()[0];
        } catch (ClassCastException e) {
            return null;
        }
    }

    private static <E extends Entity> EntityCommand<E> getCommandInstance(Class<? extends EntityCommand<E>> commandClass) {
        if (commandClass.isMemberClass() && !Modifier.isStatic(commandClass.getModifiers())) {
            Logger.getLogger().warn("In is not possible to register non-static inner command class [{0}]", commandClass);
            return null;
        }
        try {
            Constructor<? extends EntityCommand<E>> ctor = commandClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }
}
