package codex.component.dialog;

import codex.component.button.DialogButton;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
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
     * Код выхода при нажатии стандартной кнопки {@link Default#BTN_CLOSE} или назначенной 
     * ей клавише клавиатуры ESC.
     */
    public static final int CLOSE  = 3;
    
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
        BTN_CANCEL("/images/cancel.png", "cancel@title", KeyEvent.VK_ESCAPE, CANCEL),
        /**
         * Шаблон стандартной кнопки закрытия, также вызывается по клавише ESC.
         */
        BTN_CLOSE("/images/close.png", "close@title", KeyEvent.VK_ESCAPE, CLOSE);

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
    private boolean                preventDefault = false;
    
    protected Function<DialogButton, ActionListener> handler;
    
    /**
     * Конструктор диалога с указанием шаблонов кнопок.
     * @param parent Указатель на родительское окна для относительного позиционирования на экране.
     * @param icon Иконка в заколовке окна диалога.
     * @param title Название окна диалога.
     * @param content Панель с контентом.
     * @param close Слушатель события закрытия окна.
     * @param buttonTemplates Список шаблонов кнопок произвольной длины.
     */
    public Dialog(Window parent, ImageIcon icon, String title, JPanel content, ActionListener close, Default... buttonTemplates) {
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
    public Dialog(Window parent, ImageIcon icon, String title, JPanel content, ActionListener close, DialogButton... buttons) {
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
        
        handler = (button) -> {
            return (keyEvent) -> {
                preventDefault = true;
                dispose();
                if (close != null) {
                    final ActionEvent event = new ActionEvent(
                            keyEvent, 
                            button == null || !button.isEnabled() ? EXIT : button.getID(),
                            null
                    );
                    close.actionPerformed(event);
                }
            };
        };

        int maxWidth = Arrays.asList(buttons).stream().map((button) -> button.getPreferredSize().width).max(Integer::compareTo).get();
        for (DialogButton button : buttons) {
            button.setPreferredSize(new Dimension(maxWidth, button.getPreferredSize().height));
            buttonPanel.add(button);
            
            // Обрабочик нажатия мыши
            button.addActionListener((e) -> {
                handler.apply(button).actionPerformed(new ActionEvent(this, button.getID(), null));
            });
            if (button.getKeyCode() != null) {
                // Обработчики нажатия кнопки клавиатуры 
                inputMap.put(button.getKeyCode(), button.getKeyCode());
                actionMap.put(button.getKeyCode(), new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        handler.apply(button).actionPerformed(new ActionEvent(this, button.getID(), null));
                    }
                });
            }
        }
        
        // Установка обработчика ESC если нет кнопки
        KeyStroke stroke = KeyStroke.getKeyStroke(Default.BTN_CANCEL.key, 0);
        if (inputMap.allKeys().length == 0 || !Arrays.asList(inputMap.allKeys()).contains(stroke)) {
            inputMap.put(stroke, stroke);
            actionMap.put(stroke, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    handler.apply(null).actionPerformed(new ActionEvent(this, Default.BTN_CANCEL.ID, null));
                }
            });
        }
        
        // Установка обработчика закрытия окна
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (!preventDefault) {
                    handler.apply(null).actionPerformed(new ActionEvent(this, EXIT, null));
                }
                preventDefault = false;
            }
        });
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
    
    public final void setContent(JPanel content) {
        contentPanel.removeAll();
        contentPanel.add(content);
    }

}