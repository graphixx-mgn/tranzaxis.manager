package codex.command;

import codex.component.button.PushButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.IEditor;
import codex.log.Logger;
import codex.model.Entity;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import javax.swing.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class CommandButton extends PushButton implements ICommandListener<Entity>, ActionListener {

    private final static int BUTTON_SIZE = IEditor.FONT_VALUE.getSize() * 2;
    protected final Supplier<EntityCommand<Entity>> commandSupplier;

    public CommandButton(EntityCommand<Entity> command) {
        this(command, false);
    }

    public CommandButton(EntityCommand<Entity> command, boolean showTitle) {
        super(
                command.getIcon() == null ? null : ImageUtils.resize(command.getIcon(), BUTTON_SIZE, BUTTON_SIZE),
                showTitle ? command.getTitle() : null
        );
        KeyStroke key = command.getKey();
        if (command.getHint() != null) {
            setHint(command.getHint().concat(key == null ? "" : " (" + getKeyText(key) + ")"));
        }
        if (key != null) {
            bindKey(key, command);
        }
        this.commandSupplier = () -> command;

        button.addActionListener(this);
        command.addListener(CommandButton.this);
        addPropertyChangeListener("ancestor", event -> {
            if (event.getOldValue() != null && event.getNewValue() == null) {
                command.removeListener(CommandButton.this);
            }
        });
    }

    @Override
    public final void commandStatusChanged(boolean active) {
        button.setEnabled(active);
    }

    @Override
    public final void commandIconChanged(ImageIcon icon) {
        setIcon(icon == null ? null : ImageUtils.resize(icon, BUTTON_SIZE, BUTTON_SIZE));
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

    protected void executeCommand(EntityCommand<Entity> command) {
        Map<String, IComplexType> params = command.getParameters();
        if (params != null) {
            List<Entity> context = command.getContext();
            if (context.isEmpty() && command.isActive()) {
                Logger.getLogger().debug("Perform contextless command [{0}]", command.getName());
                command.execute(null, null);
            } else {
                Logger.getLogger().debug("Perform command [{0}]. Context: {1}", command.getName(), context);
                context.forEach(entity -> command.execute(entity, params));
            }
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

    private static String getKeyText(KeyStroke key) {
        if (key.getModifiers() != 0) {
            return KeyEvent.getKeyModifiersText(key.getModifiers()).concat("+").concat(KeyEvent.getKeyText(key.getKeyCode()));
        } else {
            return KeyEvent.getKeyText(key.getKeyCode());
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        preprocessCommand(commandSupplier.get());
    }
}