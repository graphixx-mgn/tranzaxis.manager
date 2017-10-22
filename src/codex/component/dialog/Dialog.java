package codex.component.dialog;

import codex.component.button.DialogButton;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

/**
 * Диалоговое окно для отображения и/или запроса информации.
 */
public class Dialog extends JDialog {
    /**
     * Код выхода при закрытии окна
     */
    public static final int EXIT   = 0;
    /**
     * Код выхода при нажатии стандартной кнопки {@link Default#BTN_OK} или назначенной 
     * ей клавише клавиатуры ENTER.
     */
    public static final int OK     = 1;
    /**
     * Код выхода при нажатии стандартной кнопки {@link Default#BTN_CANCEL} или назначенной 
     * ей клавише клавиатуры ESC.
     */
    public static final int CANCEL = 2;
    
    /**
     * Набор шаблонов для упрощенного создания кнопок диалога, т.к. использование
     * статических констант типа {@link DialogButton} недопустимо.
     */
    public enum Default {
        /**
         * Шаблон стандартной кнопки подтверждения, также вызывается по клавише ENTER.
         */
        BTN_OK("/images/ok.png", "ok@title", KeyEvent.VK_ENTER, OK),
        /**
         * Шаблон стандартной кнопки отмены, также вызывается по клавише ESC.
         */
        BTN_CANCEL("/images/cancel.png", "cancel@title", KeyEvent.VK_ESCAPE, CANCEL);

        final int       ID;
        final ImageIcon icon;
        final String    title;
        final int       key;
        
        /**
         * Конструктор создания шаблона кнопки с указанием основных параметров.
         * @param iconPath Путь к иконке кнопки среди ресурсов.
         * @param titleLocaleKey Ключ для получения локализованной подписи кнопки.
         * @param keyCode Код клавиши клавиатуры (см. константы VK_*** в {@link KeyEvent}).
         * @param id Код возврата кнопки, который можно будет анализировать после 
         * закрытия диалога.
         */
        Default (String iconPath, String titleLocaleKey, int keyCode, int id) {
            this.ID    = id;
            this.icon  = ImageUtils.resize(ImageUtils.getByPath(iconPath), 26, 26);
            this.title = Language.get("Dialog", titleLocaleKey);
            this.key   = keyCode;
        }
        
        /**
         * Создание экземпляра кнопки диалога по шаблону. Для этой цели есть 
         * специальный конструктор класса {@link Dialog}. Также можно создать
         * кнопку на основе шаблона, затем модифицировать её:
         * <pre>{@code 
         * DialogButton update =  Dialog.Default.BTN_OK.newInstance();
         * update.setIcon(UPDATE_ICON);
         * update.setText("Check update");
         * }</pre>
         * @return 
         */
        public DialogButton newInstance() {
            return new DialogButton(icon, title, key, ID);
        }
    }
    
    private final JPanel           contentPanel;
    private final JPanel           buttonPanel;
    private final Consumer<Dialog> relocate;
    private final InputMap         inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    private final ActionMap        actionMap = rootPane.getActionMap();
    
    /**
     * Конструктор диалога с указанием шаблонов кнопок.
     * @param parent Указатель на родительское окна для относительного позиционирования на экране.
     * @param icon Иконка в заколовке окна диалога.
     * @param title Название окна диалога.
     * @param content Панель с контентом.
     * @param close Слушатель события закрытия окна.
     * @param buttonTemplates Список шаблонов кнопок произвольной длины.
     */
    public Dialog(Window parent, ImageIcon icon, String title, JPanel content, Action close, Default... buttonTemplates) {
        this(parent, icon, title, content, close, Arrays.asList(buttonTemplates)
                .stream()
                .map((template) -> template.newInstance())
                .collect(Collectors.toList())
                .toArray(new DialogButton[]{})
        );
    }
    
    /**
     * Конструктор диалога с указанием списка предварительно созданных кнопок.
     * @param parent Указатель на родительское окна для относительного позиционирования на экране.
     * @param icon Иконка в заколовке окна диалога.
     * @param title Название окна диалога.
     * @param content Панель с контентом.
     * @param close Слушатель события закрытия окна.
     * @param buttons Список кнопок произвольной длины.
     */
    public Dialog(Window parent, ImageIcon icon, String title, JPanel content, Action close, DialogButton... buttons) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        
        relocate = (dialog) -> {
            dialog.setLocationRelativeTo(parent);
        };
        
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setIconImage(icon.getImage());
        
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(content);
        add(contentPanel, BorderLayout.CENTER);
        
        buttonPanel = new JPanel();
        add(buttonPanel, BorderLayout.SOUTH);
        
        Function<DialogButton, AbstractAction> handler = (button) -> {
            return new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent keyEvent) {
                    dispose();
                    if (close != null) {
                        final ActionEvent event = new ActionEvent(
                                keyEvent, 
                                button == null || !button.isEnabled() ? EXIT : button.getID(),
                                null
                        );
                        close.actionPerformed(event);
                    }
                }
            }; 
        };

        int maxWidth = Arrays.asList(buttons).stream().map((button) -> button.getPreferredSize().width).max(Integer::compareTo).get();
        for (DialogButton button : buttons) {
            button.setPreferredSize(new Dimension(maxWidth, button.getPreferredSize().height));
            buttonPanel.add(button);
            
            // Обрабочик нажатия мыши
            button.addActionListener(handler.apply(button));
            if (button.getKeyCode() != null) {
                // Обработчики нажатия кнопки клавиатуры 
                inputMap.put(button.getKeyCode(), button.getKeyCode());
                actionMap.put(button.getKeyCode(), handler.apply(button));
            }
        }
        
        // Установка обработчика ESC если нет кнопки
        KeyStroke stroke = KeyStroke.getKeyStroke(Default.BTN_CANCEL.key, 0);
        if (inputMap.allKeys().length == 0 || !Arrays.asList(inputMap.allKeys()).contains(stroke)) {
            inputMap.put(stroke, stroke);
            actionMap.put(stroke, handler.apply(null));
        }
        pack();
    }
    
    /**
     * Отображение или скрытие окна диалога.
     * @param visible TRUE - если требуется показать окно, FALSE - если нужно скрыть.
     */
    @Override
    public void setVisible(boolean visible) {
        pack();
        relocate.accept(this);
        super.setVisible(visible);
    }

}
