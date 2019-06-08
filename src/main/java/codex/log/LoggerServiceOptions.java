package codex.log;

import codex.model.Access;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.service.CommonServiceOptions;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.utils.ImageUtils;

import java.util.List;


public final class LoggerServiceOptions extends CommonServiceOptions {
    
    ILogManagementService LMS = (ILogManagementService) ServiceRegistry.getInstance().lookupService(LogManagementService.class);
    
    public final static String PROP_LOG_LEVEL = "logLevel";
    
    public LoggerServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/lamp.png"));
        model.addUserProp(PROP_LOG_LEVEL, new Enum(Level.Debug), false, Access.Select);
        
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
        setLogLevel(getLogLevel());
    }
    
    public Level getLogLevel() {
        return (Level) model.getValue(PROP_LOG_LEVEL);
    }
    
    public void setLogLevel(Level value) {
        model.setValue(PROP_LOG_LEVEL, value);
        LMS.setLevel(value);
    }
}
