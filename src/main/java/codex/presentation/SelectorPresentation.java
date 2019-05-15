package codex.presentation;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.OverrideProperty;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Презентация селектора сущности. Реализует как функциональность отображения и 
 * редактирования дочерних сущностей, так и обеспечивает работу команд по созданию
 * новых сущностей.
 */
public final class SelectorPresentation extends JPanel implements ListSelectionListener, INodeListener {
    
    private final static ImageIcon IMAGE_EDIT   = ImageUtils.resize(ImageUtils.getByPath("/images/edit.png"),  28, 28);
    private final static ImageIcon IMAGE_VIEW   = ImageUtils.resize(ImageUtils.getByPath("/images/view.png"),  28, 28);
    private final static ImageIcon IMAGE_CREATE = ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"),  28, 28);
    private final static ImageIcon IMAGE_CLONE  = ImageUtils.resize(ImageUtils.getByPath("/images/clone.png"), 28, 28);
    private final static ImageIcon IMAGE_REMOVE = ImageUtils.resize(ImageUtils.getByPath("/images/minus.png"), 28, 28);

    private final Class               entityClass;
    private final Entity              entity;
    private final SelectorTableModel  tableModel;
    private final JTable              table;

    private final CommandPanel commandPanel;
    private final Supplier<List<Entity>>      context;
    private final List<EntityCommand<Entity>> systemCommands  = new LinkedList<>();
    
    /**
     * Конструктор презентации. 
     */
    public SelectorPresentation(Entity entity) {
        super(new BorderLayout());
        this.entity = entity;
        entityClass = entity.getChildClass();

        Entity prototype = Entity.newPrototype(entityClass);

        EditEntity editEntity = new EditEntity();
        systemCommands.add(editEntity);
        if (entity.allowModifyChild()) {
            systemCommands.add(new CreateEntity());

            systemCommands.add(new CloneEntity());
            systemCommands.add(new DeleteEntity());
        }
        entity.getCommands().stream()
                .filter(command -> command.getKind() == EntityCommand.Kind.System)
                .forEach(command -> {
                    command.setContext(entity);
                    systemCommands.add(command);
                });

        commandPanel = new CommandPanel(systemCommands);
        add(commandPanel, BorderLayout.NORTH);
        
        tableModel = new SelectorTableModel(entity, prototype);
        table = new SelectorTable(tableModel);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        if (entity.allowModifyChild()) {
            table.setDragEnabled(true);
            table.setDropMode(DropMode.INSERT_ROWS);
            table.setTransferHandler(new TableTransferHandler(table));
        }

        context = () -> Arrays
                .stream(table.getSelectedRows())
                .boxed()
                .map(tableModel::getEntityAt)
                .collect(Collectors.toList());
        
        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setViewportView(table);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(0, 5, 5, 5), 
                new MatteBorder(1, 1, 1, 1, Color.GRAY)
        ));
        add(scrollPane, BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(this);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && editEntity.isActive()) {
                    editEntity.execute(tableModel.getEntityAt(table.getSelectedRow()), null);
                }
            }
        });
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                e.consume();
                JComponent c = (JComponent) e.getSource();
                TransferHandler handler = c.getTransferHandler();
                handler.exportAsDrag(c, e, TransferHandler.MOVE);
                table.getSelectionModel().setValueIsAdjusting(false);
            }
        });
        
        entity.addNodeListener(new INodeListener() {
            @Override
            public void childDeleted(INode parentNode, INode childNode, int index) {
                tableModel.removeRow(index);
                // Если родительская сущность не удалена
                if (entity.getParent() != null) {
                    if (index < tableModel.getRowCount()) {
                        table.getSelectionModel().setSelectionInterval(index, index);
                    } else if (index == tableModel.getRowCount()) {
                        table.getSelectionModel().setSelectionInterval(index - 1, index - 1);
                    } else {
                        table.getSelectionModel().clearSelection();
                    }
                }
            }
        });
        refresh();
    }

    /**
     * Обновление презентации и панели команд.
     */
    public final void refresh() {
        updateCommands();
        activateCommands();
    }

    public Class getEntityClass() {
        return entityClass;
    }

    private List<EntityCommand<Entity>> getContextCommands(List<Entity> context) {
        final List<EntityCommand<Entity>> commands = new LinkedList<>();
        if (context.size() == 1) {
            commands.addAll(context.get(0).getCommands());
        } else if (!context.isEmpty()) {
            final List<String> commandIds = context.get(0).getCommands().stream().map(EntityCommand::getName).collect(Collectors.toList());
            context.forEach(entity -> commandIds.retainAll(
                    entity.getCommands().stream().map(EntityCommand::getName).collect(Collectors.toList())
            ));
            commands.addAll(
                context.get(0).getCommands().stream()
                    .filter(command -> commandIds.contains(command.getName()))
                    .collect(Collectors.toList())
            );
        }
        return commands;
    }

    private void updateCommands() {
        commandPanel.setContextCommands(getContextCommands(this.context.get()));
    }

    private void activateCommands() {
        List<Entity> context = this.context.get();
        Stream.concat(
                systemCommands.stream(),
                getContextCommands(this.context.get()).stream()
        ).forEach(command -> {
            if (command.getKind() != EntityCommand.Kind.System) {
                context.forEach((contextItem) -> contextItem.removeNodeListener(this));
                command.setContext(context);
                context.forEach((contextItem) -> contextItem.addNodeListener(this));
            } else {
                command.setContext(entity);
            }
        });
    }

    @Override
    public void valueChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            refresh();
        }
    }
    
    @Override
    public void childChanged(INode node) {
        activateCommands();
    }

    class CreateEntity extends EntityCommand<Entity> {
    
        CreateEntity() {
            super(
                    "create", null,
                    IMAGE_CREATE, 
                    Language.get(SelectorPresentation.class, "command@create"),
                    null,
                    KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_MASK)
            );
        }

        @Override
        public Kind getKind() {
            return Kind.System;
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            Entity newEntity = Entity.newInstance(entityClass, Entity.findOwner(context), null);
                    
            EntityModel parentModel = context.model;
            EntityModel childModel  = newEntity.model;
            
            List<String> overrideProps = parentModel.getProperties(Access.Edit)
                    .stream()
                    .filter(
                            propName -> 
                                    childModel.hasProperty(propName) && 
                                    !childModel.isPropertyDynamic(propName) &&
                                    !EntityModel.SYSPROPS.contains(propName) &&
                                    parentModel.getPropertyType(propName) == childModel.getPropertyType(propName)
                    ).collect(Collectors.toList());
            if (!overrideProps.isEmpty()) {
                overrideProps.forEach((propName) -> {
                    if (!childModel.getEditor(propName).getCommands().stream().anyMatch((command) -> {
                        return command instanceof OverrideProperty;
                    })) {
                        childModel.getEditor(propName).addCommand(new OverrideProperty(parentModel, childModel, propName));
                    }
                });
            }
 
            DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
            DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();
            
            Dialog editor = new Dialog(
                    SwingUtilities.getWindowAncestor(SelectorPresentation.this),
                    ImageUtils.getByPath("/images/plus.png"),
                    Language.get(SelectorPresentation.class, "creator@title"),
                    new JPanel(new BorderLayout()) {{
                        add(newEntity.getEditorPage());
                        setBorder(new CompoundBorder(
                                new EmptyBorder(10, 5, 5, 5),
                                new TitledBorder(
                                        new LineBorder(Color.LIGHT_GRAY, 1),
                                        Language.get(SelectorPresentation.class, "creator@desc")
                                )
                        ));
                    }},
                    (event) -> {
                        if (event.getID() == Dialog.OK) {
                            try {
                                newEntity.model.commit(true);
                                newEntity.setTitle(newEntity.getPID());
                                context.insert(newEntity);
                                tableModel.addRow(
                                        newEntity.model.getProperties(Access.Select).stream().map((propName) -> {
                                            return newEntity.model.getValue(propName);
                                        }).toArray()
                                );
                                newEntity.model.addModelListener(tableModel);
                                newEntity.model.addChangeListener((name, oldValue, newValue) -> {
                                    List<String> selectorProps = childModel.getProperties(Access.Select);
                                    if (newEntity.model.isPropertyDynamic(name) && selectorProps.contains(name)) {
                                        final int entityIdx = entity.getIndex(newEntity);
                                        tableModel.setValueAt(newValue, entityIdx, selectorProps.indexOf(name));
                                    }
                                });
                                newEntity.addNodeListener(new INodeListener() {
                                    @Override
                                    public void childChanged(INode node) {
                                        int rowCount = tableModel.getRowCount();
                                        for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
                                            if (tableModel.getEntityAt(rowIdx).model.equals(childModel)) {
                                                tableModel.fireTableRowsUpdated(rowIdx, rowIdx);
                                                break;
                                            }
                                        }
                                    }
                                });

                                table.getSelectionModel().setSelectionInterval(
                                        tableModel.getRowCount() - 1, 
                                        tableModel.getRowCount() - 1
                                );
                            } catch (Exception e) {
                                newEntity.model.rollback();
                            }
                        }
                    },
                    confirmBtn, declineBtn
            ) {
                {
                    // Перекрытие обработчика кнопок
                    Function<DialogButton, ActionListener> defaultHandler = handler;
                    handler = (button) -> {
                        return (event) -> {
                            if (event.getID() != Dialog.OK || newEntity.getInvalidProperties().isEmpty()) {
                                defaultHandler.apply(button).actionPerformed(event);
                            }
                        }; 
                    };
                }
                
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(700, super.getPreferredSize().height);
                }
            };
            
            newEntity.model.getProperties(Access.Edit).stream()
                    .map((propName) -> {
                        return newEntity.model.getEditor(propName);
                    }).forEach((propEditor) -> {
                        propEditor.getEditor().addComponentListener(new ComponentAdapter() {
                            @Override
                            public void componentHidden(ComponentEvent e) {
                                editor.pack();
                            }

                            @Override
                            public void componentShown(ComponentEvent e) {
                                editor.pack();
                            }
                        });
                    });
            
            editor.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            editor.setResizable(false);
            editor.setVisible(true);
        }

    }
    
    class CloneEntity extends EntityCommand<Entity> {
        
        CloneEntity() {
            super(
                    "clone", null,
                    IMAGE_CLONE, 
                    Language.get(SelectorPresentation.class, "command@clone"),
                    (entity) -> true,
                    KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK)
            );
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            Entity newEntity = Entity.newInstance(context.getParent().getChildClass(), Entity.findOwner(context.getParent()), null);
            
            EntityModel parentModel = ((Entity) context.getParent()).model;
            EntityModel childModel  = newEntity.model;
            
            List<String> overridableProps = parentModel.getProperties(Access.Edit)
                    .stream()
                    .filter(
                            propName -> 
                                    childModel.hasProperty(propName) && 
                                    !childModel.isPropertyDynamic(propName) &&
                                    !EntityModel.SYSPROPS.contains(propName) &&
                                    parentModel.getPropertyType(propName) == childModel.getPropertyType(propName)
                    ).collect(Collectors.toList());
            List<String> overriddenProps = context.getOverride();

            context.model.getProperties(Access.Edit).forEach((propName) -> {
                if ("PID".equals(propName)) {
                    newEntity.model.setValue(propName, context.model.getValue(propName)+" (1)");
                } else {
                    if (!newEntity.model.isPropertyDynamic(propName)) {
                        if (!(overridableProps.contains(propName) && (overriddenProps == null || !overriddenProps.contains(propName)))) {
                            newEntity.model.setValue(propName, context.model.getValue(propName));
                        }
                    }
                }
            });
            newEntity.setOverride(overriddenProps);

            if (!overridableProps.isEmpty()) {
                overridableProps.forEach((propName) -> {
                    if (!childModel.getEditor(propName).getCommands().stream().anyMatch((command) -> {
                        return command instanceof OverrideProperty;
                    })) {
                        childModel.getEditor(propName).addCommand(new OverrideProperty(parentModel, childModel, propName));
                    }
                });
            }
            
            DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
            DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();
            
            Dialog editor = new Dialog(
                    SwingUtilities.getWindowAncestor(SelectorPresentation.this),
                    ImageUtils.getByPath("/images/clone.png"),
                    Language.get(SelectorPresentation.class, "copier@title"),
                    new JPanel(new BorderLayout()) {{
                        add(newEntity.getEditorPage());
                        setBorder(new CompoundBorder(
                                new EmptyBorder(10, 5, 5, 5),
                                new TitledBorder(
                                        new LineBorder(Color.LIGHT_GRAY, 1),
                                        Language.get(SelectorPresentation.class, "copier@desc")
                                )
                        ));
                    }},
                    (event) -> {
                        if (event.getID() == Dialog.OK) {
                            try {
                                newEntity.model.commit(true);
                                newEntity.setTitle(newEntity.getPID());
                                context.getParent().insert(newEntity);

                                tableModel.addRow(
                                        newEntity.model.getProperties(Access.Select).stream().map((propName) -> {
                                            return newEntity.model.getValue(propName);
                                        }).toArray()
                                );
                                newEntity.model.addModelListener(tableModel);
                                newEntity.model.addChangeListener((name, oldValue, newValue) -> {
                                    List<String> selectorProps = childModel.getProperties(Access.Select);
                                    if (newEntity.model.isPropertyDynamic(name) && selectorProps.contains(name)) {
                                        final int entityIdx = entity.getIndex(newEntity);
                                        tableModel.setValueAt(newValue, entityIdx, selectorProps.indexOf(name));
                                    }
                                });
                                newEntity.addNodeListener(new INodeListener() {
                                    @Override
                                    public void childChanged(INode node) {
                                        int rowCount = tableModel.getRowCount();
                                        for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
                                            if (tableModel.getEntityAt(rowIdx).model.equals(childModel)) {
                                                tableModel.fireTableRowsUpdated(rowIdx, rowIdx);
                                                break;
                                            }
                                        }
                                    }
                                });
                                table.getSelectionModel().setSelectionInterval(
                                        tableModel.getRowCount() - 1, 
                                        tableModel.getRowCount() - 1
                                );
                            } catch (Exception e) {
                                newEntity.model.rollback();
                            }
                        }
                    },
                    confirmBtn, declineBtn
            ) {
                {
                    // Перекрытие обработчика кнопок
                    Function<DialogButton, ActionListener> defaultHandler = handler;
                    handler = (button) -> {
                        return (event) -> {
                            if (event.getID() != Dialog.OK || newEntity.getInvalidProperties().isEmpty()) {
                                defaultHandler.apply(button).actionPerformed(event);
                            }
                        }; 
                    };
                }
                
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(700, super.getPreferredSize().height);
                }
            };
            
            newEntity.model.getProperties(Access.Edit).stream()
                    .map((propName) -> {
                        return newEntity.model.getEditor(propName);
                    }).forEach((propEditor) -> {
                        propEditor.getEditor().addComponentListener(new ComponentAdapter() {
                            @Override
                            public void componentHidden(ComponentEvent e) {
                                editor.pack();
                            }

                            @Override
                            public void componentShown(ComponentEvent e) {
                                editor.pack();
                            }
                        });
                    });
            
            editor.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            editor.setResizable(false);
            editor.setVisible(true);
        }

    }
    
    class EditEntity extends EntityCommand<Entity> {
    
        EditEntity() {
            super(
                    "edit", null,
                    IMAGE_EDIT, 
                    Language.get(SelectorPresentation.class, "command@edit"),
                    (entity) -> true
            );
            activator = entities -> {
                if (entities != null && entities.size() > 0 && !(entities.size() > 1 && !multiContextAllowed())) {
                    boolean allDisabled = entities.get(0).model.getProperties(Access.Edit).stream().allMatch((name) -> {
                        return !entities.get(0).model.getEditor(name).isEditable();
                    });

                    boolean hasProps = !entities.get(0).model.getProperties(Access.Edit).isEmpty();
                    return new CommandStatus(hasProps, allDisabled || entities.get(0).islocked() ? IMAGE_VIEW : IMAGE_EDIT);
                } else {
                    return new CommandStatus(false, entity.allowModifyChild() ? IMAGE_EDIT : IMAGE_VIEW);
                }
            };
            activate();
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            boolean allDisabled = context.model.getProperties(Access.Edit).stream().noneMatch((name) -> context.model.getEditor(name).isEditable());
            
            Dialog editor = new Dialog(
                    SwingUtilities.getWindowAncestor(SelectorPresentation.this),
                    allDisabled ? IMAGE_VIEW : IMAGE_EDIT,
                    Language.get(SelectorPresentation.class, allDisabled ? "viewer@title" : "editor@title"),
                    new JPanel(new BorderLayout()) {{
                        add(context.getEditorPage());
                        setBorder(new CompoundBorder(
                                new EmptyBorder(10, 5, 5, 5),
                                new TitledBorder(new LineBorder(Color.LIGHT_GRAY, 1), context.toString())
                        ));
                    }},
                    (event) -> {
                        if (event.getID() == Dialog.OK) {
                            if (context.model.hasChanges()) {
                                try {
                                    context.model.commit(true);
                                } catch (Exception e) {
                                    context.model.rollback();
                                }
                            }
                        } else {
                            if (context.model.hasChanges()) {
                                context.model.rollback();
                            }
                        }
                    },
                    allDisabled ?
                            new DialogButton[] { Dialog.Default.BTN_CLOSE.newInstance() } :
                            new DialogButton[] { Dialog.Default.BTN_OK.newInstance(), Dialog.Default.BTN_CANCEL.newInstance() }
            ) {{
                    // Перекрытие обработчика кнопок
                    Function<DialogButton, ActionListener> defaultHandler = handler;
                    handler = (button) -> {
                        return (event) -> {
                            if (event.getID() != Dialog.OK || context.getInvalidProperties().isEmpty()) {
                                defaultHandler.apply(button).actionPerformed(event);
                            }
                        }; 
                    };
                }
                
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(700, super.getPreferredSize().height);
                }
            };
            
            context.model.getProperties(Access.Edit).stream()
                    .map((propName) -> {
                        return context.model.getEditor(propName);
                    }).forEach((propEditor) -> {
                        propEditor.getEditor().addComponentListener(new ComponentAdapter() {
                            @Override
                            public void componentHidden(ComponentEvent e) {
                                editor.pack();
                            }

                            @Override
                            public void componentShown(ComponentEvent e) {
                                editor.pack();
                            }
                        });
                    });
            
            editor.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            editor.setResizable(false);
            editor.setVisible(true);
        }

    }
    
    class DeleteEntity extends EntityCommand<Entity> {
    
        DeleteEntity() {
            super(
                    "delete", null,
                    IMAGE_REMOVE, 
                    Language.get(SelectorPresentation.class, "command@delete"),
                    (entity) -> true,
                    KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)
            );
        }

        @Override
        public String acquireConfirmation() {
            String message;
            if (getContext().size() == 1) {
                message = MessageFormat.format(
                        Language.get(SelectorPresentation.class, "confirm@del.single"),
                        getContext().get(0)
                );
            } else {
                StringBuilder msgBuilder = new StringBuilder(
                        Language.get(SelectorPresentation.class, "confirm@del.range")
                );
                getContext().forEach((entity) -> {
                    msgBuilder.append("<br>&bull;&nbsp;<b>").append(entity.toString()).append("</b>");
                });
                message = msgBuilder.toString();
            }
            return message;
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            context.getParent().delete(context);
        }

        @Override
        public boolean multiContextAllowed() {
            return true;
        }

    }

}
