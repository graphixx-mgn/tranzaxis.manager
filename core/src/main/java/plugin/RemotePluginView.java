package plugin;

import codex.editor.AnyTypeView;
import codex.editor.IEditor;
import codex.editor.IEditorFactory;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.Language;
import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

class RemotePluginView extends Catalog {

    PluginLoaderService.RemotePlugin remotePlugin;
    Map<String, IPluginLoaderService.PropertyPresentation> properties = new LinkedHashMap<>();

    RemotePluginView(IPluginLoaderService.RemotePlugin remotePlugin) {
        this(null, remotePlugin.getPluginId());
        this.remotePlugin = remotePlugin;
        this.properties.putAll(remotePlugin.getProperties().stream()
                .collect(Collectors.toMap(
                        IPluginLoaderService.PropertyPresentation::getName,
                        property -> property
                ))
        );
        properties.entrySet().stream()
                .filter(entry -> !model.hasProperty(entry.getKey()) && entry.getValue().getAccess().equals(Access.Select))
                .forEach(entry -> {
                    model.addDynamicProp(
                            entry.getKey(),
                            createType(properties.get(entry.getKey())),
                            Access.Select,
                            null
                    );
                    PackageView.changePropertyNaming(
                            model.getProperty(entry.getKey()),
                            Language.get(remotePlugin.getHandlerClass(), entry.getKey()+PropertyHolder.PROP_NAME_SUFFIX),
                            Language.get(remotePlugin.getHandlerClass(), entry.getKey()+PropertyHolder.PROP_DESC_SUFFIX)
                    );
                });
    }

    RemotePluginView(EntityRef owner, String title) {
        super(owner, null, title, null);

        // Properties
        model.addDynamicProp(Plugin.PROP_TYPE,    new AnyType(), null, () -> createType(properties.get(Plugin.PROP_TYPE)).getValue());
        model.addDynamicProp(Plugin.PROP_TYPEDEF, new AnyType(), Access.Edit, () -> createType(properties.get(Plugin.PROP_TYPEDEF)).getValue());

        PackageView.changePropertyNaming(
                model.getProperty(Plugin.PROP_TYPE),
                Language.get(Plugin.class, Plugin.PROP_TYPE+PropertyHolder.PROP_NAME_SUFFIX),
                Language.get(Plugin.class, Plugin.PROP_TYPE+PropertyHolder.PROP_DESC_SUFFIX)
        );
        PackageView.changePropertyNaming(
                model.getProperty(Plugin.PROP_TYPEDEF),
                Language.get(Plugin.class, Plugin.PROP_TYPEDEF+PropertyHolder.PROP_NAME_SUFFIX),
                Language.get(Plugin.class, Plugin.PROP_TYPEDEF+PropertyHolder.PROP_DESC_SUFFIX)
        );

        // Property settings
        setPropertyRestriction(EntityModel.THIS, Access.Any);
    }

    private IComplexType createType(final IPluginLoaderService.PropertyPresentation propPresentation) {
        return new AnyType() {
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

            @Override
            public Object getValue() {
                return new Iconified() {
                    @Override
                    public ImageIcon getIcon() {
                        return propPresentation.getIcon();
                    }

                    @Override
                    public String toString() {
                        return propPresentation.getValue();
                    }
                };
            }
        };
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
}
