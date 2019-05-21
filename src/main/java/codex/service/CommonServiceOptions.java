package codex.service;

import codex.command.EntityCommand;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Map;
import javax.swing.ImageIcon;

/**
 * Класс для хранения обцих настроек сервисов
 */
public class CommonServiceOptions extends Catalog {
    
    private final static String PROP_CLASS   = "class";
    private final static String PROP_STARTED = "started";
    
    private final static ImageIcon ICON_STARTED = ImageUtils.getByPath("/images/start.png"); 
    private final static ImageIcon ICON_STOPPED = ImageUtils.getByPath("/images/stop.png"); 
    
    private AbstractService service;
    
    public CommonServiceOptions(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/start.png"), title, null);
        model.addDynamicProp(PROP_CLASS, new AnyType(), Access.Edit, () -> new Iconified() {
            @Override
            public ImageIcon getIcon() {
                return CommonServiceOptions.this.isStarted() ? ICON_STARTED : ICON_STOPPED;
            }

            @Override
            public String toString() {
                return CommonServiceOptions.this.getService().getTitle();
            }
        });
        model.addUserProp(PROP_STARTED, new Bool(true), false, Access.Any);

        // Property settings
        setPropertyRestriction(EntityModel.THIS, Access.Any);
        
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
        } catch (Exception e) {
            //
        }
    }
    
    private class StartService extends EntityCommand<CommonServiceOptions> {

        StartService() {
            super(
                    "start", 
                    Language.get(CommonServiceOptions.class, "start@title"),
                    ImageUtils.resize(ICON_STARTED, 28, 28), 
                    Language.get(CommonServiceOptions.class, "start@title"),
                    (control) -> control.getService().isStoppable() && !control.isStarted()
            );
        }

        @Override
        public void execute(CommonServiceOptions context, Map<String, IComplexType> params) {
            context.setStarted(true);
        }
    
    }
    
    private class StopService extends EntityCommand<CommonServiceOptions> {

        StopService() {
            super(
                    "stop", 
                    Language.get(CommonServiceOptions.class, "stop@title"),
                    ImageUtils.resize(ICON_STOPPED, 28, 28), 
                    Language.get(CommonServiceOptions.class, "stop@title"),
                    (control) -> control.getService().isStoppable() && control.isStarted()
            );
        }

        @Override
        public void execute(CommonServiceOptions context, Map<String, IComplexType> params) {
            context.setStarted(false);
        }
    
    }
    
}
