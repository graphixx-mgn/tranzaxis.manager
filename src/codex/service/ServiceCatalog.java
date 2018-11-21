package codex.service;

import codex.model.Catalog;
import codex.model.Entity;
import codex.utils.ImageUtils;

/**
 * Сущность-контейнер настроек сервисов {@link CommonServiceOptions} и 
 * производных от него.
 */
public class ServiceCatalog extends Catalog {

    public ServiceCatalog() {
        super(null, ImageUtils.getByPath("/images/services.png"), null, null);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return CommonServiceOptions.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }
    
}
