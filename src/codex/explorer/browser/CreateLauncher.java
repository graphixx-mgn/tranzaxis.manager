package codex.explorer.browser;

import codex.command.EntityCommand;
import codex.component.border.DashBorder;
import codex.component.border.RoundedBorder;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;

/**
 * Кнопка запуска диалога создания ярлыка панели быстрого доступа.
 */
final class CreateLauncher extends LaunchButton implements ActionListener, IPropertyChangeListener {
    
    private ParamModel     paramModel;
    private PropertyHolder commandHolder;
    private CommandChooser commandEditor;
    
    private DialogButton   confirmBtn = Dialog.Default.BTN_OK.newInstance();
    private DialogButton   declineBtn = Dialog.Default.BTN_CANCEL.newInstance();
    
    CreateLauncher() {
        super("", ImageUtils.getByPath("/images/plus.png"));
        setOpacity(0.3f);
        setBorder(new RoundedBorder(new DashBorder(Color.DARK_GRAY, 5, 1), 18));
        addActionListener(this);
    }
    
    @Override
    public void stateChanged(ChangeEvent event) {
        setOpacity(getModel().isRollover() ? 1 : 0.3f);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        EntityRef catalogRef = new EntityRef(Catalog.class, 
                (entity) -> {
                    return entity.getChildClass() != null && !entity.isLeaf();
                }
        );
        paramModel = new ParamModel();
        paramModel.addProperty("class", catalogRef, true);
        paramModel.addProperty("entity", new EntityRef(null), true);
        paramModel.addProperty("linkname", new Str(null), false);
        paramModel.addChangeListener(this);
        EditorPage content = new EditorPage(paramModel, EditorPage.Mode.Edit);
        
        // Инъекция параметра в страницу
        commandHolder = new PropertyHolder("command", new Str(""), true);
        GridBagConstraints gbc = ((GridBagLayout) content.getLayout()).getConstraints(content.getComponent(content.getComponentCount()-2));
        gbc.gridy++;
        ((GridBagLayout) content.getLayout()).setConstraints(content.getComponent(content.getComponentCount()-2), gbc);
        gbc = ((GridBagLayout) content.getLayout()).getConstraints(content.getComponent(content.getComponentCount()-3));
        gbc.gridy++;
        ((GridBagLayout) content.getLayout()).setConstraints(content.getComponent(content.getComponentCount()-3), gbc);
        
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.weightx = 0;
        commandEditor = new CommandChooser(commandHolder, null);
        content.add(commandEditor.getLabel(), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        content.add(commandEditor.getEditor(), gbc);
        commandHolder.addChangeListener(this);
        
        propertyChange(null, null, null);
        
        Dialog dialog = new Dialog(
            SwingUtilities.getWindowAncestor(this), 
            ImageUtils.getByPath("/images/linkage.png"), 
            Language.get("title"),
            content,
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    //TODO: insert to DB
                }
            },
            confirmBtn,
            declineBtn
        );
        dialog.setPreferredSize(new Dimension(550, dialog.getPreferredSize().height));
        dialog.setVisible(true);
    }

    @Override
    public void propertyChange(String name, Object oldValue, Object newValue) {
        paramModel.getEditor("linkname").setEditable(!commandHolder.isEmpty());
        paramModel.getEditor("entity").setEditable(paramModel.getValue("class") != null);
        commandEditor.setEditable(paramModel.getValue("entity") != null);
        confirmBtn.setEnabled(!commandHolder.isEmpty());
        
        if ("class".equals(name)) {
            Class entityClass = paramModel.getValue("class") != null ? ((Catalog) paramModel.getValue("class")).getChildClass() : null;
            EntityRef entityRef = new EntityRef(entityClass);
            paramModel.setValue("entity", entityRef);
        }
        if ("entity".equals(name)) {
            commandEditor.setEntity((Entity) paramModel.getValue("entity"));
        }
        if ("command".equals(name)) {
            if (!commandHolder.isEmpty()) {
                Entity entity = (Entity) paramModel.getValue("entity");
                String commandName = (String) commandHolder.getPropValue().getValue();
                for (EntityCommand command : entity.getCommands()) {
                    if (command.getName().equals(commandName)) {
                        paramModel.setValue("linkname", MessageFormat.format(
                                "{0} ({1})",
                                command.toString(),
                                entity
                        ));
                        break;
                    }
                }
            }
        }
    }
}
