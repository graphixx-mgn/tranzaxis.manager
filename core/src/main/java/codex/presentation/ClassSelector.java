package codex.presentation;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.IEditor;
import codex.model.ClassCatalog;
import codex.model.Entity;
import codex.model.EntityDefinition;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
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
                Modifier.isAbstract(aClass.getModifiers()) //&&
                //!aClass.isAnnotationPresent(ClassCatalog.Domain.class)
        );
        if (classCatalog.size() == 0) {
            MessageBox.show(MessageType.WARNING, Language.get(ClassSelector.class, "empty"));
            return null;
        } else if (classCatalog.size() > 1 || ClassCatalog.class.isAssignableFrom(classCatalog.get(0))) {
            return new ClassSelector(buildTree(classCatalog)).select();
        } else {
            return classCatalog.get(0);
        }
    }

    @SuppressWarnings("unchecked")
    private static DefaultTreeModel buildTree(List<Class<? extends Entity>> classCatalog) {
        classCatalog.sort((class1, class2) -> {
            if (class1.isAssignableFrom(class2)) {
                return -1;
            } else if (class2.isAssignableFrom(class1)) {
                return 1;
            } return 0;
        });

        final Node root = new DirNode(null);
        final DefaultTreeModel treeModel = new DefaultTreeModel(root);
        classCatalog.forEach(aClass -> {
            Node currentNode = root;
            Class<? extends ClassCatalog> ctlClass = (Class<? extends ClassCatalog>) aClass;
            if (ctlClass.isAnnotationPresent(ClassCatalog.Domains.class)) {
                final Class<? extends ClassCatalog.IDomain>[] domains = ctlClass.getAnnotation(ClassCatalog.Domains.class).value();

                for (Class<? extends ClassCatalog.IDomain> domain : domains) {
                    String domainName = Language.get(domain, "title");
                    Node nextNode = Collections.list((Enumeration<Node>) currentNode.children()).stream()
                            .filter(childNode -> domainName.equals(childNode.getUserObject()))
                            .findFirst()
                            .orElse(new DirNode(domainName));
                    if (nextNode.getParent() == null) {
                        currentNode.add(nextNode);
                    }
                    currentNode = nextNode;
                }
            }
            currentNode.add(new ClassNode(aClass.asSubclass(ClassCatalog.class)));
        });
        return treeModel;
    }

    private ClassSelector(DefaultTreeModel classTreeModel) {
        JTree classTree = new JTree(classTreeModel);
        classTree.setRowHeight(SIZE+2);
        classTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        classTree.setBorder(new EmptyBorder(-15, 5, 5, 5));

        Node root = (Node) classTreeModel.getRoot();
        classTree.expandPath(new TreePath(((Node) root.getFirstChild()).getPath()));

        DialogButton acceptBtn = Dialog.Default.BTN_OK.newInstance();
        acceptBtn.setEnabled(false);

        classTree.setCellRenderer((tree, value, selected, expanded, leaf, row, hasFocus) -> {
            JLabel label = new JLabel();
            label.setOpaque(true);
            label.setIconTextGap(6);
            label.setBorder(new EmptyBorder(15, 2, 15, 7));
            label.setVerticalAlignment(SwingConstants.CENTER);

            Node node = (Node) value;
            label.setIcon(node.getIcon());
            label.setText(node.toString());

            label.setBackground(selected && isNodeSelectable(node) ?
                    UIManager.getDefaults().getColor("Tree.selectionBackground") :
                    UIManager.getDefaults().getColor("Tree.background")
            );
            label.setForeground(selected && isNodeSelectable(node) ? Color.WHITE : IEditor.COLOR_NORMAL);
            return label;
        });

        classTree.addTreeSelectionListener((TreeSelectionEvent event) -> {
            boolean selectable = isNodeSelectable((Node) classTree.getLastSelectedPathComponent());
            acceptBtn.setEnabled(selectable);
        });

        classTree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && isNodeSelectable((Node) classTree.getLastSelectedPathComponent())) {
                    selector.setVisible(false);
                }
            }
        });

        classSupplier = () -> {
            ClassNode node = (ClassNode) classTree.getLastSelectedPathComponent();
            if (node != null) {
                return node.catalogClass.asSubclass(Entity.class);
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

    private boolean isNodeSelectable(Node node) {
        return node instanceof ClassNode;
    }

    private Class<? extends Entity> select() {
        selector.setVisible(true);
        return classSupplier.get();
    }

    private static abstract class Node extends DefaultMutableTreeNode implements Iconified {}

    private static class DirNode extends Node {

        private final String title;

        DirNode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }

        @Override
        public ImageIcon getIcon() {
            return title == null ? new ImageIcon() : ICON_FOLDER;
        }
    }

    private static class ClassNode extends Node {

        private final Class<? extends ClassCatalog> catalogClass;
        private final ImageIcon icon;
        private final String    title;

        ClassNode(Class<? extends ClassCatalog> catalogClass) {
            this.catalogClass = catalogClass;

            final EntityDefinition entityDef = catalogClass.getAnnotation(EntityDefinition.class);
            icon = entityDef != null && !entityDef.icon().isEmpty() ?
                    ImageUtils.resize(ImageUtils.getByPath(
                            catalogClass,
                            entityDef.icon()
                    ), SIZE, SIZE) :
                    ICON_UNKNOWN;

            if (entityDef != null && !entityDef.title().isEmpty()) {
                title = Language.get(catalogClass, entityDef.title());
            } else {
                title = MessageFormat.format("<{0}>", catalogClass.getTypeName());
            }
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
