package codex.config;

import codex.model.EntityModel;
import codex.service.IService;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс сервиса загрузки и сохранения данных модели {@link EntityModel}.
 */
public interface IConfigStoreService extends IService {
    
    public static int RC_SUCCESS = 0;
    public static int RC_ERROR   = 10;
    public static int RC_DEL_CONSTRAINT = 11;
    
    /**
     * Создать каталог для сохранения моделей сущностей указанного класса. 
     * @param clazz Класс сущности.
     * @throws RemoteException 
     */
    default void createClassCatalog(Class clazz) {};
    
    /**
     * Добавить в каталог новое хранимое свойство сущности.
     * @param clazz Класс сущности.
     * @param propName Имя свойства.
     */
    default void addClassProperty(Class clazz, String propName, Class refClazz) {};
    
    /**
     * Создать пустую запись в каталоге для модели сушности по её уникальному ключу.
     * @param clazz Класс сущности.
     * @param PID Наименование сущности.
     * @return ID созданной сущности.
     */
    default Integer initClassInstance(Class clazz, String PID) {
        return null;
    };
    
    /**
     * Сохранить свойства сущности в каталог. 
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     * @param properties Список имен свойств, которые требуется сохранить.
     * @return Код результата исполнения.
     */
    default int updateClassInstance(Class clazz, Integer ID, Map<String, String> properties) {
        return RC_SUCCESS;
    };
    
    /**
     * Удалить запись в каталоге по её уникальному ключу.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     * @return Код результата исполнения.
     */
    default int removeClassInstance(Class clazz, Integer ID) {
        return RC_SUCCESS;
    };
    
    /**
     * Считать их каталога значение свойства сущности.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     * @param propName Имя свойства.
     */
    default String readClassProperty(Class clazz, Integer ID, String propName) {
        return null;
    };
    
    /**
     * Получить список значений всех записей каталога.
     * @param clazz Класс сущности.
     */
    default List<Map<String, String>> readCatalogEntries(Class clazz) {
        return new LinkedList<>();
    };
    
    /**Получить список значений свойств сущности.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     */
    default Map<String, String> readClassInstance(Class clazz, Integer ID) {
        return new HashMap<>();
    };
    
    default List<ForeignLink> findReferencedEntries(Class clazz, Integer ID) {
        return new ArrayList<>();
    }
    
    @Override
    default String getTitle() {
        return "Configuration Access Service";
    }
    
    public class ForeignLink { 
        public final String  entryClass;
        public final Integer entryID;
        
        public ForeignLink(String  entryClass, Integer entryID) {
            this.entryClass = entryClass;
            this.entryID = entryID;
        }
        
        @Override
        public String toString() {
            return MessageFormat.format("[{0} #{1}]", entryClass, entryID);
        }
    }
    
}
