package codex.log;

import codex.model.Access;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.service.CommonServiceOptions;
import codex.service.ServiceRegistry;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import java.util.List;


public class LoggerServiceOptions extends CommonServiceOptions {
    
    ILogManagementService LMS = (ILogManagementService) ServiceRegistry.getInstance().lookupService(LogManagementService.class);
    
    public final static String PROP_LOG_LEVEL = "logLevel";
    public final static String PROP_SHOW_SQL  = "showSql";
    
    public LoggerServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        model.addUserProp(PROP_LOG_LEVEL, new Enum(Level.Debug), false, Access.Select);
        model.addUserProp(PROP_SHOW_SQL,  new Bool(true), false, Access.Select);
        
        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                changes.forEach((propName) -> {
                    switch (propName) {
                        case PROP_LOG_LEVEL:
                            setLogLevel(getLogLevel());
                            break;
                    }
                });          
            }
        });
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case PROP_LOG_LEVEL:
                    model.getEditor(PROP_SHOW_SQL).setEditable(newValue == Level.Debug);
                    break;
            }
        });
        
        setLogLevel(getLogLevel());
        model.getEditor(PROP_SHOW_SQL).setEditable(getLogLevel() == Level.Debug);
    }
    
    private Level getLogLevel() {
        return (Level) model.getValue(PROP_LOG_LEVEL);
    }
    
    private void setLogLevel(Level value) {
        model.setValue(PROP_LOG_LEVEL, value);
        LMS.setLevel(value);
    }
    
    public final boolean isShowSQL() {
        return model.getValue(PROP_SHOW_SQL) == Boolean.TRUE;
    }
    
}
