package codex.launcher;

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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

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
        activator = (entities) -> {};
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
        Shortcut shortcut = (Shortcut) Entity.newInstance(Shortcut.class, null, PID);
        shortcut
                .setSection(
                        section != null ? section :
                        (ShortcutSection) Entity.newInstance(ShortcutSection.class, null, ShortcutSection.DEFAULT)
                )
                .setEntity(entity)
                .setCommand(command);
        try {
            shortcut.model.commit(true);
        } catch (Exception e) {}
        return shortcut;
    }
    
    /**
     * Вызывается для связывания виджета созданного ярлыка с виджетом секции.
     */
    void boundView(Shortcut shortcut) {
        // Do nothing
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        execute(null, null);
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
                            List<Object> values = CAS.readCatalogEntries(null, getEntityClass()).values().stream()
                                    .filter((PID) -> {
                                        return !PID.equals(ShortcutSection.DEFAULT);
                                    })
                                    .map((PID) -> {
                                        return Entity.newInstance(getEntityClass(), null, PID);
                                    }).collect(Collectors.toList());
                            return values;
                        }
                    };
                };
            }            
        };
        
        final EntityRef catalogRef = new EntityRef(Catalog.class, 
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
        
        final Str commandRef = new Str(null) {
            @Override
            public IEditorFactory editorFactory() {
                return (PropertyHolder propHolder) -> {
                    return new CommandChooser(propHolder, null);
                };
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
                        Class entityClass = newValue != null ? ((Catalog) newValue).getChildClass() : null;
                        EntityRef entityRef;
                        if (entityClass != null) {
                            entityRef = new EntityRef(entityClass, (entity) -> {
                                return entity.getID() != null && entity.getParent().equals(newValue);
                            });
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
            SwingUtilities.getWindowAncestor((Component) getButton()), 
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
                    Language.get(CreateSection.class.getSimpleName(),"title")
            );
        }

        @Override
        public void execute(PropertyHolder context) {}
    
    }
}