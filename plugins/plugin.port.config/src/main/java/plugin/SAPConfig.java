package plugin;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.editor.IEditor;
import codex.explorer.tree.NodeTreeModel;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.presentation.ISelectorTableModel;
import codex.presentation.SelectorTreeTable;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import editor.ObjectChooser;
import manager.nodes.Environment;
import plugin.command.CommandPlugin;
import type.NetInterface;
import units.InstanceControlService;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

public class SAPConfig extends CommandPlugin<Environment> {

    private final static String PROP_SETHOST = "host";

    public SAPConfig() {
        super(environment ->
                environment.getDataBase(true) != null &&
                environment.getDataBase(true).getConnectionID(false) != null &&
                environment.getInstanceId() != null
        );
    }

    @Override
    public void execute(Environment context, Map<String, IComplexType> params) {
        InstanceView instanceView = getInstance(context);
        NodeTreeModel treeModel = new NodeTreeModel(instanceView.getChildAt(0));

        DialogButton btnSubmit = Dialog.Default.BTN_OK.newInstance(Language.get(SAPConfig.class, "commit@title"));
        DialogButton btnCancel = Dialog.Default.BTN_CLOSE.newInstance();

        btnSubmit.setEnabled(false);
        StreamSupport.stream(treeModel.spliterator(), false).forEach(node -> {
            Entity entity = (Entity) node;
            entity.model.addModelListener(new IModelListener() {
                @Override
                public void modelChanged(EntityModel model, List<String> changes) {
                    btnSubmit.setEnabled(
                            StreamSupport.stream(treeModel.spliterator(), false).anyMatch(node -> ((Entity) node).model.hasChanges())
                    );
                }
            });
        });

        Dialog dialog = new Dialog(
                Dialog.findNearestWindow(),
                getIcon(),
                toString(),
                createView(instanceView),
                (event) -> {
                    if (event.getID() == Dialog.OK) {
                        commit();
                    }

                },
                btnSubmit,
                btnCancel
        );
        dialog.setPreferredSize(new Dimension(700, dialog.getPreferredSize().height));
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    private JPanel createView(InstanceView instanceView) {
        InstanceControlService instanceControl = (InstanceControlService) instanceView.getChildAt(0);
        SelectorTreeTable<AccessPoint> selectorTreeTable = new SelectorTreeTable<>(instanceControl, AccessPoint.class);
        selectorTreeTable.setPropertyEditable(AccessPoint.PROP_ADDR);

        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setViewportView(selectorTreeTable);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(0, 5, 5, 5),
                new MatteBorder(1, 1, 1, 1, Color.GRAY)
        ));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);

        ParamModel model = new ParamModel();
        ObjectChooser chooser = new ObjectChooser(PROP_SETHOST) {
            @Override
            public List getValues() {
                return NetInterface.getInterfaces();
            }
        };
        model.addProperty(chooser.getProperty());
        panel.add(new EditorPage(model), BorderLayout.NORTH);

        // Editor settings
        final IEditor setHost = model.getEditor(PROP_SETHOST);
        setHost.addCommand(new SetHost() {
            @Override
            public void execute(PropertyHolder context) {
                final ISelectorTableModel model = (ISelectorTableModel) selectorTreeTable.getModel();
                final NetInterface        iface = (NetInterface) context.getOwnPropValue().getValue();

                Arrays.stream(selectorTreeTable.getSelectedRows()).forEach(row -> {
                    AccessPoint accessPoint = (AccessPoint) model.getEntityForRow(row);

                    String address = changeHost(accessPoint.getAddress(true), iface.toString());
                    accessPoint.setAddress(address);
                    selectorTreeTable.revalidate();
                    selectorTreeTable.repaint();
                });
            }
        });
        setHost.setEditable(selectorTreeTable.getSelectedRowCount() > 0);

        // Handlers
        selectorTreeTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                setHost.setEditable(selectorTreeTable.getSelectedRowCount() > 0);
            }
        });

        return panel;
    }

    private InstanceView getInstance(Environment context) {
        InstanceView instanceView = Entity.newInstance(InstanceView.class, context.toRef(), context.getInstanceId().toString());
        instanceView.loadChildren();
        return instanceView;
    }

    private void commit() {
        getContext().forEach(environment -> {
            InstanceView instanceView = Entity.newInstance(InstanceView.class, environment.toRef(), environment.getInstanceId().toString());
            NodeTreeModel treeModel = new NodeTreeModel(instanceView.getChildAt(0));
            StreamSupport.stream(treeModel.spliterator(), false)
                    .map(iNode -> (AccessPoint) iNode)
                    .filter(accessPoint -> accessPoint.model.hasChanges())
                    .peek(accessPoint -> Logger.getLogger().info(
                            "Update address for access point ''{0}/{1}'': {2} => {3}",
                            environment,
                            accessPoint,
                            accessPoint.getAddress(false),
                            accessPoint.getAddress(true)
                    ))
                    .forEach(accessPoint -> {
                        accessPoint.saveSettings();
                        // Remove object to drop from cache
                        accessPoint.model.remove();
                    });
        });
    }

    abstract class SetHost extends EditorCommand<AnyType, Object> {

        SetHost() {
            super(
                    ImageUtils.resize(ImageUtils.getByPath("/images/submit.png"), 20, 20),
                    Language.get(SAPConfig.class, "host@apply"),
                    propertyHolder -> propertyHolder.getOwnPropValue().getValue() != null
            );
        }

        @Override
        public abstract void execute(PropertyHolder<AnyType, Object> context);

        String changeHost(String address, String host) {
            return address.contains(":") ? address.replaceAll(".*(:.*)", MessageFormat.format("{0}$1", host)) : address;
        }
    }

}