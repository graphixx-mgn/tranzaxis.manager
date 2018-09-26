package codex.launcher;

import codex.command.EntityCommand;
import codex.component.border.DashBorder;
import codex.component.border.RoundedBorder;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.editor.EntityRefTreeEditor;
import codex.editor.IEditorFactory;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;

/**
 * Кнопка запуска диалога создания ярлыка панели быстрого доступа.
 */
final class CreateShortcut extends LaunchButton implements ActionListener, IPropertyChangeListener {
    
    private final Dialog   paramDialog;
    private ParamModel     paramModel;
    
    private final DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
    private final DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();
    
    CreateShortcut() {
        super("", ImageUtils.getByPath("/images/plus.png"));
        setOpacity(0.5f);
        setBorder(new RoundedBorder(new DashBorder(Color.DARK_GRAY, 5, 1), 18));
        addActionListener(this);
        
        paramDialog = new Dialog(
            SwingUtilities.getWindowAncestor(this), 
            ImageUtils.getByPath("/images/linkage.png"), 
            Language.get("title"),
            new JPanel(),
            (event) -> {
                if (event.getID() == Dialog.OK) {
                    Entity entity = (Entity) paramModel.getValue("entity");
                    String commandName = (String) paramModel.getValue("command");
                    EntityCommand command = entity.getCommand(commandName);
                    String title = (String) paramModel.getValue("linkname");

                    Entity shortcut = Entity.newInstance(Shortcut.class, null, null);
                    shortcut.model.setValue(EntityModel.PID, title);
                    shortcut.model.setValue("class",   entity.getClass().getCanonicalName());
                    shortcut.model.setValue("entity",  entity);
                    shortcut.model.setValue("command", command.getName()); 
                    shortcut.model.commit();

                    LaunchShortcut launcher = new LaunchShortcut((Shortcut) shortcut);
                    Container panel = CreateShortcut.this.getParent();
                    panel.remove(CreateShortcut.this);
                    panel.add(launcher);
                    panel.add(CreateShortcut.this);
                    panel.revalidate();
                    panel.repaint();
                }
            },
            confirmBtn,
            declineBtn
        );
    }
    
    @Override
    public void stateChanged(ChangeEvent event) {
        setOpacity(getModel().isRollover() ? 1f : 0.5f);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        EntityRef catalogRef = new EntityRef(Catalog.class, 
                (entity) -> {
                    return 
                            entity.getChildClass() != null &&
                            Entity.newInstance(entity.getChildClass(), null, null).getCommands().stream().filter((command) -> {
                                return !command.getButton().isInactive() && command.getKind() == EntityCommand.Kind.Action;
                            }).count() > 0;
                }
        ) {
            @Override
            public IEditorFactory editorFactory() {
                return (PropertyHolder propHolder) -> {
                    return new EntityRefTreeEditor(propHolder);
                };
            }
        };
        Str commandRef = new Str(null) {
            @Override
            public IEditorFactory editorFactory() {
                return (PropertyHolder propHolder) -> {
                    return new CommandChooser(propHolder, null);
                };
            }
        };
        
        paramModel = new ParamModel();
        paramModel.addProperty("class",   catalogRef, true);
        paramModel.addProperty("entity",  new EntityRef(null), true);
        paramModel.addProperty("command", commandRef, true);  
        paramModel.addProperty("linkname", new Str(null), false);
        
        paramModel.addChangeListener(this);
        
        EditorPage content = new EditorPage(paramModel);
        paramDialog.setContent(content);
 
        propertyChange(null, null, null);
        paramDialog.pack();
        paramDialog.setPreferredSize(new Dimension(700, paramDialog.getPreferredSize().height));
        paramDialog.setVisible(true);
    }

    @Override
    public void propertyChange(String name, Object oldValue, Object newValue) {
        if (name != null) {
            switch (name) {
                case "class":
                    Class entityClass = newValue != null ? ((Catalog) newValue).getChildClass() : null;
                    EntityRef entityRef;
                    if (entityClass != null) {
                        entityRef = new EntityRef(entityClass, (entity) -> {
                            return entity.model.getID() != null && entity.getParent().equals(newValue);
                        });
                        entityRef.setValue(null);
                    } else {
                        entityRef = null;
                    }
                    paramModel.setValue("entity", entityRef);
                    break;
                    
                case "entity":
                    if (newValue instanceof EntityRef) {
                        ((CommandChooser) paramModel.getEditor("command")).setEntity(((EntityRef) newValue).getValue());
                    } else {
                        ((CommandChooser) paramModel.getEditor("command")).setEntity((Entity) newValue);
                    }
                    break;
                    
                case "command":
                    Entity entity = (Entity) paramModel.getValue("entity");
                    if (entity != null) {
                        String commandName = (String) paramModel.getValue("command");
                        if (commandName != null) {
                            EntityCommand command = entity.getCommand(commandName);
                            paramModel.setValue("linkname", MessageFormat.format("{0} ({1})", command.toString(), entity));
                        }
                    }
                    break;
            }
        }
        paramModel.getEditor("entity").setEditable(paramModel.getValue("class")    != null); 
        paramModel.getEditor("command").setEditable(paramModel.getValue("entity")  != null); 
        paramModel.getEditor("linkname").setEditable(paramModel.getValue("command") != null);
        confirmBtn.setEnabled(paramModel.getValue("command") != null);
    }
}
