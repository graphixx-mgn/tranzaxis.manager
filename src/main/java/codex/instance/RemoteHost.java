package codex.instance;

import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.type.Str;
import codex.utils.ImageUtils;
import java.text.MessageFormat;
import javax.swing.ImageIcon;


class RemoteHost extends Catalog {
    
    private static final ImageIcon ICON_REMOTE = ImageUtils.getByPath("/images/remotehost.png");
    
    public  final static String PROP_USER_NAME = "userName";
    public  final static String PROP_HOST_NAME = "hostName";
    public  final static String PROP_HOST_ADDR = "hostAddress";
    
    private final Instance instance;

    RemoteHost(Instance instance) {
        super(null, ICON_REMOTE, instance.user, null);
        setTitle(MessageFormat.format("{0}/{1}", instance.address, instance.user));
        
        model.addDynamicProp(PROP_USER_NAME, new Str(null), null, () -> {
            return getPID();
        });
        
        model.addDynamicProp(PROP_HOST_NAME, new Str(null), Access.Select, () -> {
            return instance.host;
        });
        
        model.addDynamicProp(PROP_HOST_ADDR, new Str(null), Access.Select, () -> {
            return instance.address.getHostAddress();
        });
        this.instance = instance;
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
    
    public final Instance getInstance() {
        return instance;
    }
    
}
