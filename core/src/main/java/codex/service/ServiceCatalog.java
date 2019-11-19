package codex.service;

import codex.model.Catalog;
import codex.model.Entity;
import codex.utils.ImageUtils;

/**
 * Сущность-контейнер настроек сервисов {@link Service} и
 * производных от него.
 */
public class ServiceCatalog extends Catalog {

    ServiceCatalog() {
        super(null, ImageUtils.getByPath("/images/services.png"), null, null);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return Service.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

}
