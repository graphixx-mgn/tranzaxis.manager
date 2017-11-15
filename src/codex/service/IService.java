package codex.service;

/**
 * Интерфейс сервиса.
 */
public interface IService {

    default String getTitle() {
        return "IService interface";
    };
    
}
