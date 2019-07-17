package codex.config;

import codex.command.EditorCommand;
import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.component.render.GeneralRenderer;
import codex.launcher.Shortcut;
import codex.launcher.ShortcutSection;
import codex.log.Logger;
import codex.mask.FileMask;
import codex.model.*;
import codex.presentation.ISelectorTableModel;
import codex.presentation.SelectorTreeTable;
import codex.presentation.SelectorTreeTableModel;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import org.apache.xmlbeans.XmlOptions;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ExportObjects extends EntityCommand<ConfigServiceOptions> {

    private final static IConfigStoreService CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static XmlOptions SAVE_OPTIONS = new XmlOptions() {{
        setSavePrettyPrint();
        setSavePrettyPrintIndent(4);
    }};

    private final static ImageIcon IMAGE_EDIT = ImageUtils.resize(ImageUtils.getByPath("/images/filter.png"), 18, 18);
    private final static String    PARAM_FILE = "file";

    private final EntityFilter objectSelector;

    public ExportObjects() {
        super(
                "export config",
                Language.get("title"),
                ImageUtils.getByPath("/images/export.png"),
                Language.get("title"),
                null
        );
        PropertyHolder filePath = new PropertyHolder<>(PARAM_FILE, new FilePath(null).setMask(
                new FileMask(new FileNameExtensionFilter("XML file", "xml"))
        ), true);
        filePath.addChangeListener((name, oldValue, newValue) -> {
            if (newValue != null) {
                Path path = (Path) newValue;
                if (!path.getFileName().toString().endsWith(".xml")) {
                    filePath.setValue(path.resolveSibling(path.getFileName() + ".xml"));
                }
            }
        });
        setParameters(filePath);

        Exporter exporter = new Exporter() {
            @Override
            protected Predicate<Class<? extends Entity>> getClassFilter() {
                return entityClass -> !(
                       // Todo: подумать над фильтрацией по allowModifyChild()
                       entityClass.equals(ShortcutSection.class) ||
                       entityClass.equals(Shortcut.class)
                );
            }
        };
        CAS.exportConfiguration(exporter);
        objectSelector = new EntityFilter(exporter.getEntities());
    }

    @Override
    protected void preprocessParameters(ParamModel paramModel) {
        paramModel.getEditor(PARAM_FILE).addCommand(objectSelector);
    }

    @Override
    public void execute(ConfigServiceOptions context, java.util.Map<String, IComplexType> params) {
        Path filePath = ((FilePath) params.get(PARAM_FILE)).getValue();
        Exporter exporter = new Exporter() {
            @Override
            protected Predicate<Entity> getEntityFilter() {
                return objectSelector;
            }
        };
        CAS.exportConfiguration(exporter);

        try {
            exporter.getConfiguration().save(filePath.toFile(), SAVE_OPTIONS);
            long count = exporter.getEntities().values().stream()
                    .mapToLong(Collection::size).sum();
            MessageBox.show(
                    MessageType.INFORMATION,
                    Language.get("result@title"),
                    MessageFormat.format(Language.get("result@desc"), count)
            );
        } catch (IOException e) {
            Logger.getLogger().warn("Unable to save configuration objects to file", e);
            MessageBox.show(MessageType.WARNING, e.getMessage());
        }
    }


    class EntityFilter extends EditorCommand<FilePath, Path> implements Predicate<Entity> {

        private final java.util.Map<Class<? extends Entity>, List<? extends Entity>> entities;
        private final java.util.Map<Entity, Boolean> filteredEntities;

        EntityFilter(
                java.util.Map<Class<? extends Entity>, List<? extends Entity>> entities
        ) {
            super(IMAGE_EDIT, Language.get(ExportObjects.class, "choose.title"));
            this.entities = entities;
            filteredEntities = new HashMap<>(
                    entities.values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toMap(
                                entity -> entity,
                                entity -> true
                        ))
            );
        }

        @Override
        public void execute(PropertyHolder<FilePath, Path> context) {
            EntityProxy root = new EntityProxy(null, null);
            this.entities.forEach((entityClass, childEntities) -> {
                Entity classCatalog = (Entity) childEntities.get(0).getParent();
                EntityProxy catalog = new EntityProxy(
                        classCatalog.toRef(),
                        classCatalog.getPID()
                );
                root.insert(catalog);

                childEntities.forEach(child -> {
                    EntityProxy childProxy = new EntityProxy(child.toRef(), child.getPID())
                            .setExport(test(child));
                    catalog.insert(childProxy);
                    childProxy.model.getProperty(EntityProxy.PROP_EXPORT).addChangeListener((name, oldValue, newValue) ->
                            filteredEntities.replace(child, Boolean.TRUE.equals(newValue)));
                });
            });

            Dialog dialog = new Dialog(
                    FocusManager.getCurrentManager().getActiveWindow(),
                    IMAGE_EDIT,
                    Language.get(ExportObjects.class, "choose.title"),
                    createView(root),
                    null,
                    Dialog.Default.BTN_CLOSE
            );
            dialog.setPreferredSize(new Dimension(400, dialog.getPreferredSize().height));
            dialog.setResizable(false);
            dialog.setVisible(true);
        }

        private JPanel createView(EntityProxy root) {
            SelectorTreeTable<EntityProxy> selectorTreeTable = new SelectorTreeTable<EntityProxy>(root, EntityProxy.class) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return ((ISelectorTableModel) getModel()).getEntityForRow(row).getChildCount() == 0;
                }
            };
            selectorTreeTable.setRootVisible(false);
            selectorTreeTable.setPropertiesEditable(EntityProxy.PROP_EXPORT);

            root.childrenList().forEach(iNode -> selectorTreeTable.expandPath(new TreePath(new Object[]{ root, iNode })));

            TableCellRenderer defRenderer = selectorTreeTable.getDefaultRenderer(Bool.class);
            selectorTreeTable.setDefaultRenderer(Bool.class, new GeneralRenderer<Entity>() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JComponent comp = (JComponent) defRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (column > 0) {
                        SelectorTreeTableModel model = (SelectorTreeTableModel) selectorTreeTable.getModel();
                        if (model.getEntityForRow(row).getChildCount() > 0) {
                            JLabel label = new JLabel();
                            label.setOpaque(true);
                            label.setBackground(comp.getBackground());
                            label.setBorder(comp.getBorder());
                            return label;
                        } else {
                            comp.setEnabled(false);
                        }
                    }
                    return comp;
                }
            });

            final JScrollPane scrollPane = new JScrollPane();
            scrollPane.getViewport().setBackground(Color.WHITE);
            scrollPane.setViewportView(selectorTreeTable);
            scrollPane.setBorder(new CompoundBorder(
                    new EmptyBorder(0, 5, 5, 5),
                    new MatteBorder(1, 1, 1, 1, Color.GRAY)
            ));

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(scrollPane, BorderLayout.CENTER);
            return panel;
        }

        @Override
        public boolean test(Entity entity) {
            return filteredEntities.containsKey(entity) && filteredEntities.get(entity);
        }
    }
}
