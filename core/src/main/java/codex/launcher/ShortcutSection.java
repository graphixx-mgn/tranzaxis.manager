package codex.launcher;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.config.IConfigStoreService;
import codex.editor.EntityRefEditor;
import codex.editor.IEditor;
import codex.editor.IEditorFactory;
import codex.model.Entity;
import codex.model.IModelListener;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.FocusManager;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Реализация класса сущности - секции ярлыков. Используется для группировки ярлыков.
 */
public class ShortcutSection extends Entity implements IModelListener {
    
    private final static IConfigStoreService CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
    final static String DEFAULT = Language.get(ShortcutSection.class, "default", Locale.US);
    
    private JPanel view;

    /**
     * Конструктор сущности.
     * @param owner Ссылка на владельца. Не используется.
     * @param PID Имя секции, отображаемое в окне.
     */
    public ShortcutSection(EntityRef owner, String PID) {
        super(null, ImageUtils.getByPath("/images/folder.png"), PID, null);
    }
    
    /**
     * Возвращяет (создает при первом обращении) виджет секции для размещения его на панели модуля.
     */
    JPanel getView() {
        if (view == null) {
            view = createView();
        }
        return view;
    }
    
    /**
     * Создает виджет для ярлыка и помешает его в последнюю позицию виджета секции.
     * @param shortcut Ссылка на сущность - ярлык.
     */
    LaunchShortcut addShortcut(Shortcut shortcut) {
        LaunchShortcut launcher = new LaunchShortcut(shortcut);
        addLauncher(launcher, getLaunchersCount());
        return launcher;
    }
    
    /**
     * Размещает виджет ярлыка в указанной позиции внутри виджета секции.
     * @param launcher Виджет ярлыка.
     * @param position Позиция (нумерация с '0').
     */
    void addLauncher(LaunchShortcut launcher, int position) {
        ((Container) getView().getComponent(0)).add(launcher, position);
        updateProperties();
    }
    
    /**
     * Возвращает количество ярлыков в виджете секции.
     */
    private int getLaunchersCount() {
        return ((Container) getView().getComponent(0)).getComponentCount();
    }
    
    /**
     * Возвращает упорядоченный список виджетов ярлыков в виджете секции.
     */
    private List<LaunchShortcut> getLaunchers() {
        return Arrays.stream(((Container) getView().getComponent(0)).getComponents())
                .map((component) -> (LaunchShortcut) component)
                .collect(Collectors.toList());
    }
    
    /**
     * Возвращает упорядоченный список ярлыков в секции.
     */
    List<Shortcut> getShortcuts() {
        return getLaunchers().stream()
                .map(LaunchShortcut::getShortcut)
                .collect(Collectors.toList());
    }
    
    /**
     * Корректирует внутренние свойства ярлыков для соответстсвия их расположению виджетов.
     * Используется при перестроении элементов 
     * (Drag & Drop, перемещение ярлыков при удалении секции).
     */
    private void updateProperties() {
        List<LaunchShortcut> launchers = getLaunchers();
        List<Integer> sequences = launchers.stream()
                .map((launcher) -> launcher.getShortcut().getSEQ())
                .sorted()
                .collect(Collectors.toList());

        Iterator<Integer> seqIterator = sequences.iterator();
        
        launchers.forEach((launcher) -> {
            launcher.stateChanged();
            launcher.getShortcut().setSection(this).setSEQ(seqIterator.next());
            if (!launcher.getShortcut().model.getChanges().isEmpty()) {
                try {
                    launcher.getShortcut().model.commit(false);
                } catch (Exception e) {
                    //
                }
            }
        });
    }
    
    /**
     * Создание виджета секции.
     */
    private JPanel createView() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BorderLayout());
        wrapper.setBorder(new EmptyBorder(0, 3, 5, 3));
        
        JPanel container = new ShortcutContainer(this) {
            @Override
            public Component add(Component comp) {
                Component c = super.add(comp);
                setVisible(true);
                return c;
            }

            @Override
            public Component add(Component comp, int index) {
                Component c = super.add(comp, index);
                setVisible(true);
                return c;
            }

            @Override
            public void remove(Component comp) {
                super.remove(comp);
                if (getPID().equals(DEFAULT) && getComponentCount() == 0) {
                    setVisible(false);
                }
            }
        };
        container.setBorder(
            new TitledBorder(
                new LineBorder(Color.LIGHT_GRAY, 1),
                getPID().equals(DEFAULT) ? Language.get("default") : getPID(),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION,
                IEditor.FONT_BOLD,
                getPID().equals(DEFAULT) ? Color.GRAY : Color.decode("#3399FF")
            )
        );
        
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        
        if (!getPID().equals(DEFAULT)) {
            container.addMouseListener(new TitledBorderListener());
            
            JLabel signDelete = new JLabel(ImageUtils.resize(ImageUtils.getByPath("/images/close.png"), 15, 15));
            signDelete.setBorder(new EmptyBorder(5, 0, 0, 0));
            signDelete.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    remove();
                }
            });
            controls.add(signDelete);
        } else {
            controls.setPreferredSize(new Dimension(15, 0));
            container.setVisible(false);
        }
        
        wrapper.add(container, BorderLayout.CENTER);
        wrapper.add(controls,  BorderLayout.WEST);
        
        return wrapper;
    }
    
    /**
     * Удаление секции и её виджета.
     */
    private void remove() {
        List<IConfigStoreService.ForeignLink> links = CAS.findReferencedEntries(ShortcutSection.class, getID());
        if (links.isEmpty()) {                    
            if (model.remove()) {
                Container panel = getView().getParent();
                panel.remove(getView());
            }
        } else {
            DialogButton BTN_MOVE  = new DialogButton(
                    ImageUtils.resize(ImageUtils.getByPath("/images/moveto.png"), 22, 22), Language.get("move@title"), -1, 100
            );
            DialogButton BTN_DROP  = new DialogButton(
                    ImageUtils.resize(ImageUtils.getByPath("/images/remove.png"), 22, 22), Language.get("drop@title"), -1, 101
            );
            
            final EntityRef<ShortcutSection> sectionRef = new EntityRef<ShortcutSection>(ShortcutSection.class) {
                @Override
                public IEditorFactory<EntityRef<ShortcutSection>, ShortcutSection> editorFactory() {
                    return propHolder -> new EntityRefEditor<ShortcutSection>(propHolder) {
                        @Override
                        protected List<ShortcutSection> getValues() {
                            return CAS.readCatalogEntries(null, getEntityClass()).values().stream()
                                    .filter((PID) -> !PID.equals(ShortcutSection.DEFAULT) && !PID.equals(getPID()))
                                    .map((PID) -> newInstance(getEntityClass(), null, PID))
                                    .collect(Collectors.toList());
                        }
                    };
                }
            };

            ParamModel paramModel = new ParamModel();
            paramModel.addProperty("section", sectionRef, false);
            
            final Dialog actionDialog = new Dialog(
                FocusManager.getCurrentManager().getActiveWindow(), 
                ImageUtils.getByPath("/images/warn.png"), 
                Language.get("error@notempty"),
                new EditorPage(paramModel),
                (event) -> {
                    if (event.getID() == 100) {
                        final ShortcutSection newSection = (ShortcutSection) (
                                paramModel.getValue("section") != null ?
                                    paramModel.getValue("section") :
                                    Entity.newInstance(ShortcutSection.class, null, ShortcutSection.DEFAULT)
                        );
                        getLaunchers().forEach((launcher) -> newSection.addLauncher(launcher, newSection.getLaunchersCount()));
                        if (model.remove()) {
                            Container panel = getView().getParent();
                            panel.remove(getView());
                        }
                    } else if (event.getID() == 101) {
                        getLaunchers().forEach((launcher) -> {
                            launcher.getShortcut().model.remove();
                            launcher.getParent().remove(launcher);
                        });
                        if (model.remove()) {
                            Container panel = getView().getParent();
                            panel.remove(getView());
                        }
                    }
                },
                BTN_MOVE, BTN_DROP
            );
    
            actionDialog.pack();
            actionDialog.setPreferredSize(new Dimension(500, actionDialog.getPreferredSize().height));
            actionDialog.setVisible(true);
        }
    }
    
    /**
     * Реализация класса, реализующего переименование секции.
     */
    private class TitledBorderListener extends MouseAdapter implements DocumentListener {
    
        private JPopupMenu   editPopup;  
        private JTextField   editTextField;
        private TitledBorder titledBorder;

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() != 2)
                return;

            JComponent component = (JComponent) e.getSource();
            Border border = component.getBorder();

            if (border instanceof TitledBorder) {
                titledBorder = (TitledBorder) border;
                FontMetrics fm = component.getFontMetrics(titledBorder.getTitleFont());
                int titleWidth = fm.stringWidth(titledBorder.getTitle()) + 20;
                Rectangle bounds = new Rectangle(0, 0, titleWidth, fm.getHeight());

                if (bounds.contains(e.getPoint())) {
                    if (editPopup == null) 
                        createEditPopup();

                    editTextField.setText(titledBorder.getTitle());
                    Dimension dim = editTextField.getPreferredSize();
                    dim.width = titleWidth * 2;
                    editPopup.setPreferredSize(dim);
                    editPopup.show(component, 4, -2);

                    editTextField.selectAll();
                    editTextField.requestFocusInWindow();
                }
            }
        }

        private void createEditPopup() {
            editTextField = new JTextField();
            editTextField.getDocument().addDocumentListener(this);
            editTextField.setInputVerifier(new InputVerifier() {
                @Override
                public boolean verify(JComponent input) {
                    String value = ((JTextField) input).getText();
                    return !(
                            value.isEmpty() ||
                            CAS.readCatalogEntries(null, ShortcutSection.class).entrySet().stream()
                                    .anyMatch((entry) ->
                                            !entry.getKey().equals(ShortcutSection.this.getID()) &&
                                            entry.getValue().equals(value)
                                    )
                    );
                }
            });
            editTextField.addActionListener((ActionEvent event) -> {
                String value = editTextField.getText();
                if (editTextField.getInputVerifier().verify(editTextField)) {
                    ShortcutSection.this.setPID(value);
                    try {
                        ShortcutSection.this.model.commit(true);
                        titledBorder.setTitle(value);
                    } catch (Exception e) {
                        ShortcutSection.this.model.rollback();
                    }
                    
                    editPopup.setVisible(false);
                    editPopup.getInvoker().revalidate();
                    editPopup.getInvoker().repaint();
                }
            });

            editPopup = new JPopupMenu();
            editPopup.setBorder(new EmptyBorder(0, 0, 0, 0));
            editPopup.add(editTextField);
        }
        
        private void setEditorBorder(boolean validated) {
            editTextField.setBorder(new CompoundBorder(
                    new LineBorder(validated ? Color.decode("#3399FF") : IEditor.COLOR_INVALID, 1),
                    new EmptyBorder(1, 3, 1, 3)
            ));
        }

        @Override
        public void insertUpdate(DocumentEvent event) {
            setEditorBorder(editTextField.getInputVerifier().verify(editTextField));
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            setEditorBorder(editTextField.getInputVerifier().verify(editTextField));
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            setEditorBorder(editTextField.getInputVerifier().verify(editTextField));
        }
        
    }
}
