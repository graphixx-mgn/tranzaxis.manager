package plugin.job;

import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageType;
import codex.component.panel.HTMLView;
import codex.config.IConfigStoreService;
import codex.editor.IEditor;
import codex.explorer.tree.INode;
import codex.model.ClassCatalog;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.PolyMorph;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import plugin.PluginException;
import plugin.PluginHandler;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class JobPluginHandler extends PluginHandler<JobPlugin> {

    private final static String PROP_JOB = "job";

    private final Iconified typeDescription = new Iconified() {
        @Override
        public ImageIcon getIcon() {
            return JobPlugin.JOB_ICON;
        }
        @Override
        public String toString() {
            return Language.get(JobPluginHandler.class, "type");
        }
    };

    protected JobPluginHandler(Class<JobPlugin> pluginClass) {
        super(pluginClass);
    }

    @Override
    protected Map<String, Supplier<Iconified>> getTypeDefinition() {
        return new LinkedHashMap<String, Supplier<Iconified>>() {{
            put(PROP_JOB, () -> new Iconified() {
                @Override
                public ImageIcon getIcon() {
                    return ImageUtils.getByPath(getPluginClass(), Language.get(getPluginClass(), "icon"));
                }
                @Override
                public String toString() {
                    return getTitle();
                }
            });
        }};
    }

    @Override
    protected final boolean loadPlugin() throws PluginException {
        super.loadPlugin();
        ClassCatalog.registerClassCatalog(getPluginClass());
        return true;
    }

    @Override
    protected final boolean unloadPlugin() throws PluginException {
        Class<? extends PolyMorph> tableClass = PolyMorph.getPolymorphClass(getPluginClass());
        Collection<Entity> references = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).readCatalogEntries(tableClass).stream()
                .filter(entityRef -> (entityRef.getValue()).getClass().equals(getPluginClass()))
                .map(EntityRef::getValue)
                .collect(Collectors.toList());
        if (!references.isEmpty()) {
            showUsingObjects(references);
            return false;
        } else {
            ClassCatalog.unregisterClassCatalog(getPluginClass());
        }
        super.unloadPlugin();
        return true;
    }

    @Override
    protected final boolean reloadPlugin(PluginHandler<JobPlugin> pluginHandler) throws PluginException {
        Class<? extends PolyMorph> tableClass = PolyMorph.getPolymorphClass(getPluginClass());
        Collection<Entity> references = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).readCatalogEntries(tableClass).stream()
                .filter(entityRef -> (entityRef.getValue()).getClass().equals(getPluginClass()))
                .map(EntityRef::getValue)
                .collect(Collectors.toList());
        if (!references.isEmpty()) {
            references.forEach(entity -> {
                // Clear ID to prevent model removal
                // noinspection unchecked
                entity.model.getProperty(EntityModel.ID).getPropValue().setValue(null);
                //entity.close(); // ????
                // Drop object from cache
                entity.model.remove();
                // Create new instance of reloaded class
                Entity newInstance = Entity.newInstance(
                        pluginHandler.getPluginClass().asSubclass(PolyMorph.class),
                        Entity.findOwner(entity.getParent()),
                        entity.getPID()
                );
                // Replace object
                if (entity.getParent() != null) {
                    INode parent = entity.getParent();
                    int position = parent.getIndex(entity);
                    parent.replace(newInstance, position);
                }
            });
        }
        super.unloadPlugin();
        super.loadPlugin();
        return true;
    }

    @Override
    protected String getTitle() {
        return Language.get(getPluginClass(), "title");
    }

    @Override
    protected Iconified getDescription() {
        return typeDescription;
    }

    private void showUsingObjects(Collection<Entity> references) {
        new Dialog(
                FocusManager.getCurrentManager().getActiveWindow(),
                MessageType.WARNING.getIcon(),
                MessageType.WARNING.toString(),
                new JPanel(new BorderLayout()) {{
                    setBorder(new EmptyBorder(10, 5, 15, 5));

                    JLabel infoLabel = new JLabel() {{
                        setBorder(new EmptyBorder(0, 5, 5, 5));
                        setIconTextGap(10);
                        setIcon(MessageType.WARNING.getIcon());
                        setText(Language.get(JobPluginHandler.class, "warn@used"));
                    }};
                    add(infoLabel, BorderLayout.NORTH);

                    add(new JPanel() {{
                        setBorder(new EmptyBorder(
                                0, infoLabel.getIcon().getIconWidth()+infoLabel.getIconTextGap()+infoLabel.getInsets().left,
                                0, 5
                        ));
                        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                            HTMLView view = new HTMLView() {
                                {
                                    setText("<html>"+ Entity.entitiesTable(references, true)+"</html>");
                                    setFont(IEditor.FONT_VALUE.deriveFont((float) (IEditor.FONT_VALUE.getSize()*0.9)));
                                }
                                @Override
                                public Dimension getPreferredScrollableViewportSize() {
                                    if (references.size() > 4) {
                                        Dimension defSize = super.getPreferredScrollableViewportSize();
                                        int margin = getInsets().top + getInsets().bottom + 2;
                                        int itemHeight = (defSize.height - margin) / references.size();
                                        return new Dimension(defSize.width, itemHeight * 4 + margin);
                                    } else {
                                        return super.getPreferredScrollableViewportSize();
                                    }
                                }
                            };

                            add(new JScrollPane(view) {{
                                SwingUtilities.invokeLater(() -> getViewport().setViewPosition(new Point(0, 0)));
                                setBorder(new TitledBorder(
                                        new LineBorder(Color.LIGHT_GRAY, 1),
                                        Language.get(Entity.class, "ref@child")
                                ));
                            }});
                    }}, BorderLayout.CENTER);
                }},
                null,
                Dialog.Default.BTN_CLOSE
        ){
            @Override
            public Dimension getPreferredSize() {
                Dimension defSize = super.getPreferredSize();
                return new Dimension(Math.max(defSize.width, 400), defSize.height);
            }
        }.setVisible(true);
    }
}
