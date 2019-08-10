package codex.model;

import codex.command.EntityCommand;
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

    private CommandRegistry() {
//        Logger.getLogger().debug("Initialize Command Registry");
//        Logger.getLogger().debug("CMD: Load commands...");
//        StreamSupport.stream(ClassIndex.getSubclasses(EntityCommand.class).spliterator(), false)
//                .map(cmdClass -> (Class<EntityCommand<Entity>>) cmdClass)
//                .filter(cmdClass -> {
//                    if (Modifier.isAbstract(cmdClass.getModifiers())) {
//                        Logger.getLogger().debug("CMD: Skip abstract command: {0}", cmdClass.getCanonicalName());
//                        return false;
//                    }
//                    Class<Entity> entityClass = getCommandEntityClass(cmdClass);
//                    if (entityClass == null) {
//                        Logger.getLogger().debug("CMD: Skip parametrized command: {0}", cmdClass.getCanonicalName());
//                        return false;
//                    } else if (entityClass.equals(Entity.class)) {
//                        Logger.getLogger().debug("CMD: Skip general purpose command: {0}", cmdClass.getCanonicalName());
//                        return false;
//                    }
//                    Constructor<EntityCommand<Entity>> ctor = getCommandDefConstructor(cmdClass, entityClass);
//                    if (ctor == null) {
//                        Logger.getLogger().debug("CMD: Skip parametrized command: {0} (manual registration)", cmdClass.getCanonicalName());
//                        return false;
//                    }
//                    return true;
//                })
//                .collect(Collectors.toMap(
//                        cmdClass -> cmdClass,
//                        CommandRegistry::getCommandEntityClass
//                )).entrySet().stream()
//                .collect(Collectors.groupingBy(Map.Entry::getValue))
//                .forEach((entityClass, entries) -> {
//                    Logger.getLogger().debug(
//                            "Register commands for entity class [{0}]:\n{1}",
//                            entityClass.getSimpleName(),
//                            entries.stream().map(entry -> MessageFormat.format(
//                                    "* Command: class={0}",
//                                    entry.getKey().getCanonicalName()
//                            )).collect(Collectors.joining("\n"))
//                    );
//                    entries.forEach(entry -> {
//                        registerCommand(entityClass, entry.getKey());
//                    });
//                });
    }

    public synchronized EntityCommand<? extends Entity> registerCommand(Class<? extends EntityCommand<? extends Entity>> commandClass) {
        Class<? extends Entity> entityClass = getCommandEntityClass(commandClass);
        if (!REGISTRY.containsKey(entityClass)) {
            REGISTRY.put(entityClass, new LinkedList<>());
        }
        EntityCommand<? extends Entity> command = getCommandInstance(commandClass);
        REGISTRY.get(entityClass).add(command);
        return command;
    }

    public synchronized EntityCommand<? extends Entity> registerCommand(Class<? extends Entity> entityClass, Class<? extends EntityCommand<? extends Entity>> commandClass) {
        if (!REGISTRY.containsKey(entityClass)) {
            REGISTRY.put(entityClass, new LinkedList<>());
        }
        EntityCommand<? extends Entity> command = getCommandInstance(commandClass);
        REGISTRY.get(entityClass).add(command);
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
        try {
            Class<Entity> entityClass = getCommandEntityClass(commandClass);
            if (commandClass.isMemberClass() && commandClass.getEnclosingClass().equals(entityClass) && !Modifier.isStatic(commandClass.getModifiers())) {
                Constructor<? extends EntityCommand<? extends Entity>> ctor = commandClass.getDeclaredConstructor(entityClass);
                ctor.setAccessible(true);
                return ctor.newInstance(Entity.newPrototype(entityClass));
            } else {
                Constructor<? extends EntityCommand<? extends Entity>>  ctor = commandClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }
}
