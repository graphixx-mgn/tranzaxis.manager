package codex.database;

import codex.service.IService;
import codex.type.IComplexType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    
    public static String prepareTraceSQL(String sql, Object... values) {
        AtomicInteger index = new AtomicInteger(-1);
        Object[] flattened = flatten(values).toArray();
        String pattern = sql
                .chars()
                .mapToObj((code) -> {
                    String symbol = String.valueOf((char) code);
                    if ("?".equals(symbol)) {
                        index.addAndGet(1);
                        if (flattened[index.get()] == null) {
                            symbol = "<NULL>";
                        } else if (flattened[index.get()] instanceof IComplexType) {
                            IComplexType complexVal = (IComplexType) flattened[index.get()];
                            symbol = complexVal.getQualifiedValue(complexVal.getValue());
                        } else {
                            symbol = "{"+index.get()+"}";
                        }
                    }
                    return symbol;
                })
                .collect(Collectors.joining());
        return MessageFormat.format(pattern, flattened);
    }
    
    public static Stream<Object> flatten(Object[] array) {
        return Arrays
                .stream(array)
                .flatMap(o -> o instanceof Object[]? flatten((Object[])o): Stream.of(o));
    }
    
}
