package codex.config;

import codex.model.Entity;
import codex.model.EntityModel;
import codex.service.IService;
import codex.type.EntityRef;
import codex.type.IComplexType;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;

/**
 * Интерфейс сервиса загрузки и сохранения данных модели {@link EntityModel}.
 */
public interface IConfigStoreService extends IService {

    default Connection getConnection() throws SQLException {
        return null;
    }
    
    /**
     * Создать пустую запись в каталоге для модели сушности по её уникальному ключу.
     * @param clazz Класс сущности.
     * @param PID Наименование сущности.
     * @param propDefinition Карта свойств сущности.
     * @param ownerId Идентификатор владельца сущности.
     * @return ID созданной сущности.
     */
    default Map<String, Integer> initClassInstance(Class clazz, String PID, Map<String, IComplexType> propDefinition, Integer ownerId) throws Exception {
        return Collections.emptyMap();
    }
    
    /**
     * Сохранить свойства сущности в каталог. 
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     * @param properties Список имен свойств, которые требуется сохранить.
     */
    default void updateClassInstance(Class clazz, Integer ID, Map<String, IComplexType> properties) throws Exception {}
    
    /**
     * Проверка существования сущности в каталоге.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     */
    default boolean isInstanceExists(Class clazz, Integer ID) {
        return false;
    }
    
    /**
     * Проверка существования сущности в каталоге.
     * @param clazz Класс сущности.
     * @param PID Наименование сущности.
     * @param ownerId Идентификатор владельца сущности.
     */
    default boolean isInstanceExists(Class clazz, String PID, Integer ownerId) {
        return false;
    }
    
    /**Получить список значений свойств сущности.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     */
    default Map<String, String> readClassInstance(Class clazz, Integer ID) {
        return Collections.emptyMap();
    }
    
    /**Получить список значений свойств сущности.
     * @param clazz Класс сущности.
     * @param PID Наименование сущности.
     * @param ownerId Идентификатор владельца сущности.
     */
    default Map<String, String> readClassInstance(Class clazz, String PID, Integer ownerId) {
        return Collections.emptyMap();
    }
    
    /**
     * Получить список (ID, PID) всех записей каталога.
     * @param ownerId Идентификатор владельца сущности.
     * @param entityClass Класс сущности.
     */
    default <E extends Entity> List<EntityRef<E>> readCatalogEntries(Integer ownerId, Class<E> entityClass) {
        return Collections.emptyList();
    }

    default <E extends Entity> List<EntityRef<E>> readCatalogEntries(Class<E> entityClass) {
        return Collections.emptyList();
    }
    
    /**
     * Удалить запись в каталоге по её уникальному ключу.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     */
    default void removeClassInstance(Class clazz, Integer ID) throws Exception {}

    /**
     * Поиск ссылок на сущность
     * @param clazz Класс сущности
     * @param ID Идентификатор сущности
     */
    default List<ForeignLink> findReferencedEntries(Class clazz, Integer ID) {
        return Collections.emptyList();
    }
    
    /**
     * Перестроение таблицы для актуализации структуры.
     * @param clazz Класс сущности.
     * @param unusedProperties Список неиспользуемых колонок
     * @param newProperties Список новых колонок
     */
    default void maintainClassCatalog(Class clazz, List<String> unusedProperties, Map<String, IComplexType> newProperties) throws Exception {}

    boolean deleteConfirmRequired();

    /**
     * Получение класса владельца сущности
     * @param clazz Класс сущности
     */
    default Class<? extends Entity> getOwnerClass(Class clazz) throws Exception {
        return null;
    }
    
    @Override
    default String getTitle() {
        return "Configuration Access Service";
    }

    /**
     * Класс содержащий основную информацию о ссылке.
     */
    class ForeignLink {
        /**
         * Класс сущности.
         */
        public final Class<? extends Entity> entryClass;
        /**
         * Идентификатор сущности.
         */
        public final Integer entryID;
        /**
         * Наименование сущности.
         */
        public final String  entryPID;
        /**
         * Признак входящей ссылки.
         */
        public final Boolean isIncoming;
        
        /**
         * Конструктор ссылки.
         * @param entryClass Класс сущности.
         * @param entryID Идентификатор сущности.
         * @param entryPID Наименование сущности.
         * @param incoming Признак входящей ссылки.
         */
        ForeignLink(Class<? extends Entity>  entryClass, Integer entryID, String entryPID, boolean incoming) {
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
