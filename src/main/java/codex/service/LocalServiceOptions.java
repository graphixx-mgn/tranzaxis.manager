package codex.service;

import codex.command.EntityCommand;
import codex.model.*;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.lang.reflect.ParameterizedType;
import java.util.Map;

/**
 * Класс для хранения обцих настроек сервисов
 */
public class LocalServiceOptions<S extends IService> extends Catalog {
    
    private final static String PROP_CLASS   = "class";
    private final static String PROP_STARTED = "started";
    
    private final static ImageIcon ICON_STARTED = ImageUtils.getByPath("/images/start.png"); 
    private final static ImageIcon ICON_STOPPED = ImageUtils.getByPath("/images/stop.png");
    
    private S service;
    
    public LocalServiceOptions(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/services.png"), title, null);
        model.addDynamicProp(PROP_CLASS, new AnyType(), Access.Edit, () -> new Iconified() {
            @Override
            public ImageIcon getIcon() {
                return isStarted() ? LocalServiceOptions.this.getIcon() : ImageUtils.combine(
                        ImageUtils.grayscale(LocalServiceOptions.this.getIcon()),
                        ImageUtils.resize(ICON_STOPPED, 20, 20),
                        SwingConstants.SOUTH_EAST
                );
            }

            @Override
            public String toString() {
                return LocalServiceOptions.this.getService().getTitle();
            }
        });
        model.addUserProp(PROP_STARTED, new Bool(true), false, Access.Any);

        // Property settings
        setPropertyRestriction(EntityModel.THIS, Access.Any);

        IService.Definition definition = getServiceDefinition();
        if (definition != null && definition.optional()) {
            CommandRegistry.getInstance().registerCommand(getClass(), StartService.class);
            CommandRegistry.getInstance().registerCommand(getClass(), StopService.class);
        }
    }
    
    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
    
    void setService(S service) {
        this.service = service;
        setTitle(service.getTitle());
    }
    
    protected S getService() {
        return this.service;
    }
    
    public final boolean isStarted() {
        return model.getValue(PROP_STARTED) == Boolean.TRUE;
    }
    
    public final void setStarted(boolean started) {
        model.setValue(PROP_STARTED, started);
        try {
            model.commit(false);
        } catch (Exception e) {/**/}
    }

    private IService.Definition getServiceDefinition() {
        return ((Class<?>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]).getAnnotation(IService.Definition.class);
    }


    private static class StartService extends EntityCommand<LocalServiceOptions> {

        StartService() {
            super(
                    "start",
                    Language.get(LocalServiceOptions.class, "start@title"),
                    ICON_STARTED,
                    Language.get(LocalServiceOptions.class, "start@title"),
                    (control) -> !control.isStarted()
            );
        }

        @Override
        public void execute(LocalServiceOptions context, Map<String, IComplexType> params) {
            context.setStarted(true);
        }

    }

    private static class StopService extends EntityCommand<LocalServiceOptions> {

        StopService() {
            super(
                    "stop",
                    Language.get(LocalServiceOptions.class, "stop@title"),
                    ICON_STOPPED,
                    Language.get(LocalServiceOptions.class, "stop@title"),
                    LocalServiceOptions::isStarted
            );
        }

        @Override
        public void execute(LocalServiceOptions context, Map<String, IComplexType> params) {
            context.setStarted(false);
        }
    }
    
}
