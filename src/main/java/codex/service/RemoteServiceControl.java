package codex.service;

import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.Iconified;
import codex.utils.ImageUtils;

public class RemoteServiceControl<T extends IRemoteService> extends Catalog {

    private final static String PROP_INFO = "serviceInfo";

    private T remoteService;

    public RemoteServiceControl(EntityRef owner, String title) {
        super(
                owner,
                ImageUtils.getByPath("/images/services.png"),
                title,
                null
        );

        // Properties
        model.addDynamicProp(PROP_INFO, new AnyType(), Access.Edit, this::getServiceInfo);
    }

    public Iconified getServiceInfo() {
        return null;
    }

    public RemoteServiceControl bindService(T remoteService) {
        this.remoteService = remoteService;
        return this;
    }

    public T getService() {
        return this.remoteService;
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
}
