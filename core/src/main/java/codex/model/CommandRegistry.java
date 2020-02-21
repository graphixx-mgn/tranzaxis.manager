package codex.model;

import codex.command.EntityCommand;
import codex.log.Logger;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public final class CommandRegistry {

    private final static CommandRegistry INSTANCE = new CommandRegistry();
    public static CommandRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<Class<? extends Entity>, List<EntityCommand<? extends Entity>>> REGISTRY = new HashMap<>();

    private CommandRegistry() {}

    public synchronized EntityCommand<? extends Entity> registerCommand(Class<? extends EntityCommand<? extends Entity>> commandClass) {
        Class<? extends Entity> entityClass = getCommandEntityClass(commandClass);
        if (!REGISTRY.containsKey(entityClass)) {
            REGISTRY.put(entityClass, new LinkedList<>());
        }
        EntityCommand<? extends Entity> command = getCommandInstance(commandClass);
        if (command != null) {
            REGISTRY.get(entityClass).add(command);
        }
        return command;
    }

    public synchronized EntityCommand<? extends Entity> registerCommand(Class<? extends Entity> entityClass, Class<? extends EntityCommand<? extends Entity>> commandClass) {
        if (!REGISTRY.containsKey(entityClass)) {
            REGISTRY.put(entityClass, new LinkedList<>());
        }
        EntityCommand<? extends Entity> command = getCommandInstance(commandClass);
        if (command != null) {
            REGISTRY.get(entityClass).add(command);
        }
        return command;
    }

    public synchronized void unregisterCommand(Class<? extends EntityCommand<? extends Entity>> commandClass) {
        Class<? extends Entity> entityClass = getCommandEntityClass(commandClass);
        if (REGISTRY.containsKey(entityClass)) {
            REGISTRY.get(entityClass).removeIf(command -> command.getClass().equals(commandClass));
        }
    }

    public synchronized void unregisterCommand(Class<? extends Entity> entityClass, Class<? extends EntityCommand<? extends Entity>> commandClass) {
        if (REGISTRY.containsKey(entityClass)) {
            REGISTRY.get(entityClass).removeIf(command -> command.getClass().equals(commandClass));
        }
    }


    public synchronized List<EntityCommand<? extends Entity>> getRegisteredCommands(Class<? extends Entity> entityClass) {
        List<EntityCommand<? extends Entity>> entityCommands = new LinkedList<>();
        entityCommands.addAll(REGISTRY.getOrDefault(entityClass, Collections.emptyList()));
        Class superClass = entityClass.getSuperclass();
        if (Entity.class.isAssignableFrom(superClass) && !Modifier.isAbstract(superClass.getModifiers())) {
            entityCommands.addAll(getRegisteredCommands((Class<Entity>) superClass));
        }
        return entityCommands;
    }

    @SuppressWarnings("unchecked")
    private static Class<Entity> getCommandEntityClass(Class<? extends EntityCommand<? extends Entity>> commandClass) {
        try {
            return (Class<Entity>) ((ParameterizedType) commandClass.getGenericSuperclass()).getActualTypeArguments()[0];
        } catch (ClassCastException e) {
            return null;
        }
    }

    private static EntityCommand<? extends Entity> getCommandInstance(Class<? extends EntityCommand<? extends Entity>> commandClass) {
        if (commandClass.isMemberClass() && !Modifier.isStatic(commandClass.getModifiers())) {
            Logger.getLogger().warn("In is not possible to register non-static inner command class [{0}]", commandClass);
            return null;
        }
        try {
            Constructor<? extends EntityCommand<? extends Entity>>  ctor = commandClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }
}
