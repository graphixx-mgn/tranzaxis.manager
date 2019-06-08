package codex.notification;

import codex.model.Access;
import codex.service.CommonServiceOptions;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Map;
import codex.type.Str;
import codex.utils.ImageUtils;

import java.util.LinkedHashMap;


public class NotifyServiceOptions extends CommonServiceOptions {
    
    public  final static String PROP_SOURCES   = "sources";
    
    public NotifyServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/notify.png"));
        
        model.addUserProp(PROP_SOURCES, 
                new Map(Str.class, Enum.class, new LinkedHashMap<>(), null, NotifyCondition.NEVER), 
                false, Access.Select
        );
    }
    
    public final java.util.Map<Str, Enum> getSources() {
        return (java.util.Map<Str, Enum>) model.getValue(PROP_SOURCES);
    }
    
}
