package codex.service;

import codex.command.EntityCommand;
import codex.model.*;
import codex.type.*;
import codex.utils.Caller;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Класс для хранения обцих настроек сервисов
 */
public abstract class LocalServiceOptions<S extends IService> extends Catalog {
    
    private final static String PROP_CLASS   = "class";
    private final static String PROP_STARTED = "started";
    
    private final static ImageIcon ICON_STARTED = ImageUtils.getByPath("/images/start.png"); 
    private final static ImageIcon ICON_STOPPED = ImageUtils.getByPath("/images/stop.png");

    private final static String APP_CATALOG = Caller.getInstance().getClassStack().stream().reduce((first, second) -> second).orElse(null).getSimpleName();
    private final static String SRV_CATALOG = "Services";

    private static <S extends IService> void setProperty(Class<S> serviceClass, String propName, String value) {
        getPreferences(serviceClass).put(propName, value);
    }

    private static <S extends IService> Preferences getPreferences(Class<S> serviceClass) {
        return Preferences.userRoot().node(APP_CATALOG).node(SRV_CATALOG).node(serviceClass.getTypeName());
    }

    public static <S extends IService> String getProperty(Class<S> serviceClass, String propName) {
        return getPreferences(serviceClass).get(propName, null);
    }
    
    private S service;

    protected LocalServiceOptions(S service) {
        super(null, ImageUtils.getByPath("/images/services.png"), service.getTitle(), null);
        this.service = service;

        // Properties
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

        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                changes.stream()
                        .filter(propName -> !EntityModel.SYSPROPS.contains(propName))
                        .forEach(propName -> setProperty(
                            getService().getClass(),
                            propName,
                            model.getProperty(propName).getOwnPropValue().toString()
                        ));
            }
        });
    }
    
    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
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
