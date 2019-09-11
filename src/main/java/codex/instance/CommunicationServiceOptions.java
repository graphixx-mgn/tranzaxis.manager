package codex.instance;

import codex.service.RemoteServiceOptions;
import codex.model.Access;
import codex.model.Entity;
import codex.service.LocalServiceOptions;
import codex.type.Bool;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class CommunicationServiceOptions extends LocalServiceOptions<InstanceCommunicationService> {
    
    private final static String PROP_SHOW_NET_OPS = "showNetOps";
    public CommunicationServiceOptions(InstanceCommunicationService service) {
        super(service);
        setIcon(ImageUtils.getByPath("/images/remotehost.png"));
        
        model.addUserProp(PROP_SHOW_NET_OPS, new Bool(true), false, Access.Select);
        model.addPropertyGroup(Language.get(LocalServiceOptions.class, "debug@options"), PROP_SHOW_NET_OPS);
    }
    
    public final boolean isShowNetOps() {
        return model.getValue(PROP_SHOW_NET_OPS) == Boolean.TRUE;
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
