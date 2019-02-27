package codex.presentation;

import codex.command.EntityCommand;
import codex.command.ICommandListener;
import codex.component.button.PushButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.log.Logger;
import codex.model.Entity;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Панель команд презентаций редактора и селектора сущности.
 */
public final class CommandPanel extends Box {

    private static final ImageIcon ARROW = ImageUtils.getByPath("/images/arrow.png");

    private final JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
    private final Box systemCommandsPanel  = new Box(BoxLayout.X_AXIS);
    private final Box contextCommandsPanel = new Box(BoxLayout.X_AXIS);

    private static String getKeyText(KeyStroke key) {
        if (key.getModifiers() != 0) {
            return KeyEvent.getKeyModifiersText(key.getModifiers()).concat("+").concat(KeyEvent.getKeyText(key.getKeyCode()));
        } else {
            return KeyEvent.getKeyText(key.getKeyCode());
        }
    }
    
    /**
     * Конструктор панели.
     */
    public CommandPanel(List<EntityCommand<Entity>> systemCommands) {
        super(BoxLayout.LINE_AXIS);
        setBorder(new CompoundBorder(
                new EmptyBorder(2, 5, 2, 5),
                new CompoundBorder(
                        new MatteBorder(0, 0, 1, 0, Color.GRAY), 
                        new EmptyBorder(3, 0, 3, 0)
                )
        ));
        separator.setMaximumSize( new Dimension(1, Integer.MAX_VALUE) );
        separator.setVisible(false);

        add(systemCommandsPanel);
        add(separator);
        add(Box.createRigidArea(new Dimension(5, 0)));
        add(contextCommandsPanel);

        setCommands(systemCommandsPanel, systemCommands);
    }

    void setContextCommands(List<EntityCommand<Entity>> commands) {
        setCommands(contextCommandsPanel, commands);
        separator.setVisible(systemCommandsPanel.getComponentCount() > 0 && !commands.isEmpty());
    }

    private void setCommands(Box commandPanel, List<EntityCommand<Entity>> commands) {

        while (commandPanel.getComponentCount() > 0) {
            Component comp = commandPanel.getComponent(0);
            if (comp instanceof EntityCommandButton) {
                ((EntityCommandButton) comp).remove();
            }
            commandPanel.remove(comp);
        }

        for (EntityCommand<Entity> command : commands) {
            if (command.getGroupId() != null) {
                Optional<GroupButton> attachedGroupButton = Arrays.stream(commandPanel.getComponents())
                        .filter(component -> component instanceof GroupButton)
                        .map(component -> (GroupButton) component)
                        .filter(groupButton -> groupButton.groupId.equals(command.getGroupId()))
                        .findFirst();
                if (attachedGroupButton.isPresent()) {
                    attachedGroupButton.get().addGroupItem(command);
                } else {
                    GroupButton button = new GroupButton(command);
                    commandPanel.add(button);
                    commandPanel.add(Box.createRigidArea(new Dimension(5, 0)));
                }
            } else {
                EntityCommandButton button = new EntityCommandButton(command);
                commandPanel.add(button);
                commandPanel.add(Box.createRigidArea(new Dimension(5, 0)));
            }
        }
        commandPanel.revalidate();
        commandPanel.repaint();
    }


    private class EntityCommandButton extends PushButton implements ICommandListener<Entity> {

        protected final Supplier<EntityCommand<Entity>> command;

        EntityCommandButton(EntityCommand<Entity> command) {
            this(command, false);
        }

        EntityCommandButton(EntityCommand<Entity> command, boolean showTitle) {
            super(command.getIcon(), showTitle ? command.getTitle() : null);

            KeyStroke key = command.getKey();
            setHint(command.getHint().concat(key == null ? "" : " ("+getKeyText(key)+")"));
            button.addActionListener(e -> preprocessCommand(command));
            command.addListener(this);

            if (key != null) {
                bindKey(key, command);
            }

            this.command = () -> command;
        }

        protected void remove() {
            command.get().removeListener(this);
        }

        @Override
        public final void commandStatusChanged(boolean active) {
            button.setEnabled(active);
        }

        @Override
        public final void commandIconChanged(ImageIcon icon) {
            button.setIcon(icon);
            button.setDisabledIcon(ImageUtils.grayscale(icon));
        }

        private void preprocessCommand(EntityCommand<Entity> command) {
            String confirmation = command.acquireConfirmation();
            if (confirmation != null) {
                SwingUtilities.invokeLater(() -> MessageBox.show(
                        MessageType.CONFIRMATION, null, confirmation,
                        (close) -> {
                            if (close.getID() == Dialog.OK) {
                                executeCommand(command);
                            }
                        }
                ));
            } else {
                SwingUtilities.invokeLater(() -> executeCommand(command));
            }
        }

        private void executeCommand(EntityCommand<Entity> command) {
            Map<String, IComplexType> params = command.getParameters();
            if (params != null) {
                List<Entity> context = command.getContext();
                Logger.getLogger().debug("Perform command [{0}]. Context: {1}", command.getName(), context);
                context.forEach(entity -> command.execute(entity, params));
                command.activate();
            }
        }

        private void bindKey(KeyStroke key, EntityCommand<Entity> command) {
            InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            if (inputMap.get(key) != null && inputMap.get(key) == this) {
                throw new IllegalStateException(MessageFormat.format(
                        "Key [{0}] already used by command ''{1}''",
                        getKeyText(key), inputMap.get(key).getClass().getSimpleName()
                ));
            } else {
                inputMap.put(key, this);
                getActionMap().put(this, new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (isEnabled()) {
                            preprocessCommand(command);
                        }
                    }
                });
            }
        }
    }


    private class GroupButton extends EntityCommandButton implements MouseListener, PopupMenuListener {

        final String groupId;
        private final JButton    popup;
        private final JPopupMenu menu;

        GroupButton(EntityCommand<Entity> command) {
            super(command);
            groupId = command.getGroupId();
            button.addMouseListener(this);
            button.getModel().removeChangeListener(this);
            addMouseListener(this);

            popup = new JButton(ARROW);
            popup.setFocusPainted(false);
            popup.setOpaque(false);
            popup.setContentAreaFilled(false);
            popup.setBorder(new EmptyBorder(0, 1, 0, 0));
            popup.setMaximumSize(new Dimension(15, 28));
            popup.addMouseListener(this);

            menu = new JPopupMenu();
            menu.setForeground(Color.BLACK);
            menu.addPopupMenuListener(this);

            popup.addActionListener((ActionEvent ev) -> menu.show(
                    button,
                    button.getBounds().x - 2 ,
                    button.getBounds().y  + button.getBounds().height + 1)
            );
            add(popup);
        }

        @Override
        protected final void remove() {
            command.get().removeListener(this);
            Arrays.stream(menu.getComponents())
                    .map(component -> (EntityCommandButton) component)
                    .forEach(EntityCommandButton::remove);
        }

        void addGroupItem(EntityCommand<Entity> command) {
            EntityCommandButton cmdButton = new EntityCommandButton(command, true);
            cmdButton.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
            cmdButton.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    menu.setVisible(false);
                }
            });
            menu.add(cmdButton);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            if (!menu.isVisible()) {
                setBorder(HOVER_BORDER);
                setBackground(HOVER_COLOR);
                popup.setBorder(new MatteBorder(0, 1, 0, 0, Color.GRAY));
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (!menu.isVisible()) {
                setBorder(EMPTY_BORDER);
                setBackground(null);
                popup.setBorder(new EmptyBorder(0, 1, 0, 0));
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {}

        @Override
        public void mousePressed(MouseEvent e) {}

        @Override
        public void mouseReleased(MouseEvent e) {}

        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            setBorder(PRESS_BORDER);
            setBackground(HOVER_COLOR);
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            if (!(button.getModel().isRollover() || popup.getModel().isRollover())) {
                setBorder(EMPTY_BORDER);
                setBackground(null);
                popup.setBorder(new EmptyBorder(0, 1, 0, 0));
            } else {
                setBorder(HOVER_BORDER);
            }
        }

        @Override
        public void popupMenuCanceled(PopupMenuEvent e) {
            popupMenuWillBecomeInvisible(e);
        }

    }

}
