package codex.service;

import codex.command.EntityCommand;
import codex.model.*;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Map;
import javax.swing.*;

/**
 * Класс для хранения обцих настроек сервисов
 */
public class CommonServiceOptions extends Catalog {
    
    private final static String PROP_CLASS   = "class";
    private final static String PROP_STARTED = "started";
    
    private final static ImageIcon ICON_STARTED = ImageUtils.getByPath("/images/start.png"); 
    private final static ImageIcon ICON_STOPPED = ImageUtils.getByPath("/images/stop.png");

    static {
        CommandRegistry.getInstance().registerCommand(StartService.class);
        CommandRegistry.getInstance().registerCommand(StopService.class);
    }
    
    private AbstractService service;
    
    public CommonServiceOptions(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/services.png"), title, null);
        model.addDynamicProp(PROP_CLASS, new AnyType(), Access.Edit, () -> new Iconified() {
            @Override
            public ImageIcon getIcon() {
                return CommonServiceOptions.this.isStarted() ? CommonServiceOptions.this.getIcon() : ImageUtils.combine(
                        ImageUtils.grayscale(CommonServiceOptions.this.getIcon()),
                        ImageUtils.resize(ICON_STOPPED, 20, 20),
                        SwingConstants.SOUTH_EAST
                );
            }

            @Override
            public String toString() {
                return CommonServiceOptions.this.getService().getTitle();
            }
        });
        model.addUserProp(PROP_STARTED, new Bool(true), false, Access.Any);

        // Property settings
        setPropertyRestriction(EntityModel.THIS, Access.Any);
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
                    ICON_STARTED,
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
                    ICON_STOPPED,
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
