package codex.instance;

import codex.model.Access;
import codex.service.CommonServiceOptions;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.utils.Language;


public class CommunicationServiceOptions extends CommonServiceOptions {
    
    public final static String PROP_SHOW_NET_OPS = "showNetOps";
    
    public CommunicationServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        
        model.addUserProp(PROP_SHOW_NET_OPS, new Bool(true), false, Access.Select);
        model.addPropertyGroup(Language.get(CommonServiceOptions.class.getSimpleName(), "debug@options"), PROP_SHOW_NET_OPS);
    }
    
    public final boolean isShowNetOps() {
        return model.getValue(PROP_SHOW_NET_OPS) == Boolean.TRUE;
    }
    
}
