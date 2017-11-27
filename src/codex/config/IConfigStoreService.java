package codex.config;

import codex.model.EntityModel;
import codex.service.IService;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс сервиса загрузки и сохранения данных модели {@link EntityModel}.
 */
public interface IConfigStoreService extends IService {
    
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
     * @throws RemoteException 
     */
    default void addClassProperty(Class clazz, String propName) {};
    
    /**
     * Создать пустую запись в каталоге для модели сушности по её уникальному ключу.
     * @param clazz Класс сущности.
     * @param PID Наименование сущности.
     * @throws RemoteException 
     */
    default Integer initClassInstance(Class clazz, String PID) {
        return null;
    };
    
    /**
     * Удалить запись в каталоге по её уникальному ключу.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     * @throws RemoteException 
     */
    default boolean removeClassInstance(Class clazz, Integer ID) {
        return false;
    };
    
    /**
     * Считать их каталога значение свойства сущности.
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     * @param propName Имя свойства.
     * @throws RemoteException 
     */
    default String readClassProperty(Class clazz, Integer ID, String propName) {
        return null;
    };
    
    /**
     * Сохранить свойства сущности в каталог. 
     * @param clazz Класс сущности.
     * @param ID Уникальный числовой идентификатор сущности.
     * @param properties Список имен свойств, которые требуется сохранить.
     * @return Признак успешности операции сохранения.
     * @throws RemoteException 
     */
    default boolean updateClassInstance(Class clazz, Integer ID, Map<String, String> properties) {
        return false;
    };
    
    /**
     * Получить список первичных ключей записей каталога.
     * @param clazz Класс сущности.
     */
    default List<Map<String, String>> readCatalogEntries(Class clazz) {
        return new LinkedList<>();
    };
    
    default Map<String, String> readClassInstance(Class clazz, Integer ID) {
        return new HashMap<>();
    };
    
    @Override
    default String getTitle() {
        return "Configuration Access Service";
    }
    
}
