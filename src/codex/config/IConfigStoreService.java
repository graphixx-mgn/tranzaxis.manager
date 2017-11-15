package codex.config;

import codex.model.EntityModel;
import codex.property.PropertyHolder;
import codex.service.IService;
import codex.type.IComplexType;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;

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
     * @param PID Уникальный строковый идентификатор сущности.
     * @throws RemoteException 
     */
    default void initClassInstance(Class clazz, String PID) {};
    
    /**
     * Удалить запись в каталоге по её уникальному ключу.
     * @param clazz Класс сущности.
     * @param PID Уникальный строковый идентификатор сущности.
     * @throws RemoteException 
     */
    default boolean removeClassInstance(Class clazz, String PID) {
        return false;
    };
    
    /**
     * Считать их каталога значение свойства сущности.
     * @param clazz Класс сущности.
     * @param PID Уникальный строковый идентификатор сущности.
     * @param propName Имя свойства.
     * @param propValue Внутреннее значение свойства сущности.
     * @throws RemoteException 
     */
    default void readClassProperty(Class clazz, String PID, String propName, IComplexType propValue) {};
    
    /**
     * Сохранить свойства сущности в каталог. 
     * @param clazz Класс сущности.
     * @param PID Уникальный строковый идентификатор сущности.
     * @param properties Список имен свойств, которые требуется сохранить.
     * @return Признак успешности операции сохранения.
     * @throws RemoteException 
     */
    default boolean updateClassInstance(Class clazz, String PID, List<PropertyHolder> properties) {
        return false;
    };
    
    /**
     * Получить список первичных ключей записей каталога.
     * @param clazz Класс сущности.
     */
    default List<String> readCatalogEntries(Class clazz) {
        return new LinkedList<>();
    };
    
    @Override
    default String getTitle() {
        return "Configuration Access Service";
    }
    
}
