package codex.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

class EntityCache {
    
    private final Map<Class<?>, Map<CacheKey, Entity>> registry = new ConcurrentHashMap<>();
    private final static EntityCache INSTANCE = new EntityCache();
    
    private EntityCache() {}
    
    public static EntityCache getInstance() {
        return INSTANCE;
    }
    
    Entity find(Class entityClass, Integer ownerId, String PID)  {
        if (registry.containsKey(entityClass)) {
            CacheKey key = new CacheKey(PID, ownerId);
            return registry.get(entityClass).get(key);
        }
        return null;
    }

    void cache(Entity entity, String PID, Integer ownerId) {
        if (!registry.containsKey(entity.getClass())) {
            registry.put(entity.getClass(), Collections.synchronizedMap(new HashMap<>()));
        }
        registry.get(entity.getClass()).put(
                new CacheKey(PID, ownerId),
                entity
        );
    }
    
    void remove(Entity entity) {
        if (registry.containsKey(entity.getClass())) {
            Entity owner = entity.getOwner();
            registry.get(entity.getClass()).remove(new CacheKey(entity.getPID(), owner == null ? null : owner.getID()));
        }
    }
    
    private class CacheKey {
        private final String  objectName;
        private final Integer ownerId;

        public CacheKey(String objectName, Integer ownerId) {
            this.objectName = objectName;
            this.ownerId = ownerId;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 17 * hash + Objects.hashCode(this.objectName);
            hash = 17 * hash + Objects.hashCode(this.ownerId);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CacheKey other = (CacheKey) obj;
            if (!Objects.equals(this.objectName, other.objectName)) {
                return false;
            }
            if (!Objects.equals(this.ownerId, other.ownerId)) {
                return false;
            }
            return true;
        }

    }


} 
