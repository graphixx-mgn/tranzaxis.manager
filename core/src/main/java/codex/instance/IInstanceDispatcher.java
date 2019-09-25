package codex.instance;

import java.util.List;

public interface IInstanceDispatcher extends IInstanceCommunicationService {

    /**
     * Получить список присоединенных инстанций.
     */
    List<Instance> getInstances();
    /**
     * Добавить слушателя события подключения инстанции.
     */
    void addInstanceListener(IInstanceListener listener);

    /**
     * Удалить слушателя события подключения инстанции.
     */
    void removeInstanceListener(IInstanceListener listener);
}
