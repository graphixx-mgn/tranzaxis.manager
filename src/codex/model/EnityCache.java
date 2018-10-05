package codex.model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class EnityCache {
    
    private final Map<Class<?>, List<Entity>> store = new HashMap<>();
    private final static EnityCache INSTANCE = new EnityCache();
    
    private EnityCache() {}
    
    public static EnityCache getInstance() {
        return INSTANCE;
    }
    
    public synchronized Entity findEntity(Class entityClass, Integer ownerId, String PID)  {
        if (store.containsKey(entityClass) && PID != null) {
            return store.get(entityClass).stream().filter((entity) -> {
                Integer entityOwnerId = entity.model.getOwner() == null ? null : entity.model.getOwner().model.getID();
                return 
                        PID.equals(entity.model.getPID()) && (
                            (entityOwnerId == null && ownerId == null) || (ownerId == entityOwnerId)
                        );
            }).findFirst().orElse(null);
        }
        return null;
    }

    public synchronized void addEntity(Entity entity) {
        if (!store.containsKey(entity.getClass())) {
            store.put(entity.getClass(), new LinkedList<>());
        }
        store.get(entity.getClass()).add(entity);
    }
    
    public synchronized void removeEntity(Entity entity) {
        if (store.containsKey(entity.getClass())) {
            store.get(entity.getClass()).remove(entity);
        }
    }
} 
