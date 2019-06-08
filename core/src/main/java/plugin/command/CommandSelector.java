package plugin.command;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.render.GeneralRenderer;
import codex.model.Entity;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import java.util.function.Supplier;

final class CommandSelector {

    private final DialogButton acceptBtn = Dialog.Default.BTN_OK.newInstance();
    private final Dialog       selector;
    private Supplier<EntityCommand<? extends Entity>> command;

    CommandSelector(DefaultListModel<EntityCommand<? extends Entity>> commandListModel) {

        JList<EntityCommand<? extends Entity>> list = new JList<>(commandListModel);
        list.setBorder(new EmptyBorder(5, 10, 5, 10));

        Function<EntityCommand, Boolean> isCommandEnabled =
                command -> command.isActive() && (command.multiContextAllowed() || command.getContext().size() == 1);

        list.setCellRenderer(new GeneralRenderer<EntityCommand<? extends Entity>>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends EntityCommand<? extends Entity>> list, EntityCommand<? extends Entity> command, int index, boolean isSelected, boolean hasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, command, index, isSelected, hasFocus);
                if (!isCommandEnabled.apply(command)) {
                    label.setEnabled(false);
                }
                return label;
            }
        });

        list.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(final int index0, final int index1) {
                EntityCommand command = list.getModel().getElementAt(index0);
                if (!isCommandEnabled.apply(command)) {
                    if (!list.isSelectionEmpty()) {
                        list.setSelectedIndex(list.getSelectedIndex());
                    }
                } else {
                    super.setSelectionInterval(index0, index1);
                }
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

        command  = list::getSelectedValue;
        selector = new Dialog(
                null, CommandPlugin.COMMAND_ICON,
                Language.get(CommandPlugin.class, "launcher@selector"),
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

    EntityCommand<? extends Entity> select() {
        selector.setVisible(true);
        return command.get();
    }
}
