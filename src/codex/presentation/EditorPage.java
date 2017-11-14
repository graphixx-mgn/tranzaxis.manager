package codex.presentation;

import codex.command.ICommand;
import codex.editor.IEditor;
import codex.model.Entity;
import codex.model.AbstractModel;
import codex.model.Access;
import codex.model.EntityModel;
import codex.property.PropertyHolder;
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

/**
 * Страница редактирования сущности, используется в составе презентации редактора
 * сущности {@link EditorPresentation}. Формирует панель с редакторами свойств 
 * модели сущности.
 */
final class EditorPage extends JPanel {
    
    enum Mode {
        Edit, Create
    }
    
    /**
     * Конструктор страницы. 
     * @param entity Редактируемая сущность.
     */
    EditorPage(Entity entity, Mode mode) {
        super(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;
        
        int lineIdx = 1, maxSize = 0;
        AbstractModel parentModel = null;
        if (entity.getParent() != null) {
            parentModel = ((Entity) entity.getParent()).model;
        }
        
        final Vector<Component> focusOrder = new Vector<>();
        List<String> properties = entity.model.getProperties(Access.Edit);
        if (mode.equals(Mode.Create)) {
            properties.add(0, EntityModel.PID);
        }
        for (String propName : properties) {
            gbc.gridx = 0; 
            gbc.gridy = lineIdx;
            gbc.weightx = 0;
            
            IEditor propEditor = entity.model.getEditor(propName);
            if (propEditor.getFocusTarget() != null) {
                focusOrder.add(propEditor.getFocusTarget());
            }
            add(propEditor.getLabel(), gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1;
            Box container = new Box(BoxLayout.X_AXIS);
            
            container.add(propEditor.getEditor());
            container.add(Box.createRigidArea(new Dimension(1, 28)));

            ((List<ICommand<PropertyHolder>>) propEditor.getCommands()).stream().forEach((command) -> {
                propEditor.getEditor().add((JComponent) command.getButton());
            });
            add(container, gbc);
            
            maxSize = Math.max(maxSize, propEditor.getLabel()
                    .getFontMetrics(IEditor.FONT_BOLD)
                    .stringWidth(propEditor.getLabel().getText()));
            lineIdx++;
        }
        add(Box.createHorizontalStrut(Math.max(220, maxSize+30)));
        if (focusOrder.size() > 0) {
            setFocusCycleRoot(true);
            setFocusTraversalPolicyProvider(true);
            setFocusTraversalPolicy(new FocusPolicy(focusOrder));
        }
    }
    
}
