package codex.component.button;

import codex.command.ICommand;
import static codex.component.button.IButton.PRESS_COLOR;
import java.awt.Color;
import javax.swing.ImageIcon;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * Кнопка вызова исполнения команд {@link ICommand} над объектами.
 */
public class CommandButton extends PushButton implements IButton {
    
    /**
     * Рамка по-умолчанию неактивной кнопки.
     */
    private static final Border NORMAL_BORDER = new CompoundBorder(
            new MatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY),
            new EmptyBorder(1, 0, 1, 1)
    );
    
    /**
     * Конструктор экземпляра кнопки.
     * @param icon Иконка устанавливаемая на кнопку, не может быть NULL, поскольку 
     * иконка обязательна ввиду отсутствия подписи.
     */
    public CommandButton(ImageIcon icon) {
        super(icon, null);
        button.setBorder(new EmptyBorder(2, 2, 2, 2));
        setBorder(NORMAL_BORDER);
    }
    
    @Override
    protected final void redraw() {
        if (button.getModel().isPressed()) {
            setBorder(PRESS_BORDER);
            setBackground(PRESS_COLOR);
        } else if (button.getModel().isRollover()) {
            setBorder(HOVER_BORDER);
            setBackground(HOVER_COLOR);
        } else {
            setBorder(NORMAL_BORDER);
            setBackground(null);
        }
    }
    
}
