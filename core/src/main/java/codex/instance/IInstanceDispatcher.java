package codex.instance;

import codex.service.IRemoteService;
import java.util.List;

public interface IInstanceDispatcher extends IInstanceCommunicationService {

    /**
     * Получить список присоединенных инстанций.
     */
    List<Instance> getInstances();

    /**
     * Публикация внешнего сетевого сервиса
     */
    void registerRemoteService(Class<? extends IRemoteService> remoteServiceClass);

    /**
     * Добавить слушателя события подключения инстанции.
     */
    void addInstanceListener(IInstanceListener listener);

    /**
     * Удалить слушателя события подключения инстанции.
     */
    void removeInstanceListener(IInstanceListener listener);
}
