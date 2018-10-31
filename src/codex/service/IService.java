package codex.service;

/**
 * Интерфейс сервиса.
 */
public interface IService {

    /**
     * Возвращает имя сервиса.
     */
    default String getTitle() {
        return "IService interface";
    };
    
}
