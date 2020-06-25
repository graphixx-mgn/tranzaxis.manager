package plugin;

import codex.editor.AnyTypeView;
import codex.editor.IEditorFactory;
import codex.editor.TextView;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.Iconified;
import codex.utils.Language;
import javax.swing.*;

final class RemotePluginView extends Catalog {

    RemotePluginView(IPluginLoaderService.RemotePlugin remotePlugin) {
        super(null, null, remotePlugin.getPluginId(), null);

        remotePlugin.getProperties().forEach(propPresentation -> {
            final String propName = propPresentation.getName();
            if (propName.equals(EntityModel.THIS)) {
                setIcon(propPresentation.getIcon());
                setTitle(propPresentation.getValue());
            } else {
                Class<?> propClass = Plugin.VIEW_PROPS.contains(propName) ? Plugin.class : remotePlugin.getHandlerClass();
                model.addDynamicProp(
                        propName,
                        Language.get(propClass, propName+PropertyHolder.PROP_NAME_SUFFIX),
                        Language.get(propClass, propName+PropertyHolder.PROP_DESC_SUFFIX),
                        new AnyType() {
                            @Override
                            public IEditorFactory<AnyType, Object> editorFactory() {
                                return propHolder -> propName.equals(Plugin.PROP_DESC) ? new TextView(propHolder) : new AnyTypeView(propHolder);
                            }
                        },
                        propPresentation.getAccess(),
                        () -> propPresentation.getIcon() == null ? propPresentation.getValue() :
                                new Iconified() {
                                    @Override
                                    public ImageIcon getIcon() {
                                        return propPresentation.getIcon();
                                    }

                                    @Override
                                    public String toString() {
                                        return propPresentation.getValue();
                                    }
                                }
                );
            }
        });
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
}
