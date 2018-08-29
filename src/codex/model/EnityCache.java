package codex.model;

import codex.type.IComplexType;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

class EnityCache {
    
    private final Map<Class<?>, List<Entity>> store = new HashMap<>();
    private final Semaphore semaphore = new Semaphore(1, true);
    private final static EnityCache INSTANCE = new EnityCache();
    
    private EnityCache() {}
    
    public static EnityCache getInstance() {
        return INSTANCE;
    }

    public Entity findEntity(Class entityClass, Integer ownerId, String PID)  {
        if (store.containsKey(entityClass) && PID != null) {
            try {
                semaphore.acquire();
                return store.get(entityClass).stream().filter((entity) -> {
                    return 
                            PID.equals(entity.model.getPID()) && (
                                (entity.model.getOwner() == null && ownerId == null) || (ownerId.equals(entity.model.getOwner().model.getID()))
                            );
                }). findFirst().orElse(null);
            } catch (InterruptedException e) {
            } finally {
                semaphore.release();
            }
        }
        return null;
    }

    public void addEntity(Entity entity) {
        if (IComplexType.coalesce(entity.model.getID(), entity.model.getPID()) == null) {
            return;
        }
        if (!store.containsKey(entity.getClass())) {
            store.put(entity.getClass(), new LinkedList<>());
        }
        try {
            semaphore.acquire();
            store.get(entity.getClass()).add(entity);
        } catch (InterruptedException e) {
        } finally {
            semaphore.release();
        }
    }
} 
