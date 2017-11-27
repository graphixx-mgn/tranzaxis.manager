package codex.model;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.mask.IMask;
import codex.presentation.SelectorPresentation;
import codex.service.ServiceRegistry;
import codex.utils.Language;
import java.util.stream.Collectors;

/**
 * Маска проверки наименования модели сущности, которое должно быть заполнено и 
 * быть уникально.
 */
class PIDMask implements IMask<String> {
    
    private final static IConfigStoreService STORE = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static String              ERROR = Language.get(
            SelectorPresentation.class.getSimpleName(), 
            "creator@pid.hint"
    );
    private final Class   entityClass;
    private final Integer ID;

    /**
     * Конструктор маски.
     * @param entityClass Класс сущности, используется для подучения списка 
     * существующих сущностей.
     * @param ID Идентификатор сущности которой назначена маска.
     */
    public PIDMask(Class entityClass, Integer ID) {
        this.entityClass = entityClass;
        this.ID = ID;
    }

    @Override
    public boolean verify(String value) {
        //TODO: При сохранении новой (copy || create) сущности поле подсвечено красным
        if (value != null) {
            return STORE.readCatalogEntries(entityClass).stream().filter((map) -> {
                return map.get(EntityModel.PID).equals(value) && (ID == null || !ID.toString().equals(map.get(EntityModel.ID)));
            }).collect(Collectors.toList()).isEmpty();
        } else {
            return false;
        }
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
