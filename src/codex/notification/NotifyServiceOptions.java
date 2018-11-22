package codex.notification;

import codex.model.Access;
import codex.service.CommonServiceOptions;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Map;
import codex.type.Str;
import java.util.LinkedHashMap;


public class NotifyServiceOptions extends CommonServiceOptions {
    
    public  final static String PROP_CONDITION = "condition";
    public  final static String PROP_SOURCES   = "sources";
    
    public NotifyServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        model.addUserProp(PROP_CONDITION, new Enum(NotifyCondition.INACTIVE), false, Access.Select);
        model.addUserProp(PROP_SOURCES, 
                new Map<>(Str.class, Bool.class, new LinkedHashMap<>()), 
                false, Access.Select
        );
        
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case PROP_CONDITION:
                    model.getEditor(PROP_SOURCES).setEditable(newValue != NotifyCondition.NEVER);
                    break;
            }
        });
        
        model.getEditor(PROP_SOURCES).setEditable(getCondition() != NotifyCondition.NEVER);
    }
    
    public final NotifyCondition getCondition() {
        return (NotifyCondition) model.getValue(PROP_CONDITION);
    }
    
    public final java.util.Map<Str, Bool> getSources() {
        return (java.util.Map<Str, Bool>) model.getValue(PROP_SOURCES);
    }
    
}
