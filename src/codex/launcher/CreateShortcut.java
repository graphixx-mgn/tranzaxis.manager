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
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Реализация команды создания нового ярлыка.
 */
class CreateShortcut extends EntityCommand {
        
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
    private Shortcut newShortcut(String PID, Entity section, Entity entity, String command) {
        Entity shortcut = Entity.newInstance(Shortcut.class, null, PID);
        shortcut.model.setValue("section", 
                section != null ? section :
                Entity.newInstance(ShortcutSection.class, null, ShortcutSection.DEFAULT)
        );
        shortcut.model.setValue("class",   entity.getClass().getCanonicalName());
        shortcut.model.setValue("entity",  entity);
        shortcut.model.setValue("command", command);
        shortcut.model.commit();
        return (Shortcut) shortcut;
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
        paramModel.addProperty("section",  sectionRef, false);
        paramModel.addProperty("catalog",  catalogRef, true);
        paramModel.addProperty("entity",   new EntityRef(null), true);
        paramModel.addProperty("command",  commandRef, true);
        paramModel.addProperty("linkname", new Str(null), true);
        
        paramModel.addChangeListener((String name, Object oldValue, Object newValue) -> {
            if (name != null) {
                switch (name) {
                    case "catalog":
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
                        CommandChooser commandEditor = (CommandChooser) paramModel.getEditor("command");
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
                        
                    case "command":
                        Entity entity = (Entity) paramModel.getValue("entity");
                        if (entity != null && newValue != null) {
                            String commandName = (String) paramModel.getValue("command");
                            if (commandName != null) {
                                EntityCommand command = entity.getCommand(commandName);
                                paramModel.setValue("linkname", MessageFormat.format("{0} ({1})", command.toString(), entity));
                            }
                        } else {
                            paramModel.setValue("linkname", null);
                        }
                        break;
                }
            }
            
            paramModel.getEditor("entity").setEditable(paramModel.getValue("catalog")   != null); 
            paramModel.getEditor("command").setEditable(paramModel.getValue("entity")   != null); 
            paramModel.getEditor("linkname").setEditable(paramModel.getValue("command") != null);
            confirmBtn.setEnabled(paramModel.getValue("command") != null);
        });
        
        AbstractEditor sectionEditor = (AbstractEditor) paramModel.getEditor("section");
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
            SwingUtilities.getWindowAncestor((JComponent) getButton()), 
            ImageUtils.getByPath("/images/linkage.png"), 
            Language.get("title"),
            new JPanel(),
            (event) -> {
                if (event.getID() == Dialog.OK) {
                    boundView(newShortcut(
                        (String) paramModel.getValue("linkname"), 
                        (Entity) paramModel.getValue("section"), 
                        (Entity) paramModel.getValue("entity"), 
                        (String) paramModel.getValue("command")
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

        public AddSection() {
            super(
                    ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 18, 18), 
                    Language.get(CreateSection.class.getSimpleName(),"title")
            );
        }

        @Override
        public void execute(PropertyHolder context) {}
    
    }
}