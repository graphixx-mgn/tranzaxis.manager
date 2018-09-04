package codex.presentation;

import codex.command.ICommand;
import codex.editor.IEditor;
import codex.model.AbstractModel;
import codex.model.Access;
import codex.property.PropertyHolder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Vector;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

/**
 * Страница редактирования сущности, используется в составе презентации редактора
 * сущности {@link EditorPresentation}. Формирует панель с редакторами свойств 
 * модели сущности.
 */
public final class EditorPage extends JPanel {
    
    /**
     * Конструктор страницы. 
     * @param model Редактируемая модель сущности.
     */
    public EditorPage(AbstractModel model) {
        super(new GridBagLayout());
        setBorder(new LineBorder(Color.RED, 1));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;
        
        int lineIdx = 1, maxSize = 0;
        final Vector<Component> focusOrder = new Vector<>();
        List<String> properties = model.getProperties(Access.Edit);
        for (String propName : properties) {
            gbc.gridx = 0; 
            gbc.gridy = lineIdx;
            gbc.weightx = 0;
            
            IEditor propEditor = model.getEditor(propName);
            /* Нужно перерисовать компонент на случай если уже было обращение к нему
             * на ранней стадии инициализации сухности */
            ((JComponent) propEditor).updateUI();
            if (propEditor.getFocusTarget() != null && propEditor.isEditable()) {
                focusOrder.add(propEditor.getFocusTarget());
            }
            add(propEditor.getLabel(), gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1;
            Box container = new Box(BoxLayout.X_AXIS);
            Box editor    = propEditor.getEditor();
            
            propEditor.getLabel().setVisible(propEditor.isVisible());
            container.setVisible(propEditor.isVisible());
            editor.addPropertyChangeListener("visible", (event) -> {
                propEditor.getLabel().setVisible(propEditor.isVisible());
                container.setVisible(propEditor.isVisible());
            });
            
            container.add(editor);
            container.add(Box.createRigidArea(new Dimension(1, 28)));

            ((List<ICommand<PropertyHolder>>) propEditor.getCommands()).stream().forEach((command) -> {
                editor.add((JComponent) command.getButton());
            });
            add(container, gbc);
            
            maxSize = Math.max(maxSize, propEditor.getLabel()
                    .getFontMetrics(IEditor.FONT_BOLD)
                    .stringWidth(propEditor.getLabel().getText()));
            lineIdx++;
        }
        
//        gbc.gridx = 0;
//        gbc.gridy++;
//        gbc.gridwidth = 2;
//        gbc.insets = new Insets(5, 3, 5, 3);
//        
//        JPanel group = new JPanel(new GridBagLayout());
//        group.setBorder(new TitledBorder("TEST"));
//        add(group, gbc);
//        
//        GridBagConstraints gbcGrouped = new GridBagConstraints();
//        gbcGrouped.insets = new Insets(2, 2, 2, 2);
//        gbcGrouped.gridwidth = 1;
//        gbcGrouped.fill = GridBagConstraints.HORIZONTAL;
//        
//        PropertyHolder propHolder = new PropertyHolder("test", new Bool(false), true);
//        IEditor propEditor = propHolder.getPropValue().editorFactory().newInstance(propHolder);
//        
//        gbcGrouped.gridx = 0;
//        gbcGrouped.gridy = 0;
//        gbcGrouped.weightx = 0;
//        group.add(propEditor.getLabel(), gbcGrouped);
//        gbcGrouped.gridx = 1;
//        gbcGrouped.gridy = 0;
//        gbcGrouped.weightx = 1;
//        Box container = new Box(BoxLayout.X_AXIS);
//        Box editor    = propEditor.getEditor();
//            
//        container.add(editor);
//        container.add(Box.createRigidArea(new Dimension(1, 28)));
//        group.add(container, gbcGrouped);
//        gbcGrouped.gridx = 0;
//        gbcGrouped.gridy++;
//        gbcGrouped.weightx = 0;
//        group.add(Box.createHorizontalStrut(Math.max(220, maxSize+30)), gbcGrouped);
//        
//        gbc.gridwidth = 1;
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        add(Box.createHorizontalStrut(Math.max(220, maxSize+30)), gbc);
        if (focusOrder.size() > 0) {
            setFocusCycleRoot(true);
            setFocusTraversalPolicyProvider(true);
            setFocusTraversalPolicy(new FocusPolicy(focusOrder));
        }
    }

}
