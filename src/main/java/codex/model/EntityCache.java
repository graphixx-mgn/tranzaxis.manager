package codex.model;

import codex.log.Logger;
import codex.utils.Language;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

class EntityCache {

    private static final Boolean DEV_MODE = "1".equals(java.lang.System.getProperty("showCacheOps"));

    private final Map<Class<?>, Map<CacheKey, Entity>> registry = new ConcurrentHashMap<>();
    private final static EntityCache INSTANCE = new EntityCache();
    
    private EntityCache() {}
    
    public static EntityCache getInstance() {
        return INSTANCE;
    }
    
    Entity find(Class entityClass, Integer ownerId, String PID)  {
        if (registry.containsKey(entityClass)) {
            CacheKey key = new CacheKey(PID, ownerId);
            if (DEV_MODE) {
                if (registry.get(entityClass).containsKey(key)) {
                    Entity entity = registry.get(entityClass).get(key);
                    Logger.getLogger().info("Found cached entity {0} by key {1}", entity.model.getQualifiedName(), key);
                    return entity;
                } else {
                    if (registry.get(entityClass).values().parallelStream().anyMatch(entity ->
                            entity.getPID().equals(PID) && (
                                (ownerId == null && entity.getOwner() == null) ||
                                (ownerId != null && ownerId.equals(entity.getOwner().getID()))
                            )
                    )) {
                        Logger.getLogger().warn("Entity not found by key {1} but similar entity has been found", key);
                    }
                }
            } else {
                return registry.get(entityClass).get(key);
            }
        }
        return null;
    }

    void cache(Entity entity, String PID, Integer ownerId) {
        if (!registry.containsKey(entity.getClass())) {
            registry.put(entity.getClass(), Collections.synchronizedMap(new HashMap<>()));
        }
        CacheKey key = new CacheKey(PID, ownerId);
        registry.get(entity.getClass()).put(key, entity);
        if (DEV_MODE) {
            if (Language.NOT_FOUND.equals(PID)) {
                Logger.getLogger().warn("Cached entity {0} by key {1}", entity.model.getQualifiedName(), key);
            } else {
                Logger.getLogger().debug("Cached entity {0} by key {1}", entity.model.getQualifiedName(), key);
            }
        }
    }
    
    void remove(Entity entity) {
        if (registry.containsKey(entity.getClass())) {
            Entity owner = entity.getOwner();
            CacheKey key = new CacheKey(entity.getPID(), owner == null ? null : owner.getID());
            registry.get(entity.getClass()).remove(key);
            if (DEV_MODE) {
                Logger.getLogger().info("Remove cached entity {0} by key {1}", entity.model.getQualifiedName(), key);
            }
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
        public String toString() {
            return "[name="+objectName+", owner="+ownerId+"]";
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
