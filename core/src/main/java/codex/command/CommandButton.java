package codex.command;

import codex.component.button.PushButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.IEditor;
import codex.model.Entity;
import codex.utils.ImageUtils;
import javax.swing.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Класс реализует кнопку запуска команд сущности в командных панелях презентаций:<br>
 * * {@link codex.presentation.EditorPresentation}<br>
 * * {@link codex.presentation.SelectorPresentation}<br>
 * Экземпляры кнопок автоматически создаются при построении командной панели {@link codex.presentation.CommandPanel}
 * при изменении контекста презентации. Кнопка является слушателем {@link ICommandListener} событий команды, которое вызывается
 * при активации / деактивации команды в процессе обработки метода расчета состояния {@link EntityCommand#activate()}.
 */
public class CommandButton extends PushButton implements ICommandListener<Entity>, ActionListener {

    private final static int BUTTON_SIZE = IEditor.FONT_VALUE.getSize() * 2;
    private final Supplier<EntityCommand<Entity>> commandSupplier;

    /**
     * Стандартный конструктор кнопки независимой команды сущности. Независимая команда располагается непосредственно в
     * панели команд презентации сущности.
     * @param command Ссылка на вызываемую команду.
     */
    public CommandButton(EntityCommand<Entity> command) {
        this(command, false);
    }

    /**
     * Расширенный конструктор кнопки команды сущности. Помимо ссылки на команду имеет флаг, указывающий на способ
     * отрисовки наименования команды. Если TRUE - наименование команды будет отрисовываться непосредственно на кнопке,
     * иначе - наименование бдет отображаться во всплывающей подсказке при наведении курсора мыши на кнопку. В этом случае
     * к наименованию будет добавлена и аббревиатура комбинации клавиш для быстрого вызова (если команда реализует такую возможность).
     * @param command Ссылка на вызываемую команду.
     * @param showTitle Способ отображения наименования: TRUE - текст на кнопке, FALSE - в виде всплвающей подсказки.
     */
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
            bindKey(key);
        }
        this.commandSupplier = () -> command;

        button.addActionListener(this);
        command.addListener(CommandButton.this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        preprocessCommand();
    }

    @Override
    public final void commandStatusChanged(boolean active) {
        button.setEnabled(active);
    }

    @Override
    public final void commandIconChanged(ImageIcon icon) {
        setIcon(icon == null ? null : ImageUtils.resize(icon, BUTTON_SIZE, BUTTON_SIZE));
    }

    /**
     * Метод, предварительно запрашивающий подтверждение пользователя на исполнение команды если команда реализует
     * метод {@link EntityCommand#acquireConfirmation()}, который возвращает не пусттую строку текста запроса.
     * В противном случае команда исполняется незамедлительно.
     */
    private void preprocessCommand() {
        EntityCommand<Entity> command = commandSupplier.get();
        String confirmation = command.acquireConfirmation();
        if (confirmation != null) {
            SwingUtilities.invokeLater(() -> MessageBox.show(
                    MessageType.CONFIRMATION, null, confirmation,
                    (close) -> {
                        if (close.getID() == Dialog.OK) {
                            executeCommand();
                        }
                    }
            ));
        } else {
            SwingUtilities.invokeLater(this::executeCommand);
        }
    }

    /**
     * Вызов метода исполнения команды {@link EntityCommand#process()}.
     */
    protected void executeCommand() {
        commandSupplier.get().process();
    }

    /**
     * Привязка "горячей" комбинации клавиш клавиатуры к компоненту GUI для вызова данной команды.
     * @param key Ссылка на комбинацию, указанную в специальном конструкторе команды
     * {@link EntityCommand#EntityCommand(String, String, ImageIcon, String, Predicate, KeyStroke)}
     */
    private void bindKey(KeyStroke key) {
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
                        preprocessCommand();
                    }
                }
            });
        }
    }

    /**
     * Метод генерации текстовой аббревиатуры горячей клавиши для отображении во всплывающей подсказке.
     * @param key Ссылка на комбинацию, указанную в специальном конструкторе команды
     * {@link EntityCommand#EntityCommand(String, String, ImageIcon, String, Predicate, KeyStroke)}
     */
    private static String getKeyText(KeyStroke key) {
        if (key.getModifiers() != 0) {
            return KeyEvent.getKeyModifiersText(key.getModifiers()).concat("+").concat(KeyEvent.getKeyText(key.getKeyCode()));
        } else {
            return KeyEvent.getKeyText(key.getKeyCode());
        }
    }
}