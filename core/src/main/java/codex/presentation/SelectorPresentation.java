package codex.presentation;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.browser.BrowseMode;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.log.Logger;
import codex.model.*;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Презентация селектора сущности. Реализует как функциональность отображения и 
 * редактирования дочерних сущностей, так и обеспечивает работу команд по созданию
 * новых сущностей.
 */
public final class SelectorPresentation extends JPanel implements ListSelectionListener, INodeListener {
    
    private final static ImageIcon IMAGE_EDIT   = ImageUtils.getByPath("/images/edit.png");
    private final static ImageIcon IMAGE_VIEW   = ImageUtils.getByPath("/images/view.png");
    private final static ImageIcon IMAGE_CREATE = ImageUtils.getByPath("/images/plus.png");
    private final static ImageIcon IMAGE_CLONE  = ImageUtils.getByPath("/images/clone.png");
    private final static ImageIcon IMAGE_REMOVE = ImageUtils.getByPath("/images/minus.png");

    private final Class<? extends Entity> entityClass;
    private final Entity                  entity;
    private final SelectorTableModel      tableModel;
    private final JTable                  table;
    private final CommandPanel            commandPanel;

    private final Supplier<List<Entity>>  context;
    private final Map<EntityCommand<Entity>,  CommandContextKind> systemCommands  = new LinkedHashMap<>();
    private final Map<EntityCommand<Entity>,  CommandContextKind> contextCommands = new LinkedHashMap<>();
    
    /**
     * Конструктор презентации. 
     */
    public SelectorPresentation(Entity entity) {
        super(new BorderLayout());
        this.entity = entity;
        entityClass = entity.getChildClass();

        commandPanel = new CommandPanel(Collections.emptyList());
        add(commandPanel, BorderLayout.NORTH);
        
        tableModel = new SelectorTableModel(entity);
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
                .map(tableModel::getEntityForRow)
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

            @Override
            public void childInserted(INode parentNode, INode childNode) {
                Entity newEntity = (Entity) childNode;
                tableModel.addEntity(newEntity);
                newEntity.model.addModelListener(tableModel);
                newEntity.model.addChangeListener((name, oldValue, newValue) -> {
                    OptionalInt propShown = IntStream.range(1, tableModel.getColumnCount())
                            .filter(col -> tableModel.getPropertyForColumn(col).equals(name))
                            .findFirst();
                    if (newEntity.model.isPropertyDynamic(name) && propShown.isPresent()) {
                        final int entityIdx = entity.getIndex(newEntity);
                        tableModel.setValueAt(newValue, entityIdx, propShown.getAsInt());
                    }
                });
                newEntity.addNodeListener(new INodeListener() {
                    @Override
                    public void childChanged(INode node) {
                        int rowCount = tableModel.getRowCount();
                        for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
                            if (tableModel.getEntityForRow(rowIdx).model.equals(newEntity.model)) {
                                tableModel.fireTableRowsUpdated(rowIdx, rowIdx);
                                break;
                            }
                        }
                    }
                });
            }
        });
        SwingUtilities.invokeLater(this::refresh);
    }

    /**
     * Обновление презентации и панели команд.
     */
    public final void refresh() {
        updateCommands();
        activateCommands();
    }

    private void updateCommands() {
        Map<EntityCommand<Entity>, CommandContextKind> sysCommands = getSystemCommands();
        boolean updateRequired = !(
                sysCommands.keySet().containsAll(systemCommands.keySet()) &&
                        systemCommands.keySet().containsAll(sysCommands.keySet())
        );
        if (updateRequired) {
            systemCommands.clear();
            systemCommands.putAll(sysCommands);
            commandPanel.setSystemCommands(systemCommands.keySet());
        }

        contextCommands.clear();
        getContextCommands().entrySet().stream()
                .filter(commandEntry -> commandEntry.getKey().getKind() != EntityCommand.Kind.System)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new
                )).forEach(contextCommands::put);
        commandPanel.setContextCommands(contextCommands.keySet());
    }

    private void activateCommands() {
        final List<Entity> context = this.context.get();
        systemCommands.forEach((command, ctxKind) -> {
            switch (ctxKind) {
                case Parent:
                    command.setContext(entity); break;
                default:
                    context.forEach((contextItem) -> contextItem.removeNodeListener(this));
                    command.setContext(context);
                    context.forEach((contextItem) -> contextItem.addNodeListener(this));
            }
        });

        context.forEach(ctxEntity -> ctxEntity.removeNodeListener(this));
        contextCommands.forEach((command, ctxKind) -> {
            if (PolyMorph.class.isAssignableFrom(entityClass)) {
                command.setContext(context.stream()
                        .map(ctxEntity -> ((PolyMorph) ctxEntity).getImplementation())
                        .collect(Collectors.toList())
                );
            } else {
                command.setContext(context);
            }
        });
        context.forEach(ctxEntity -> ctxEntity.addNodeListener(this));
    }

    private Map<EntityCommand<Entity>, CommandContextKind> getSystemCommands() {
        final Map<EntityCommand<Entity>, CommandContextKind> commands = new LinkedHashMap<>();

        if (true) {
            final EntityCommand<Entity> editCmd   = findCommand(systemCommands.keySet(), EditEntity.class, new EditEntity());
            commands.put(editCmd, CommandContextKind.Child);
        }
        if (canCreateEntities()) {
            final EntityCommand<Entity> createCmd = findCommand(systemCommands.keySet(), CreateEntity.class, new CreateEntity());
            commands.put(createCmd, CommandContextKind.Parent);
        }
        if (canCreateEntities()) {
            final EntityCommand<Entity> cloneCmd  = findCommand(systemCommands.keySet(), CloneEntity.class, new CloneEntity());
            commands.put(cloneCmd, CommandContextKind.Child);
        }
        if (canDeleteEntities()) {
            final EntityCommand<Entity> deleteCmd = findCommand(systemCommands.keySet(), DeleteEntity.class, new DeleteEntity());
            commands.put(deleteCmd, CommandContextKind.Child);
        }
        getContextCommands().keySet().stream()
                .filter(command -> command.getKind() == EntityCommand.Kind.System)
                .forEach(command -> commands.put(command, CommandContextKind.Child));
        return commands;
    }

    private Map<EntityCommand<Entity>, CommandContextKind> getContextCommands() {
        final List<Entity> context    = this.context.get();
        return context.stream()
                .map(Entity::getCommands)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(
                        EntityCommand::getName,
                        LinkedHashMap::new,
                        Collectors.toList())
                )
                .entrySet().stream().filter(cmdGroup -> cmdGroup.getValue().size() == context.size())
                .collect(Collectors.toMap(
                        cmdGroup -> cmdGroup.getValue().get(0),
                        cmdGroup -> CommandContextKind.Child,
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new
                ));
    }

    private EntityCommand<Entity> findCommand(
            Collection<EntityCommand<Entity>> commands,
            Class<? extends EntityCommand<Entity>> commandClass,
            EntityCommand<Entity> defCommand
    ) {
        return commands.stream().filter(command -> command.getClass().equals(commandClass)).findFirst().orElse(defCommand);
    }

    private boolean canCreateEntities() {
        return entity.allowModifyChild();
    }

    private boolean canDeleteEntities() {
        return entity.allowModifyChild() || !getCleanerMethod(entity.getClass()).getDeclaringClass().equals(Entity.class);
    }

    @Override
    public void valueChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            refresh();
        }
    }

    private List<String> getOverrideProps(EntityModel parentModel, EntityModel childModel) {
        return parentModel.getProperties(Access.Edit).stream()
                .filter(
                        propName ->
                                childModel.hasProperty(propName) &&
                                        !childModel.isPropertyDynamic(propName) &&
                                        !EntityModel.SYSPROPS.contains(propName) &&
                                        parentModel.getPropertyType(propName) == childModel.getPropertyType(propName)
                ).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private void addOverrideCommand(EntityModel parentModel, EntityModel childModel, List<String> props) {
        if (!props.isEmpty()) {
            props.forEach((propName) -> {
                if (childModel.getEditor(propName).getCommands().stream().noneMatch((command) -> command instanceof OverrideProperty)) {
                    childModel.getEditor(propName).addCommand(new OverrideProperty(parentModel, childModel, propName));
                }
            });
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
            final List<Class<? extends Entity>> classCatalog = ((Catalog) entity).getClassCatalog().stream()
                    .filter(aClass -> !aClass.getSuperclass().equals(PolyMorph.class))
                    .collect(Collectors.toList());

            Class<? extends Entity> createEntityClass;
            if (classCatalog.size() == 0) {
                MessageBox.show(MessageType.WARNING, Language.get(ClassSelector.class, "empty"));
                return;
            } else if (classCatalog.size() > 1 || context.getChildClass().isAnnotationPresent(ClassCatalog.Definition.class)) {
                createEntityClass = new ClassSelector(classCatalog).select();
                if (createEntityClass == null) {
                    return;
                }
            } else {
                createEntityClass = classCatalog.get(0);
            }

            final Entity newEntity = SelectorPresentation.newEntity(createEntityClass, Entity.findOwner(context));
            if (newEntity == null) {
                return;
            }

            final EntityModel parentModel = context.model;
            final EntityModel childModel = newEntity.model;

            final List<String> overridableProps = getOverrideProps(parentModel, childModel);
            addOverrideCommand(parentModel, childModel, overridableProps);

            final Dialog editor = new Dialog(
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

                                table.getSelectionModel().setSelectionInterval(
                                        tableModel.getRowCount() - 1,
                                        tableModel.getRowCount() - 1
                                );
                            } catch (Exception e) {
                                newEntity.model.rollback();
                            }
                        }
                    },
                    Dialog.Default.BTN_OK, Dialog.Default.BTN_CANCEL
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
                    .map(newEntity.model::getEditor)
                    .forEach((propEditor) -> propEditor.getEditor().addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentHidden(ComponentEvent e) {
                            editor.pack();
                        }

                        @Override
                        public void componentShown(ComponentEvent e) {
                            editor.pack();
                        }
                    }));

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
            final Class<? extends Entity> createEntityClass = context.getClass();
            final Entity newEntity = SelectorPresentation.cloneEntity(createEntityClass, context.toRef());
            if (newEntity == null) {
                return;
            }

            final EntityModel parentModel = ((Entity) context.getParent()).model;
            final EntityModel childModel  = newEntity.model;

            final List<String> overridableProps = getOverrideProps(parentModel, childModel);
            final List<String> overriddenProps  = context.getOverride();

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

            final DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
            final DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();

            final Dialog editor = new Dialog(
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
                    handler = (button) -> (event) -> {
                        if (event.getID() != Dialog.OK || newEntity.getInvalidProperties().isEmpty()) {
                            defaultHandler.apply(button).actionPerformed(event);
                        }
                    };
                }
                
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(700, super.getPreferredSize().height);
                }
            };
            
            newEntity.model.getProperties(Access.Edit).stream()
                    .map(newEntity.model::getEditor)
                    .forEach((propEditor) -> propEditor.getEditor().addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentHidden(ComponentEvent e) {
                            editor.pack();
                        }

                        @Override
                        public void componentShown(ComponentEvent e) {
                            editor.pack();
                        }
                    }));
            
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
                    boolean hasChild = entities.get(0) instanceof Catalog && entities.get(0).getChildCount() > 0;
                    return new CommandStatus(hasProps || hasChild, allDisabled || entities.get(0).islocked() ? IMAGE_VIEW : IMAGE_EDIT);
                } else {
                    return new CommandStatus(false, entity.allowModifyChild() ? IMAGE_EDIT : IMAGE_VIEW);
                }
            };
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2 && isActive()) {
                        execute(tableModel.getEntityForRow(table.getSelectedRow()), null);
                    }
                }
            });
            activate();
        }


        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            boolean allDisabled = context.model.getProperties(Access.Edit).stream().noneMatch((name) -> context.model.getEditor(name).isEditable());

            final Dialog editor = new Dialog(
                    SwingUtilities.getWindowAncestor(SelectorPresentation.this),
                    allDisabled ? IMAGE_VIEW : IMAGE_EDIT,
                    Language.get(SelectorPresentation.class, allDisabled ? "viewer@title" : "editor@title"),
                    new JPanel(new BorderLayout()) {{
                        add(context.getEditorPage(), BorderLayout.NORTH);

                        if (context instanceof Catalog && context.getChildCount() > 0) {
                            SelectorPresentation embedded = context.getSelectorPresentation();
                            if (embedded != null) {
                                add(context.getSelectorPresentation(), BorderLayout.CENTER);
                                embedded.setBorder(new TitledBorder(
                                        new LineBorder(Color.GRAY, 1),
                                        IComplexType.coalesce(BrowseMode.getDescription(BrowseMode.getClassHierarchy(context), "group@title"), BrowseMode.SELECTOR_TITLE)
                                ));
                            }
                        }

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
                    handler = (button) -> (event) -> {
                        if (event.getID() != Dialog.OK || context.getInvalidProperties().isEmpty()) {
                            defaultHandler.apply(button).actionPerformed(event);
                        }
                    };
                }
                
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(700, super.getPreferredSize().height);
                }
            };
            
            context.model.getProperties(Access.Edit).stream()
                    .map(context.model::getEditor)
                    .forEach((propEditor) -> propEditor.getEditor().addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentHidden(ComponentEvent e) {
                            editor.pack();
                        }

                        @Override
                        public void componentShown(ComponentEvent e) {
                            editor.pack();
                        }
                    }));
            
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
                message = MessageFormat.format(
                        Language.get(SelectorPresentation.class, "confirm@del.range"),
                        getContext().stream()
                                .map(entity -> "&bull;&nbsp;<b>"+entity+"</b>")
                                .collect(Collectors.joining("<br>"))
                );
            }
            return message;
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            deleteInstance(entity);
//            if (context.model.remove()) {
//                context.getParent().delete(context);
//            }
        }

        @Override
        public boolean multiContextAllowed() {
            return true;
        }

    }


    @SuppressWarnings("unchecked")
    private static <E extends Entity> E newEntity(Class<E> entityClass, EntityRef owner) {
        try {
            return (E) getCreatorMethod(entityClass).invoke(null, entityClass, owner, null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Logger.getLogger().warn("Unable to create new entity", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Entity> E cloneEntity(Class<E> entityClass, EntityRef source) {
        try {
            Method method = getCreatorMethod(entityClass);
            if (method.getDeclaringClass().equals(PolyMorph.class)) {
                return (E) method.invoke(null, entityClass, source, null);
            } else {
                return (E) method.invoke(null, entityClass, Entity.findOwner(source.getValue().getParent()), null);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            Logger.getLogger().warn("Unable to clone entity", e);
            return null;
        }
    }

    private static <E extends Entity> void deleteInstance(E entity) {
        try {
            Method method = getCleanerMethod(entity.getClass());
            method.setAccessible(true);
            method.invoke(null, entity);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Logger.getLogger().warn("Unable to delete entity", e);
        }
    }

    private static <E extends Entity> Method getCreatorMethod(Class<E> entityClass) {
        Class<? super E> creator = entityClass;
        while (true) {
            try {
                return creator.getDeclaredMethod("newInstance", Class.class, EntityRef.class, String.class);
            } catch (NoSuchMethodException e) {
                creator = creator.getSuperclass();
            }
        }
    }

    private static <E extends Entity> Method getCleanerMethod(Class<E> entityClass) {
        Class<? super E> creator = entityClass;
        while (true) {
            try {
                return creator.getDeclaredMethod("deleteInstance", Entity.class);
            } catch (NoSuchMethodException e) {
                creator = creator.getSuperclass();
            }
        }
    }

    private enum CommandContextKind {
        Parent, Child
    }
}
