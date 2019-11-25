package codex.editor;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.render.GeneralRenderer;
import codex.explorer.IExplorerAccessService;
import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.model.Catalog;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.NullValue;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.FocusManager;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Древовидный селектор сущностей.
 */
public class EntityRefTreeEditor extends AbstractEditor<EntityRef<Entity>, Entity> {
    
    private DefaultListModel<Entity> listModel;
    protected JTextField textField;
    private final JLabel signDelete;

    public EntityRefTreeEditor(PropertyHolder<EntityRef<Entity>, Entity> propHolder) {
        super(propHolder);
        
        signDelete = new JLabel(ImageUtils.resize(
                ImageUtils.getByPath("/images/clearval.png"), 
                textField.getPreferredSize().height-2, textField.getPreferredSize().height-2
        ));

        EntitySelector selector = new EntitySelector();
        addCommand(selector);

        textField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    selector.execute(propHolder);
                }
            }
        });

        textField.add(signDelete, BorderLayout.EAST);
        signDelete.setCursor(Cursor.getDefaultCursor());
        signDelete.setVisible(!propHolder.isEmpty() && isEditable() && textField.isFocusOwner());
        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                propHolder.setValue(null);
            }
        });
    }

    @Override
    public Box createEditor() {
        textField = new JTextField();
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.setEditable(false);
        textField.addFocusListener(this);
        textField.setBackground(Color.WHITE);
        textField.setLayout(new BorderLayout());
        
        listModel = new DefaultListModel<>();
        listModel.addElement(new Undefined());
        JList<Entity> list = new JList<>(listModel);
        list.setCellRenderer(new GeneralRenderer<Entity>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends Entity> list, Entity value, int index, boolean isSelected, boolean hasFocus) {
                if (NullValue.class.isAssignableFrom(value.getClass())) {
                    Entity owner = value.getOwner();
                    if (owner != null) {
                        JPanel compPanel = new JPanel();
                        compPanel.setLayout(new BoxLayout(compPanel, BoxLayout.X_AXIS));

                        JLabel ownerLabel = (JLabel) super.getListCellRendererComponent(list, owner, index, isSelected, hasFocus);
                        ownerLabel.setText(ownerLabel.getText()+" -");

                        compPanel.add(ownerLabel);
                        compPanel.add(super.getListCellRendererComponent(list, value, index, isSelected, hasFocus));
                        return compPanel;
                    }
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
            }
        });
        list.setEnabled(false);
        list.setBorder(new EmptyBorder(2, 0, 0, 0));
        textField.add(list, BorderLayout.WEST);

        Box container = new Box(BoxLayout.X_AXIS);
        container.add(textField);
        return container;
    }

    @Override
    public void setValue(Entity value) {
        if (listModel != null) {
            listModel.removeAllElements();
            if (value == null) {
                listModel.addElement(new Undefined());
            } else {
                listModel.addElement(value);
            }
        }
        if (signDelete!= null) {
            signDelete.setVisible(!propHolder.isEmpty() && isEditable() && textField.isFocusOwner());
        }
    }
    
    @Override
    public void focusGained(FocusEvent event) {
        super.focusGained(event);
        if (signDelete!= null) {
            signDelete.setVisible(!propHolder.isEmpty() && isEditable() && textField.isFocusOwner());
        }
    }
    
    @Override
    public void focusLost(FocusEvent event) {
        super.focusLost(event);
        if (signDelete!= null) {
            signDelete.setVisible(false);
        }
    }
    
    private List<Entity> getValues() {
        EntityRef<Entity> ref = propHolder.getPropValue();
        Class<? extends Entity> entityClass = ref.getEntityClass();
        IExplorerAccessService EAS = ServiceRegistry.getInstance().lookupService(IExplorerAccessService.class);

        return entityClass != null ? EAS.getEntitiesByClass(entityClass)
                .stream()
                .filter(entity -> ref.getMask().verify(entity))
                .collect(Collectors.toList())
                : new LinkedList<>();
    }
    
    private class EntitySelector extends EditorCommand<EntityRef<Entity>, Entity> {

        private EntitySelector() {
            super(ImageUtils.resize(ImageUtils.getByPath("/images/hierachy.png"), 18, 18), Language.get("title"));
        }

        @Override
        public void execute(PropertyHolder context) {
            JPanel content = new JPanel(new BorderLayout());
            IExplorerAccessService EAS = ServiceRegistry.getInstance().lookupService(IExplorerAccessService.class);
            Entity rootEAS = EAS.getRoot();

            NodeTreeModel treeModel = new NodeTreeModel(new EntityProxy(rootEAS));
            Supplier<Stream<INode>> treeStream = () -> ((EntityProxy) treeModel.getRoot()).flattened();

            getValues().forEach((entity) -> {
                List<Entity> path = entity.getPath().stream()
                        .map((node) -> (Entity) node)
                        .collect(Collectors.toList());

                path.forEach((entityEAS) -> {
                    if (entityEAS == rootEAS) {
                        // Do nothing
                    } else {
                        boolean existInTree = treeStream.get().anyMatch((node) -> ((EntityProxy) node).getEntity() == entityEAS);
                        if (!existInTree) {
                            treeStream.get()
                                    .filter((node) -> ((EntityProxy) node).getEntity() == entityEAS.getParent())
                                    .findFirst()
                                    .ifPresent(parentNode -> parentNode.attach(new EntityProxy(entityEAS)));

                        }
                    }
                });
            });

            JTree navigator = new JTree(treeModel) {{
                setRowHeight((int) (getRowHeight() * 1.5));
                setBorder(new EmptyBorder(5, 10, 5, 5));
                getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                setCellRenderer(new GeneralRenderer());
            }};

            treeStream.get().forEach((nodeProxy) -> {
                navigator.expandPath(new TreePath(treeModel.getPathToRoot(nodeProxy)));
                if (!propHolder.getPropValue().getMask().verify(((EntityProxy) nodeProxy).getEntity())) {
                    nodeProxy.setMode(INode.MODE_NONE);
                }
                if (((EntityProxy) nodeProxy).getEntity() == propHolder.getPropValue().getValue()) {
                    navigator.setSelectionPath(new TreePath(treeModel.getPathToRoot(nodeProxy)));
                }
            });

            JScrollPane scrollPane = new JScrollPane(navigator);
            scrollPane.setBorder(new MatteBorder(1, 0, 1, 0, Color.LIGHT_GRAY));
            content.add(scrollPane, BorderLayout.CENTER);
            scrollPane.setPreferredSize(new Dimension(navigator.getPreferredSize().width + 50, navigator.getPreferredSize().height + 50));

            DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
            confirmBtn.setEnabled(false);

            ActionListener dialogHandler = (event) -> {
                if (event.getID() == Dialog.OK) {
                    propHolder.setValue(((EntityProxy) navigator.getLastSelectedPathComponent()).getEntity());
                }
            };
            Dialog selector = new Dialog(
                    FocusManager.getCurrentManager().getActiveWindow(),
                    ImageUtils.getByPath("/images/hierachy.png"),
                    Language.get("title"),
                    content,
                    dialogHandler,
                    confirmBtn
            );

            navigator.addTreeSelectionListener((TreeSelectionEvent event) -> {
                SwingUtilities.invokeLater(() -> {
                    final INode nodeProxy = (INode) navigator.getLastSelectedPathComponent();
                    if (nodeProxy == null) return;

                    if ((nodeProxy.getMode() & INode.MODE_SELECTABLE) != INode.MODE_SELECTABLE) {
                        navigator.clearSelection();
                        navigator.getSelectionModel().setSelectionPath(event.getOldLeadSelectionPath());
                    }
                    confirmBtn.setEnabled((nodeProxy.getMode() & INode.MODE_SELECTABLE) == INode.MODE_SELECTABLE);
                });
            });
            navigator.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        final INode nodeProxy = (INode) navigator.getLastSelectedPathComponent();
                        if (nodeProxy == null) return;

                        if ((nodeProxy.getMode() & INode.MODE_SELECTABLE) == INode.MODE_SELECTABLE) {
                            dialogHandler.actionPerformed(new ActionEvent(selector, confirmBtn.getID(), null));
                            selector.dispose();
                        }
                    }
                }
            });

            selector.pack();
            selector.setVisible(true);
        }
    }
    
    private class EntityProxy extends Catalog {
        
        private final Entity entity;
        
        EntityProxy(Entity entity) {
            super(null, entity.getIcon(), entity.toString(), null);
            this.entity = entity;
        }

        @Override
        public Class<? extends Entity> getChildClass() {
            return null;
        }
        
        public Entity getEntity() {
            return entity;
        }
        
    }
    
}
