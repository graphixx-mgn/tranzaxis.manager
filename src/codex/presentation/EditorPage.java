package codex.presentation;

import codex.command.EditorCommand;
import codex.component.border.RoundedBorder;
import codex.editor.IEditor;
import codex.model.AbstractModel;
import codex.model.Access;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

/**
 * Страница редактирования сущности, используется в составе презентации редактора
 * сущности {@link EditorPresentation}. Формирует панель с редакторами свойств 
 * модели сущности.
 */
public final class EditorPage extends JPanel {
    
    private final Map<String, JPanel> groupWidgets = new HashMap<>();
    
    /**
     * Конструктор страницы. 
     * @param model Редактируемая модель сущности.
     */
    public EditorPage(AbstractModel model) {
        super(new GridBagLayout());
        
        this.putClientProperty("GBC", new GridBagConstraints() {{
            insets = new Insets(5, 10, 5, 10);
            fill = GridBagConstraints.HORIZONTAL;
            gridwidth = 1;
            gridx = 0; 
            gridy = 1;
        }});
        
        final AtomicInteger maxSize = new AtomicInteger(0);
        final Vector<Component> focusOrder = new Vector<>();
        List<String> properties = model.getProperties(Access.Edit);
        for (String propName : properties) {
            IEditor propEditor = model.getEditor(propName);
            /* Нужно перерисовать компонент на случай если уже было обращение к нему
             * на ранней стадии инициализации сущности */
            ((JComponent) propEditor).updateUI();
            if (propEditor.getFocusTarget() != null && propEditor.isEditable()) {
                focusOrder.add(propEditor.getFocusTarget());
            }
            
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

            ((List<EditorCommand>) propEditor.getCommands()).stream().forEach((command) -> {
                editor.add((JComponent) command.getButton());
            });
            String propGroup = model.getPropertyGroup(propName);
            if (propGroup != null) {
                addEditorWidget(addGroupWidget(this, propGroup), propEditor.getLabel(), container);
            } else {
                addEditorWidget(this, propEditor.getLabel(), container);
            }
            
            maxSize.set(Math.max(
                    maxSize.get(), 
                    propEditor.getLabel()
                        .getFontMetrics(IEditor.FONT_BOLD)
                        .stringWidth(propEditor.getLabel().getText())
            ));
        }
        
        addEditorWidget(this, Box.createHorizontalStrut(Math.max(220, maxSize.get()+30)), null);
        groupWidgets.values().forEach((panel) -> {
            addEditorWidget(panel, Box.createHorizontalStrut(Math.max(220, maxSize.get()+30)), null);
        });
        
        if (focusOrder.size() > 0) {
            setFocusCycleRoot(true);
            setFocusTraversalPolicyProvider(true);
            setFocusTraversalPolicy(new FocusPolicy(focusOrder));
        }
    }
    
    private void addEditorWidget(JPanel container, Component label, Component editor) {
        GridBagConstraints gbc = (GridBagConstraints) container.getClientProperty("GBC");
        gbc.gridx   = 0;
        gbc.weightx = 0;
        container.add(label, gbc);
        if (editor != null) {
            gbc.gridx   = 1;
            gbc.weightx = 1;
            container.add(editor, gbc);
        }
        gbc.gridy++;
    }
    
    private JPanel addGroupWidget(JPanel container, String groupName) {
        if (!groupWidgets.containsKey(groupName)) {
            GridBagConstraints gbc = (GridBagConstraints) container.getClientProperty("GBC");
            Insets prevInsets = gbc.insets;
            gbc.insets = new Insets(3, 4, 3, 3);
            gbc.gridx  = 0;
            gbc.gridwidth = 2;

            JPanel group = new JPanel(new GridBagLayout());
            group.setBackground(Color.decode("#F5F5F5"));
            group.setOpaque(true);
            group.setBorder(new TitledBorder(
                    new RoundedBorder(new LineBorder(Color.LIGHT_GRAY, 1), 5),
                    groupName,
                    TitledBorder.CENTER,
                    TitledBorder.BELOW_TOP
            ));
            
            group.putClientProperty("GBC", new GridBagConstraints() {{
                insets = new Insets(7, 9, 3, 2);
                fill = GridBagConstraints.HORIZONTAL;
                gridwidth = 1;
                gridx = 0; 
                gridy = 1;
            }});
            container.add(group, gbc);
            groupWidgets.put(groupName, group);

            gbc.insets = prevInsets;
            gbc.gridwidth = 1;
            gbc.gridy++;
        }
        return groupWidgets.get(groupName);
    }

}
