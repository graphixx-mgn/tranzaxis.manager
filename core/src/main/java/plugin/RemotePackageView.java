package plugin;

import codex.editor.AnyTypeView;
import codex.editor.IEditor;
import codex.editor.IEditorFactory;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.Iconified;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;

class RemotePackageView extends Catalog {

    private final static ImageIcon ICON_CREATE = ImageUtils.getByPath("/images/plus.png");
    private final static ImageIcon ICON_UPDATE = ImageUtils.getByPath("/images/up.png");

    private final static String PROP_VERSION = "version";
    private final static String PROP_UPGRADE = "upgrade";
    private final static String PROP_AUTHOR  = "author";

    PluginLoaderService.RemotePackage remotePackage;

    RemotePackageView(PluginLoaderService.RemotePackage remotePackage) {
        this(null, remotePackage.getTitle());
        this.remotePackage = remotePackage;
        setIcon(remotePackage.getIcon());

        if (remotePackage.getPlugins().size() == 1) {
            IPluginLoaderService.RemotePlugin remotePlugin = remotePackage.getPlugins().get(0);

            remotePlugin.getProperties().stream()
                    .filter(propPresentation -> propPresentation.getAccess().equals(Access.Select))
                    .forEach(propPresentation -> {
                        String propName = propPresentation.getName();
                        model.addDynamicProp(
                                propName,
                                new AnyType() {
                                    @Override
                                    public IEditorFactory editorFactory() {
                                        return propHolder -> {
                                            try {
                                                Constructor<? extends IEditor> ctor = propPresentation.getEditor().getConstructor(PropertyHolder.class);
                                                return ctor.newInstance(propHolder);
                                            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                                                e.printStackTrace();
                                                return new AnyTypeView(propHolder);
                                            }
                                        };
                                    }
                                }, Access.Select, () -> new Iconified() {
                                    @Override
                                    public ImageIcon getIcon() {
                                        return propPresentation.getIcon();
                                    }

                                    @Override
                                    public String toString() {
                                        return propPresentation.getValue();
                                    }
                                });
                        String propTitle = Language.get(
                                propName.equals(Plugin.PROP_TYPE) ? Plugin.class : remotePlugin.getHandlerClass(),
                                propName+PropertyHolder.PROP_NAME_SUFFIX
                        );
                        String propDesc = Language.get(
                                propName.equals(Plugin.PROP_TYPE) ? Plugin.class : remotePlugin.getHandlerClass(),
                                propName+PropertyHolder.PROP_DESC_SUFFIX
                        );
                        PackageView.changePropertyNaming(model.getProperty(propName), propTitle, propDesc);
                    });
        } else {
            remotePackage.getPlugins().forEach(remotePlugin -> insert(new RemotePluginView(remotePlugin)));
        }
    }

    RemotePackageView(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/repository.png"), title, null);

        // Properties
        model.addDynamicProp(PROP_VERSION, new AnyType(), Access.Select, () -> remotePackage.getVersion());
        model.addDynamicProp(PROP_UPGRADE, new AnyType(), Access.Edit, () -> {
            final PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(remotePackage.getId());
            return new Iconified() {
                @Override
                public ImageIcon getIcon() {
                    return localPackage == null ? ICON_CREATE : ICON_UPDATE;
                }

                @Override
                public String toString() {
                    return localPackage == null ? remotePackage.getVersion() : MessageFormat.format(
                            "<html>{0} &rarr; {1}</html>",
                            localPackage.getVersion(),
                            remotePackage.getVersion()
                    );
                }
            };
        });
        model.addDynamicProp(PROP_AUTHOR, new Str(null), null, () -> remotePackage.getAuthor());
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return RemotePluginView.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }
}
