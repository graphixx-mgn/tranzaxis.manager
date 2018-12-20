package codex.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class EnityCache {
    
    private final Map<Class<?>, Set<Entity>>  registry  = new ConcurrentHashMap<>();
    private final static EnityCache INSTANCE = new EnityCache();
    
    private EnityCache() {}
    
    public static EnityCache getInstance() {
        return INSTANCE;
    }
    
    List<Entity> list() {
        return new HashMap<>(registry).values().stream().flatMap(entity -> entity.stream()).collect(Collectors.toList());
    }
    
    Entity find(Class entityClass, Integer ownerId, String PID)  {
        if (registry.containsKey(entityClass)) {
            Optional<Entity> found = registry.get(entityClass).stream().filter((entity) -> {
                Integer entityOwnerId = entity.getOwner() == null ? null : entity.getOwner().getID();
                return 
                        entity.model.getPID(false).equals(PID) && (
                            (entityOwnerId == null && ownerId == null) || (ownerId == entityOwnerId)
                        );
            }).findFirst();
            if (found.isPresent()) {
                Entity entity = found.get();
//                Logger.getLogger().debug(
//                        "Provided cached entity ({0}): {1} / Owner: {2}", 
//                        String.format("%10d", entity.hashCode()), 
//                        entity.model.getQualifiedName(),
//                        entity.getOwner() == null ? "<NULL>" : entity.getOwner().model.getQualifiedName()
//                );
                return entity;
            }
        }
        return null;
    }

    void cache(Entity entity) {
        if (!registry.containsKey(entity.getClass())) {
            registry.put(entity.getClass(), Collections.synchronizedSet(new HashSet<>()));
        }
        if (registry.get(entity.getClass()).add(entity)) {
//            Logger.getLogger().debug(
//                    "Registered new entity  ({0}) in cache: {1} / Owner: {2}", 
//                    String.format("%10d", entity.hashCode()), 
//                    entity.model.getQualifiedName(),
//                    entity.getOwner() == null ? "<NULL>" : entity.getOwner().model.getQualifiedName()
//            );
        }
    }
    
    void remove(Entity entity) {
        if (registry.containsKey(entity.getClass())) {
            if (registry.get(entity.getClass()).remove(entity)) {
//                Logger.getLogger().debug(
//                        "Deleted entity ({0}) from cache: {1} / Owner: {2}", 
//                        String.format("%10d", entity.hashCode()), 
//                        entity.model.getQualifiedName(),
//                        entity.getOwner() == null ? "<NULL>" : entity.getOwner().model.getQualifiedName()
//                );
            }
        }
    }

} 
