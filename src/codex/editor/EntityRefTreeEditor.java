package codex.editor;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.render.GeneralRenderer;
import codex.explorer.ExplorerAccessService;
import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.model.Catalog;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
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
import java.util.function.Predicate;
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
public class EntityRefTreeEditor extends AbstractEditor {
    
    private final static ExplorerAccessService EAS = (ExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    
    private DefaultListModel listModel;
    protected JTextField     textField;
    private final JLabel     signDelete;

    public EntityRefTreeEditor(PropertyHolder propHolder) {
        super(propHolder);
        
        signDelete = new JLabel(ImageUtils.resize(
                ImageUtils.getByPath("/images/clearval.png"), 
                textField.getPreferredSize().height-2, textField.getPreferredSize().height-2
        ));
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
        
        listModel = new DefaultListModel();
        listModel.addElement(new NullValue());
        JList list = new JList<>(listModel);
        list.setCellRenderer(new GeneralRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasFocus) {
                if (!(value instanceof NullValue)) {
                    Entity owner = ((Entity) value).getOwner();
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

        Box container = new Box(BoxLayout.X_AXIS);
        container.add(textField);
        return container;
    }

    @Override
    public void setValue(Object value) {
        if (listModel != null) {
            listModel.removeAllElements();
            if (value == null) {
                listModel.addElement(new NullValue());
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
        Class             entityClass  = ((EntityRef) propHolder.getPropValue()).getEntityClass();
        Predicate<Entity> entityFilter = ((EntityRef) propHolder.getPropValue()).getEntityFilter();
        return entityClass != null ? EAS.getEntitiesByClass(entityClass)
                    .stream()
                    .filter(entityFilter)
                    .collect(Collectors.toList())
            : new LinkedList<>();
    }
    
    private class EntitySelector extends EditorCommand {

        private EntitySelector() {
            super(ImageUtils.resize(ImageUtils.getByPath("/images/hierachy.png"), 18, 18), Language.get("title"));
        }

        @Override
        public void execute(PropertyHolder context) {
            
            JPanel content = new JPanel(new BorderLayout());
            
            Entity rootEAS = EAS.getRoot();
            Predicate<Entity> entityFilter = ((EntityRef) propHolder.getPropValue()).getEntityFilter();
            
            NodeTreeModel treeModel = new NodeTreeModel(new EntityProxy(rootEAS));
            Supplier<Stream<INode>> treeStream = () -> {
                return ((EntityProxy) treeModel.getRoot()).flattened();
            };

            getValues().forEach((entity) -> {
                List<Entity> path = entity.getPath().stream().map((node) -> {
                    return (Entity) node;
                }).collect(Collectors.toList());
                
                path.forEach((entityEAS) -> {
                    if (entityEAS == rootEAS) {
                    } else {
                        boolean existInTree = treeStream.get().anyMatch((node) -> {
                            return ((EntityProxy) node).getEntity() == entityEAS;
                        });
                        if (!existInTree) {
                            EntityProxy parentProxy = (EntityProxy) treeStream.get().filter((node) -> {
                                return ((EntityProxy) node).getEntity() == entityEAS.getParent();
                            }).findFirst().get();

                            EntityProxy nodeProxy = new EntityProxy(entityEAS);
                            parentProxy.insert(nodeProxy);
                        }
                    }
                });
            });
            
            JTree navigator = new JTree(treeModel) {{
                setRowHeight((int) (getRowHeight()*1.5));
                setBorder(new EmptyBorder(5, 10, 5, 5));
                getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                setCellRenderer(new GeneralRenderer());
            }};
            
            treeStream.get().forEach((nodeProxy) -> {
                if (!entityFilter.test(((EntityProxy) nodeProxy).getEntity())) {
                    navigator.expandPath(new TreePath(treeModel.getPathToRoot(nodeProxy)));
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
        public Class getChildClass() {
            return null;
        }
        
        public Entity getEntity() {
            return entity;
        }
        
    }
    
}
