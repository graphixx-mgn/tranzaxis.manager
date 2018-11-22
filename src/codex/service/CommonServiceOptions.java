package codex.service;

import codex.command.EntityCommand;
import codex.mask.IMask;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Map;
import javax.swing.ImageIcon;

/**
 * Класс для хранения обцих настроек сервисов
 */
public class CommonServiceOptions extends Catalog {
    
    public  final static String PROP_CLASS   = "class";
    public  final static String PROP_STARTED = "started";
    
    private final static ImageIcon ICON_STARTED = ImageUtils.getByPath("/images/start.png"); 
    private final static ImageIcon ICON_STOPPED = ImageUtils.getByPath("/images/stop.png"); 
    
    private AbstractService service;
    
    public CommonServiceOptions(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/start.png"), title, null);
        model.addDynamicProp(PROP_CLASS, new ServiceLabel(), Access.Edit, () -> {
            return new ServiceStatus();
        });
        model.addUserProp(PROP_STARTED, new Bool(true), false, Access.Any);
        
        // Commands
        addCommand(new StartService());
        addCommand(new StopService());
    }
    
    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
    
    void setService(AbstractService service) {
        this.service = service;
    }
    
    protected AbstractService getService() {
        return this.service;
    }
    
    public final boolean isStarted() {
        return model.getValue(PROP_STARTED) == Boolean.TRUE;
    }
    
    public final void setStarted(boolean started) {
        model.setValue(PROP_STARTED, started);
        try {
            model.commit(false);
        } catch (Exception e) {}
    }
    
    class ServiceLabel implements IComplexType<ServiceStatus, IMask<ServiceStatus>> {

        private ServiceStatus value;
        
        @Override
        public ServiceStatus getValue() {
            return value;
        }

        @Override
        public void setValue(ServiceStatus value) {
            this.value = value;
        }

        @Override
        public void valueOf(String value) {}

        @Override
        public String getQualifiedValue(ServiceStatus val) {
            return null;
        }
    
    }
    
    class ServiceStatus implements Iconified {

        @Override
        public ImageIcon getIcon() {
            return CommonServiceOptions.this.isStarted() ? ICON_STARTED : ICON_STOPPED;
        }
        
        @Override
        public String toString() {
            return CommonServiceOptions.this.getService().getTitle();
        }
    
    }
    
    private class StartService extends EntityCommand<CommonServiceOptions> {

        public StartService() {
            super(
                    "start", 
                    Language.get("CommonServiceOptions", "start@title"), 
                    ImageUtils.resize(ICON_STARTED, 28, 28), 
                    Language.get("CommonServiceOptions", "start@title"), 
                    (control) -> {
                        return control.getService().isStoppable() && !control.isStarted();
                    }
            );
        }

        @Override
        public void execute(CommonServiceOptions context, Map<String, IComplexType> params) {
            context.setStarted(true);
        }
    
    }
    
    private class StopService extends EntityCommand<CommonServiceOptions> {

        public StopService() {
            super(
                    "stop", 
                    Language.get("CommonServiceOptions", "stop@title"), 
                    ImageUtils.resize(ICON_STOPPED, 28, 28), 
                    Language.get("CommonServiceOptions", "stop@title"), 
                    (control) -> {
                        return control.getService().isStoppable() && control.isStarted();
                    }
            );
        }

        @Override
        public void execute(CommonServiceOptions context, Map<String, IComplexType> params) {
            context.setStarted(false);
        }
    
    }
    
}
