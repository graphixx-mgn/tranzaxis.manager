package codex.instance;

import codex.model.EntityDefinition;
import codex.service.RemoteServiceOptions;
import codex.model.Entity;
import codex.service.Service;
import codex.type.EntityRef;

@EntityDefinition(icon = "/images/remotehost.png")
public class CommunicationServiceOptions extends Service<InstanceCommunicationService> {
    public CommunicationServiceOptions(EntityRef owner, String title) {
        super(owner, title);
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
