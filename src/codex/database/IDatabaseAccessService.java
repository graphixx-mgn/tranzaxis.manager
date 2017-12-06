package codex.database;

import codex.service.IService;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Интерфейс сервиса взаимодействия с базой данных.
 */
public interface IDatabaseAccessService extends IService {
    
    /**
     * Регистрация соединения/пула для выполнения дпльнейших операций. Возвращает 
     * уникальный идентификатор соединения. Для каждой уникальной комбинации
     * адрес-пользователь создается отдельное соединение.
     * @param url Адрес БД в формате JDBC для конкретного драйвера.
     * @param user Имя пользователя.
     * @param password Пароль.
     */
    default Integer registerConnection(String url, String user, String password) throws SQLException {
        return null;
    }
    
    /**
     * Исполнение запроса на выборку данных из БД.
     * @param connectionID Идентификатор соединения.
     * @param query Запрос, при необходимости включающий в себя параметры.
     * @param params Список значений параметров запроса, не указывать если 
     * параметров нет.
     */
    default ResultSet select(Integer connectionID, String query, Object... params) throws SQLException {
        return null;
    }
    
    @Override
    default String getTitle() {
        return "Database Access Service";
    }
    
}
