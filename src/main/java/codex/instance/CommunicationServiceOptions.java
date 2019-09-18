package codex.instance;

import codex.service.RemoteServiceOptions;
import codex.model.Entity;
import codex.service.LocalServiceOptions;
import codex.utils.ImageUtils;

public class CommunicationServiceOptions extends LocalServiceOptions<InstanceCommunicationService> {

    public CommunicationServiceOptions(InstanceCommunicationService service) {
        super(service);
        setIcon(ImageUtils.getByPath("/images/remotehost.png"));
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return RemoteServiceOptions.class;
    }
}
