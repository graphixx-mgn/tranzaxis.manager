package codex.editor;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.component.button.IButton;
import codex.component.render.GeneralRenderer;
import codex.explorer.IExplorerAccessService;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.mask.EntityFilter;
import codex.mask.IRefMask;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.ICatalog;
import codex.presentation.EditorPresentation;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import net.jcip.annotations.ThreadSafe;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicComboPopup;

/**
 * Редактор свойств типа {@link EntityRef}, представляет собой выпадающий список
 * сущностей, найденный по классу и с учетом фильтра указанных в свойстве.
 */
@ThreadSafe
public class EntityRefEditor<T extends Entity> extends AbstractEditor<EntityRef<T>, T> implements ActionListener, INodeListener {

    private JComboBox<T> comboBox;
    private EmbeddedEditor embeddedEditor;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public EntityRefEditor(PropertyHolder<EntityRef<T>, T> propHolder) {
        super(propHolder);
        embeddedEditor = new EmbeddedEditor();
        addCommand(embeddedEditor);
    }

    protected List<T> getValues() {
        EntityRef<T> ref = propHolder.getPropValue();
        Class<T> entityClass = ref.getEntityClass();
        IExplorerAccessService EAS = ServiceRegistry.getInstance().lookupService(IExplorerAccessService.class);

        return entityClass != null ? EAS.getEntitiesByClass(entityClass)
                    .stream()
                    .map(entity -> (T) entity)
                    .filter(entity -> ref.getMask().verify(entity))
                    .collect(Collectors.toList())
            : new LinkedList<>();
    }
    
    @Override
    public Box getEditor() {
        SwingUtilities.invokeLater(() -> {
            comboBox.removeActionListener(this);
            comboBox.removeAllItems();

            if (!propHolder.getPropValue().isEmpty()) {
                comboBox.addItem(propHolder.getPropValue().getValue());
                setValue(propHolder.getPropValue().getValue());
            } else {
                comboBox.addItem((T) new Undefined());
                setValue(null);
            }
            comboBox.addActionListener(this);
        });
        return super.getEditor();
    }

    @Override
    public Box createEditor() {        
        comboBox = new JComboBox<T>() {

            @Override
            public void removeAllItems() {
                for (int i = 0; i < getItemCount(); i++) {
                    Entity item = getItemAt(i);
                    if (item != null) {
                        item.removeNodeListener(EntityRefEditor.this);
                    }
                }
                super.removeAllItems();
            }

            @Override
            public void addItem(T item) {
                try {
                    super.addItem(item);
                    if (item != null) {
                        item.addNodeListener(EntityRefEditor.this);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        comboBox.addItem((T) new Undefined());
        
        UIManager.put("ComboBox.border", new BorderUIResource(
                new LineBorder(UIManager.getColor ("Panel.background"), 1))
        );
        SwingUtilities.updateComponentTreeUI(comboBox);

        comboBox.setFont(FONT_VALUE);
        comboBox.setRenderer(new GeneralRenderer<T>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean isSelected, boolean hasFocus) {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                IRefMask<T> mask = propHolder.getPropValue().getMask();
                try {
                    EntityFilter.Match match = mask.getEntityMatcher().apply(value);
                    switch (match) {
                        case Exact:
                            component.setForeground(Color.decode("#1C5F0A"));
                            component.setFont(component.getFont().deriveFont(Font.BOLD));
                            break;
                        case About:
                            component.setForeground(Color.decode("#E8A442"));
                            component.setFont(component.getFont().deriveFont(Font.BOLD));
                            break;
                    }
                } catch (ClassCastException e) {
                    //
                }
                return component;
            }
        });
        comboBox.addFocusListener(this);
        comboBox.addActionListener(this);
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                rebuildList();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                if (propHolder.getPropValue().getValue() == null) {
                    comboBox.setSelectedItem(comboBox.getItemAt(0));
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {}
        });
        
        Object child = comboBox.getAccessibleContext().getAccessibleChild(0);
        BasicComboPopup popup = (BasicComboPopup)child;
        popup.setBorder(IButton.PRESS_BORDER);
        
        Box container = new Box(BoxLayout.X_AXIS);
        container.add(comboBox);
        return container;
    }
    
    private void rebuildList() {
        comboBox.removeActionListener(EntityRefEditor.this);
        comboBox.removeAllItems();

        List<T> values = getValues();

        comboBox.addItem((T) new Undefined());
        values.forEach((entity) -> {
            if (propHolder.getPropValue().getValue() != null &&
                    entity.getPID().equals(propHolder.getPropValue().getValue().getPID()))
            {
                comboBox.addItem(propHolder.getPropValue().getValue());
                setValue(propHolder.getPropValue().getValue());
            } else {
                comboBox.addItem(entity);
            }
        });
        if (propHolder.getPropValue().getValue() != null && !values.contains(propHolder.getPropValue().getValue())) {
            comboBox.addItem(propHolder.getPropValue().getValue());
            setValue(propHolder.getPropValue().getValue());
        }
        if (propHolder.getPropValue().getValue() == null) {
            IRefMask<T> mask = propHolder.getPropValue().getMask();
            Function<T, EntityFilter.Match> entityMatcher = mask.getEntityMatcher();
            values.stream()
                    .filter((entity) -> entityMatcher.apply(entity) == EntityFilter.Match.Exact)
                    .findFirst()
                    .ifPresent(entity -> comboBox.setSelectedItem(entity));
        }
        comboBox.addActionListener(EntityRefEditor.this);
    }

    @Override
    public void setValue(T value) {
        SwingUtilities.invokeLater(() -> {
            if (value == null) {
                comboBox.setSelectedItem(comboBox.getItemAt(0));
                comboBox.setForeground(Color.GRAY);
                comboBox.setFont(FONT_VALUE);
            } else {
                if (!comboBox.getSelectedItem().equals(value)) {
                    if (((DefaultComboBoxModel) comboBox.getModel()).getIndexOf(value) == -1) {
                        comboBox.addItem(value);
                    }
                    comboBox.setSelectedItem(value);
                }
                JList list = ((BasicComboPopup) comboBox.getAccessibleContext().getAccessibleChild(0)).getList();
                Component rendered = comboBox.getRenderer().getListCellRendererComponent(list, value, comboBox.getSelectedIndex(), false, false);
                comboBox.setForeground(rendered.getForeground());
                comboBox.setFont(rendered.getFont());
            }
        });
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        SwingUtilities.invokeLater(() -> comboBox.setEnabled(editable));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (comboBox.getSelectedIndex() == 0) {
            propHolder.setValue(null);
        } else if (comboBox.getSelectedIndex() > 0) {
            if (!comboBox.getSelectedItem().equals(propHolder.getPropValue().getValue())) {
                propHolder.setValue(comboBox.getItemAt(comboBox.getSelectedIndex()));
            }
        }
    }
    
    @Override
    public Component getFocusTarget() {
        return comboBox;
    }

    @Override
    public void childChanged(INode node) {
        SwingUtilities.invokeLater(() -> {
            if (comboBox.getSelectedItem() == node) {
                comboBox.revalidate();
                comboBox.repaint();
            }
        });
    }


    private class EmbeddedEditor extends EditorCommand<EntityRef<T>, T> {

        private EmbeddedEditor() {
            super(new ImageIcon(), null, PropertyHolder::isEmpty);
            activator = holder -> {
                Entity entity = propHolder.getPropValue().getValue();
                if (entity == null) {
                    return new CommandStatus(false, EditorPresentation.EmbeddedEditor.IMAGE_EDIT);
                } else {
                    boolean hasProps = !entity.model.getProperties(Access.Edit)
                            .stream()
                            .filter(propName -> !propName.equals(EntityModel.PID))
                            .collect(Collectors.toList())
                            .isEmpty();
                    if (!hasProps) {
                        return new CommandStatus(false, null);
                    }
                    boolean allDisabled = entity.model.getProperties(Access.Edit).stream()
                            .noneMatch(
                                    (name) -> entity.model.getEditor(name).isEditable()
                            );
                    boolean hasChild = ICatalog.class.isAssignableFrom(entity.getClass()) && entity.getChildCount() > 0;
                    return new CommandStatus(
                            true,
                            allDisabled || entity.islocked() ?
                                    EditorPresentation.EmbeddedEditor.IMAGE_VIEW :
                                    EditorPresentation.EmbeddedEditor.IMAGE_EDIT
                    );
                }
            };
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }

        @Override
        public void execute(PropertyHolder<EntityRef<T>, T> context) {
            EditorPresentation.EmbeddedEditor.show(context.getPropValue().getValue());
        }
    }
}
