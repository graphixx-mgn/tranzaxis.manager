package codex.presentation;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.IEditor;
import codex.model.ClassCatalog;
import codex.model.Entity;
import codex.model.EntityDefinition;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.List;
import java.util.Stack;
import java.util.function.Supplier;

class ClassSelector {

    private final static int       SIZE = (int) (IEditor.FONT_VALUE.getSize() * 1.6);
    private final static ImageIcon ICON_UNKNOWN = ImageUtils.resize(ImageUtils.getByPath("/images/question.png"), SIZE, SIZE);
    private final static ImageIcon ICON_FOLDER  = ImageUtils.resize(ImageUtils.getByPath("/images/folder.png"), SIZE, SIZE);
    private final static ImageIcon ICON_CATALOG = ImageUtils.getByPath("/images/catalog.png");

    private Dialog selector;
    private Supplier<Class<? extends Entity>> classSupplier;

    public static Class<? extends Entity> select(Entity entity) {
        List<Class<? extends Entity>> classCatalog = ClassCatalog.getClassCatalog(entity);
        classCatalog.removeIf(aClass ->
                Modifier.isAbstract(aClass.getModifiers()) &&
                !aClass.isAnnotationPresent(ClassCatalog.Domain.class)
        );
        if (classCatalog.size() == 0) {
            MessageBox.show(MessageType.WARNING, Language.get(ClassSelector.class, "empty"));
            return null;
        } else if (classCatalog.size() > 1 || ClassCatalog.class.isAssignableFrom(classCatalog.get(0))) {
            return new ClassSelector(buildTree(classCatalog)).select();
        } else {
            return classCatalog.get(0);
        }
//        } else if (classCatalog.size() == 1) {
//            return classCatalog.get(0);
//        } else {
//            return new ClassSelector(buildTree(classCatalog)).select();
//        }
    }

    private static DefaultTreeModel buildTree(List<Class<? extends Entity>> classCatalog) {
        classCatalog.sort((class1, class2) -> {
            if (class1.isAssignableFrom(class2)) {
                return -1;
            } else if (class2.isAssignableFrom(class1)) {
                return 1;
            } return 0;
        });

        final Stack<DefaultMutableTreeNode> stack = new Stack<>();
        stack.push(new DefaultMutableTreeNode(Object.class));
        final DefaultTreeModel treeModel = new DefaultTreeModel(stack.peek());

        classCatalog.forEach(aClass -> {
            if (((Class<?>) stack.peek().getUserObject()).isAssignableFrom(aClass)) {
                DefaultMutableTreeNode parent = stack.peek();
                stack.push(new DefaultMutableTreeNode(aClass));
                parent.add(stack.peek());
            } else {
                DefaultMutableTreeNode parent;
                do {
                    stack.pop();
                    parent = stack.peek();
                } while (!((Class<?>) parent.getUserObject()).isAssignableFrom(aClass));
                stack.push(new DefaultMutableTreeNode(aClass));
                parent.add(stack.peek());
            }
        });
        return treeModel;
    }

    private ClassSelector(DefaultTreeModel classTreeModel) {
        JTree classTree = new JTree(classTreeModel);
        classTree.setRowHeight(SIZE+2);
        classTree.setUI(new BasicTreeUI() {
            @Override
            protected void paintVerticalLine(Graphics g, JComponent c, int x, int top, int bottom) {}

            @Override
            protected void paintHorizontalLine(Graphics g, JComponent c, int y, int left, int right) {}
        });
        classTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        classTree.setBorder(new EmptyBorder(-15, 5, 5, 5));

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) classTreeModel.getRoot();
        classTree.expandPath(new TreePath(((DefaultMutableTreeNode) root.getFirstChild()).getPath()));

        DialogButton acceptBtn = Dialog.Default.BTN_OK.newInstance();
        acceptBtn.setEnabled(false);

        classTree.addTreeSelectionListener((TreeSelectionEvent event) -> {
            acceptBtn.setEnabled(isNodeSelectable((DefaultMutableTreeNode) classTree.getLastSelectedPathComponent()));
        });

        classTree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && isNodeSelectable((DefaultMutableTreeNode) classTree.getLastSelectedPathComponent())) {
                    selector.setVisible(false);
                }
            }
        });

        classTree.setCellRenderer((tree, value, selected, expanded, leaf, row, hasFocus) -> {
            JLabel label = new JLabel();
            label.setOpaque(true);
            label.setIconTextGap(6);
            label.setForeground(selected ? Color.WHITE : IEditor.COLOR_NORMAL);
            label.setBorder(new EmptyBorder(15, 2, 15, 7));
            label.setVerticalAlignment(SwingConstants.CENTER);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Class<?> entityClass = (Class<?>) node.getUserObject();
            EntityDefinition entityDef = entityClass.getAnnotation(EntityDefinition.class);

            if (value.equals(tree.getModel().getRoot())) {
                //
            } else if (entityClass.isAnnotationPresent(ClassCatalog.Domain.class)) {
                label.setIcon(ICON_FOLDER);
                label.setText(Language.get(entityClass, "domain@name"));
            } else {
                label.setIcon(entityDef != null && !entityDef.icon().isEmpty() ?
                            ImageUtils.resize(ImageUtils.getByPath(entityDef.icon()), SIZE, SIZE) :
                            ICON_UNKNOWN
                );
                if (entityDef != null && !entityDef.title().isEmpty()) {
                    label.setText(Language.get(entityClass, entityDef.title()));
                } else {
                    label.setText(MessageFormat.format("<{0}>", entityClass.getTypeName()));
                    label.setForeground(IEditor.COLOR_INVALID);
                }
            }
            label.setBackground(selected ?
                    UIManager.getDefaults().getColor("Tree.selectionBackground") :
                    UIManager.getDefaults().getColor("Tree.background")
            );
            return label;
        });

        classSupplier = () -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) classTree.getLastSelectedPathComponent();
            if (node != null) {
                Class<?> entityClass = (Class<?>) node.getUserObject();
                return entityClass.asSubclass(Entity.class);
            }
            return null;
        };

        selector = new Dialog(
            Dialog.findNearestWindow(),
            ICON_CATALOG,
            Language.get("title"),
            new JPanel(new BorderLayout()) {{
                setBorder(new EmptyBorder(5, 5, 5, 5));
                add(new JScrollPane(classTree), BorderLayout.CENTER);
                setPreferredSize(new Dimension(
                        Math.max(getPreferredSize().width + 50, 350),
                        250
                ));
            }},
            event -> {
                if (event.getID() != Dialog.OK) {
                    classTree.clearSelection();
                }
            },
            acceptBtn,
            Dialog.Default.BTN_CANCEL.newInstance()
        ) {
            @Override
            public void setVisible(boolean visible) {
                if (visible) classTree.clearSelection();
                super.setVisible(visible);
            }
        };
    }

    private boolean isNodeSelectable(DefaultMutableTreeNode node) {
        return node != null && !((Class<?>) node.getUserObject()).isAnnotationPresent(ClassCatalog.Domain.class);
    }

    private Class<? extends Entity> select() {
        selector.setVisible(true);
        return classSupplier.get();
    }
}
