package codex.presentation;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.model.Entity;
import codex.model.EntityDefinition;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.List;
import java.util.function.Supplier;

class ClassSelector {

    final static ImageIcon ICON_UNKNOWN = ImageUtils.getByPath("/images/question.png");
    final static ImageIcon ICON_CATALOG = ImageUtils.getByPath("/images/catalog.png");

    private final DialogButton acceptBtn = Dialog.Default.BTN_OK.newInstance();
    private final Dialog       selector;
    private Supplier<Class<? extends Entity>> classSupplier;

    ClassSelector(List<Class<? extends Entity>> classList) {
        this(new DefaultListModel<Class<? extends Entity>>() {{
            classList.forEach(this::addElement);
        }});
    }

    ClassSelector(DefaultListModel<Class<? extends Entity>> classListModel) {
        JList<Class<? extends Entity>> list = new JList<>(classListModel);
        list.setBorder(new EmptyBorder(5, 10, 5, 10));

        list.setCellRenderer(new GeneralRenderer<Class<? extends Entity>>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends Class<? extends Entity>> list, Class<? extends Entity> entityClass, int index, boolean isSelected, boolean hasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, entityClass, index, isSelected, hasFocus);
                EntityDefinition entityDef = Entity.getDefinition(entityClass);
                if (entityDef != null && !entityDef.icon().isEmpty()) {
                    label.setIcon(ImageUtils.resize(ImageUtils.getByPath(entityDef.icon()), 17, 17));
                } else {
                    label.setIcon(ImageUtils.resize(ICON_UNKNOWN, 17, 17));
                }
                if (entityDef != null && !entityDef.title().isEmpty()) {
                    label.setText(Language.get(entityClass, entityDef.title()));
                } else {
                    label.setText(MessageFormat.format("<{0}>", entityClass.getTypeName()));
                    label.setForeground(IEditor.COLOR_INVALID);
                }
                return label;
            }
        });

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                acceptBtn.setEnabled(!list.isSelectionEmpty());
            }
        });
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    selector.setVisible(false);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(new EmptyBorder(5, 5, 5, 5));
        content.add(scroll, BorderLayout.CENTER);
        content.setPreferredSize(new Dimension(
                Math.max(content.getPreferredSize().width+50, 300),
                200
        ));

        classSupplier = list::getSelectedValue;
        selector = new Dialog(
                null,
                ICON_CATALOG,
                Language.get("title"),
                content,
                event -> {
                    if (event.getID() != Dialog.OK) {
                        list.clearSelection();
                    }
                },
                acceptBtn, Dialog.Default.BTN_CANCEL.newInstance()
        ) {
            @Override
            public void setVisible(boolean visible) {
                if (visible) list.clearSelection();
                super.setVisible(visible);
            }
        };
        acceptBtn.setEnabled(!list.isSelectionEmpty());
    }

    final Class<? extends Entity> select() {
        selector.setVisible(true);
        return classSupplier.get();
    }
}
