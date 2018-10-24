package codex.config;

import codex.model.EntityModel;
import codex.service.IService;
import codex.type.IComplexType;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс сервиса загрузки и сохранения данных модели {@link EntityModel}.
 */
public interface IConfigStoreService extends IService {
    
    public static int RC_SUCCESS        = 0;
    public static int RC_ERROR          = 10;
    public static int RC_DEL_CONSTRAINT = 11;
    
    /**
     * Создать пустую запись в каталоге для модели сушности по её уникальному ключу.
     * @param clazz Класс сущности.
     * @param PID Наименование сущности.
     * @return ID созданной сущности.
     */
    default Map<String, Integer> initClassInstance(Class clazz, String PID, Map<String, IComplexType> propDefinition, Integer ownerId) throws Exception {
        return null;
    };
    
    /**
     * Сохранить свойства сущности в каталог. 
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     * @param properties Список имен свойств, которые требуется сохранить.
     */
    default void updateClassInstance(Class clazz, Integer ID, Map<String, IComplexType> properties) throws Exception {};
    
    /**Получить список значений свойств сущности.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     */
    default Map<String, String> readClassInstance(Class clazz, Integer ID) {
        return new HashMap<>();
    };
    
    /**Получить список значений свойств сущности.
     * @param clazz Класс сущности.
     * @param PID Наименование сущности.
     */
    default Map<String, String> readClassInstance(Class clazz, String PID, Integer ownerId) {
        return new HashMap<>();
    };
    
    /**
     * Получить список (ID, PID) всех записей каталога.
     * @param clazz Класс сущности.
     */
    default Map<Integer, String> readCatalogEntries(Integer ownerId, Class clazz) {
        return new LinkedHashMap<>();
    };
    
    /**
     * Удалить запись в каталоге по её уникальному ключу.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     */
    default void removeClassInstance(Class clazz, Integer ID) throws Exception {};

    /**
     * Поиск ссылок на сущность
     * @param clazz Класс сущности
     * @param ID Идентификатор сущности
     */
    default List<ForeignLink> findReferencedEntries(Class clazz, Integer ID) {
        return new ArrayList<>();
    }
    
    /**
     * Перестроение таблицы для актуализации структуры.
     * @param unusedProperties Список неиспользуемых колонок
     * @param newProperties Список новых колонок
     */
    default public void maintainClassCatalog(Class clazz, List<String> unusedProperties, Map<String, IComplexType> newProperties) throws Exception {}
    
    /**
     * Получение класса владельца сущности
     * @param clazz Класс сущности
     */
    default public Class getOwnerClass(Class clazz) throws Exception {
        return null;
    }
    
    @Override
    default String getTitle() {
        return "Configuration Access Service";
    }
    
    public class ForeignLink { 
        public final String  entryClass;
        public final Integer entryID;
        public final String  entryPID;
        public final Boolean isIncoming;
        
        public ForeignLink(String  entryClass, Integer entryID, String entryPID, boolean incoming) {
            this.entryClass = entryClass;
            this.entryID    = entryID;
            this.entryPID   = entryPID;
            this.isIncoming = incoming;
        }
        
        @Override
        public String toString() {
            return MessageFormat.format("[{0} {1}-{2}]", isIncoming ? "from" : "to", entryClass, entryID);
        }
    }
    
}
