package codex.model;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.mask.IMask;
import codex.presentation.SelectorPresentation;
import codex.service.ServiceRegistry;
import codex.utils.Language;
import java.util.List;

/**
 * Маска проверки свойства PID модели сущности, которое должно быть заполнено и 
 * быть уникально. Подключается и удаляется в методе {@link EntityModel#init}. 
 */
class PIDMask implements IMask<String> {
    
    private final static IConfigStoreService STORE = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static String              ERROR = Language.get(
            SelectorPresentation.class.getSimpleName(), 
            "creator@pid.hint"
    );
    private final Class entityClass;

    /**
     * Конструктор маски.
     * @param entityClass Класс сущности, используется для подучения списка PID-ов
     * уже существующих сущностей.
     */
    public PIDMask(Class entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public boolean verify(String value) {
        final List<String> PIDs = STORE.readCatalogEntries(entityClass);
        return value != null && !value.isEmpty() && !PIDs.contains(value);
    }

    @Override
    public String getErrorHint() {
        return ERROR;
    }

    @Override
    public boolean notNull() {
        return true;
    }

}
