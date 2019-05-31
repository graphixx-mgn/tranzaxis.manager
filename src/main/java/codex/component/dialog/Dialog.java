package codex.component.dialog;

import codex.component.button.DialogButton;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.FocusManager;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            this.icon  = ImageUtils.getByPath(iconPath);
            this.title = Language.get(Dialog.class, titleLocaleKey);
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
        /**
         * Создание экземпляра кнопки диалога по шаблону {@link Default#newInstance()}.
         * @param icon Новая иконка кнопки.
         */
        public DialogButton newInstance(ImageIcon icon) {
            return new DialogButton(icon, title, key, ID);
        }
        /**
         * Создание экземпляра кнопки диалога по шаблону {@link Default#newInstance()}.
         * @param title Новый текст кнопки.
         */
        public DialogButton newInstance(String title) {
            return new DialogButton(icon, title, key, ID);
        }
        /**
         * Создание экземпляра кнопки диалога по шаблону {@link Default#newInstance()}.
         * @param icon Новая иконка кнопки.
         * @param title Новый текст кнопки.
         */
        public DialogButton newInstance(ImageIcon icon, String title) {
            return new DialogButton(icon, title, key, ID);
        }
    }
    
    private final JPanel contentPanel;
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
        this(parent, icon, title, content, close, Arrays.stream(buttonTemplates)
                .map(Default::newInstance)
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
        
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setIconImage(icon.getImage());
        
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(content);
        add(contentPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        add(buttonPanel, BorderLayout.SOUTH);
        
        handler = (button) -> (keyEvent) -> {
            setVisible(false);
            if (close != null) {
                final ActionEvent event = new ActionEvent(
                        keyEvent,
                        button == null || !button.isEnabled() ? EXIT : button.getID(),
                        null
                );
                close.actionPerformed(event);
            }
        };

        int maxWidth = Arrays.asList(buttons).stream().map((button) -> button.getPreferredSize().width).max(Integer::compareTo).get();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();
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
                handler.apply(null).actionPerformed(new ActionEvent(this, EXIT, null));
            }
        });
    }

    @Override
    public void setLocationRelativeTo(Component c) {
        Window owner = IComplexType.coalesce(getOwner(), FocusManager.getCurrentManager().getActiveWindow());
        super.setLocationRelativeTo(owner != null ? owner : c);
    }
    
    /**
     * Отображение или скрытие окна диалога.
     * @param visible TRUE - если требуется показать окно, FALSE - если нужно скрыть.
     */
    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            pack();
            setLocationRelativeTo(null);
        }
        super.setVisible(visible);
    }
    
    public final void setContent(JPanel content) {
        contentPanel.removeAll();
        contentPanel.add(content);
    }

}