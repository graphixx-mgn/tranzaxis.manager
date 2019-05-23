package codex.launcher;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.AbstractEditor;
import codex.editor.EntityRefEditor;
import codex.editor.EntityRefTreeEditor;
import codex.editor.IEditorFactory;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Реализация команды создания нового ярлыка.
 */
class CreateShortcut extends EntityCommand<Entity> {
    
    private static final String PARAM_SECTION  = "section";
    private static final String PARAM_CATALOG  = "catalog";
    private static final String PARAM_ENTITY   = "entity";
    private static final String PARAM_COMMAND  = "command";
    private static final String PARAM_LINKNAME = "linkname";
        
    private final static IConfigStoreService CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final CreateSection proxyCommand;
    
    /**
     * Конструктор команды.
     * @param command Ссылка на команду создания секции, которая вызывается из редактора.
     */
    CreateShortcut(CreateSection command) {
        super(
                "new_shortcut", null,
                ImageUtils.getByPath("/images/linkage.png"), 
                Language.get("title"),
                null
        );
        activator = entities -> new CommandStatus(true);
        this.proxyCommand = command;
    }
    
    /**
     * Построение ярлыка на основе введенных данных.
     * @param PID Имя ярлыка.
     * @param section Секция.
     * @param entity Ссылка на сущность
     * @param command Имя конманды.
     */
    private Shortcut newShortcut(String PID, ShortcutSection section, Entity entity, String command) {
        Shortcut shortcut = Entity.newInstance(Shortcut.class, null, PID);
        shortcut
                .setSection(
                        section != null ? section :
                        Entity.newInstance(ShortcutSection.class, null, ShortcutSection.DEFAULT)
                )
                .setEntity(entity)
                .setCommand(command);
        try {
            shortcut.model.commit(true);
        } catch (Exception e) {
            //
        }
        return shortcut;
    }
    
    /**
     * Вызывается для связывания виджета созданного ярлыка с виджетом секции.
     */
    void boundView(Shortcut shortcut) {
        // Do nothing
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        final DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();

        final EntityRef sectionRef = new EntityRef(ShortcutSection.class) {
            @Override
            public IEditorFactory editorFactory() {
                return (PropertyHolder propHolder) -> {
                    return new EntityRefEditor(propHolder) {
                        @Override
                        protected List<Object> getValues() {
                            return CAS.readCatalogEntries(null, getEntityClass()).values().stream()
                                    .filter((PID) -> !PID.equals(ShortcutSection.DEFAULT))
                                    .map((PID) -> Entity.newInstance(getEntityClass(), null, PID))
                                    .collect(Collectors.toList());
                        }
                    };
                };
            }            
        };
        
        final EntityRef catalogRef = new EntityRef(Catalog.class, 
                (entity) ->
                        entity.getChildClass() != null &&
                        Entity.newInstance(entity.getChildClass(), null, null)
                               .getCommands().stream()
                               .anyMatch((command) -> command.getKind() == Kind.Action)
        ) {
            @Override
            public IEditorFactory editorFactory() {
                return EntityRefTreeEditor::new;
            }
        };
        
        final Str commandRef = new Str(null) {
            @Override
            public IEditorFactory editorFactory() {
                return CommandChooser::new;
            }
        };

        ParamModel paramModel = new ParamModel();
        paramModel.addProperty(PARAM_SECTION,  sectionRef, false);
        paramModel.addProperty(PARAM_CATALOG,  catalogRef, true);
        paramModel.addProperty(PARAM_ENTITY,   new EntityRef(null), true);
        paramModel.addProperty(PARAM_COMMAND,  commandRef, true);
        paramModel.addProperty(PARAM_LINKNAME, new Str(null), true);
        
        paramModel.addChangeListener((String name, Object oldValue, Object newValue) -> {
            if (name != null) {
                switch (name) {
                    case PARAM_CATALOG:
                        Class<? extends Entity> entityClass = newValue != null ? ((Catalog) newValue).getChildClass() : null;
                        EntityRef entityRef;
                        if (entityClass != null) {
                            entityRef = new EntityRef(entityClass, (entity) -> entity.getID() != null && entity.getParent().equals(newValue));
                            entityRef.setValue(null);
                        } else {
                            entityRef = null;
                        }
                        paramModel.setValue(PARAM_ENTITY, entityRef);
                        break;

                    case PARAM_ENTITY:
                        CommandChooser commandEditor = (CommandChooser) paramModel.getEditor(PARAM_COMMAND);
                        if (newValue != null) {
                            if (newValue instanceof EntityRef) {
                                commandEditor.setEntity(((EntityRef) newValue).getValue());
                            } else {
                                commandEditor.setEntity((Entity) newValue);
                            }
                        } else {
                            commandEditor.setEntity(null);
                        }
                        break;
                        
                    case PARAM_COMMAND:
                        Entity entity = (Entity) paramModel.getValue(PARAM_ENTITY);
                        if (entity != null && newValue != null) {
                            String commandName = (String) paramModel.getValue(PARAM_COMMAND);
                            if (commandName != null) {
                                EntityCommand command = entity.getCommand(commandName);
                                paramModel.setValue(PARAM_LINKNAME, MessageFormat.format("{0} ({1})", command.toString(), entity));
                            }
                        } else {
                            paramModel.setValue(PARAM_LINKNAME, null);
                        }
                        break;
                }
            }
            
            paramModel.getEditor(PARAM_ENTITY).setEditable(paramModel.getValue(PARAM_CATALOG) != null);
            paramModel.getEditor(PARAM_COMMAND).setEditable(paramModel.getValue(PARAM_ENTITY) != null);
            paramModel.getEditor(PARAM_LINKNAME).setEditable(paramModel.getValue(PARAM_COMMAND)  != null);
            confirmBtn.setEnabled(paramModel.getValue(PARAM_COMMAND) != null);
        });
        
        AbstractEditor sectionEditor = (AbstractEditor) paramModel.getEditor(PARAM_SECTION);
        sectionEditor.addCommand(new AddSection() {
            @Override
            public void execute(PropertyHolder context) {
                new CreateSection() {
                    @Override
                    void boundView(ShortcutSection section) {
                        proxyCommand.boundView(section);
                        context.setValue(section);

                    }
                }.execute(null, null);
            }
        });

        final Dialog paramDialog = new Dialog(
            FocusManager.getCurrentManager().getActiveWindow(),
            ImageUtils.getByPath("/images/linkage.png"),
            Language.get("title"),
            new JPanel(),
            (event) -> {
                if (event.getID() == Dialog.OK) {
                    boundView(newShortcut(
                        (String) paramModel.getValue(PARAM_LINKNAME),
                        (ShortcutSection) paramModel.getValue(PARAM_SECTION),
                        (Entity) paramModel.getValue(PARAM_ENTITY),
                        (String) paramModel.getValue(PARAM_COMMAND)
                    ));
                }
            },
            confirmBtn
        );

        EditorPage content = new EditorPage(paramModel);
        paramDialog.setContent(content);
        paramModel.propertyChange(null, null, null);
        
        paramDialog.pack();
        paramDialog.setPreferredSize(new Dimension(700, paramDialog.getPreferredSize().height));
        paramDialog.setVisible(true);
    }
    
    
    private class AddSection extends EditorCommand {

        private AddSection() {
            super(
                    ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 18, 18),
                    Language.get(CreateSection.class,"title")
            );
        }

        @Override
        public void execute(PropertyHolder context) {}
    
    }
}