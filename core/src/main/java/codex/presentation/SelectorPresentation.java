package codex.presentation;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.config.IConfigStoreService;
import codex.editor.AbstractEditor;
import codex.editor.IEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.log.Logger;
import codex.model.*;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
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
import java.util.stream.Stream;

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

    private final Entity                  entity;
    private final SelectorTableModel      tableModel;
    private final JTable                  table;
    private final CommandPanel            commandPanel;

    private final Supplier<List<Entity>>  context;
    private final Map<EntityCommand<Entity>,  CommandContextKind> systemCommands  = new LinkedHashMap<>();
    private final Map<EntityCommand<Entity>,  CommandContextKind> contextCommands = new LinkedHashMap<>();

    private final List<IEntitySelectedListener> selectListeners = new LinkedList<>();
    
    /**
     * Конструктор презентации. 
     */
    public SelectorPresentation(Entity entity) {
        super(new BorderLayout());
        this.entity = entity;
        
        tableModel = new SelectorTableModel(entity);
        table = new SelectorTable(tableModel);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        commandPanel = new CommandPanel(Collections.emptyList());
        
        if (entity.allowModifyChild()) {
            table.setDragEnabled(true);
            table.setDropMode(DropMode.INSERT_ROWS);
            table.setTransferHandler(new TableTransferHandler(table));
        }

        context = () -> Arrays
                .stream(table.getSelectedRows())
                .boxed()
                .map(row -> tableModel.getEntityForRow(table.convertRowIndexToModel(row)))
                .collect(Collectors.toList());
        
        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setViewportView(table);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(0, 5, 5, 5), 
                new MatteBorder(1, 1, 1, 1, Color.GRAY)
        ));

        updateCommands();
        activateCommands();
        add(commandPanel, BorderLayout.NORTH);
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

        final TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
        if (entity instanceof Catalog && ((Catalog) entity).isChildFilterDefined()) {
            sorter.setRowFilter(new RowFilter<TableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                    return ((Catalog) entity).getCurrentFilter().getCondition().test(entity, tableModel.getEntityForRow(entry.getIdentifier()));
                }
            });
            final IEditor filterEditor = ((Catalog) entity).getFilterEditor();
            commandPanel.add(Box.createHorizontalGlue());
            commandPanel.add(filterEditor.getEditor());

            entity.model.getProperty(((AbstractEditor) filterEditor).getPropName()).addChangeListener((name, oldValue, newValue) -> sorter.sort());
        } else {
            sorter.setRowFilter(new RowFilter<TableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                    return true;
                }
            });
        }
        table.setRowSorter(sorter);

        entity.addNodeListener(this);
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
                sorter.sort();
            }

            @Override
            public void childReplaced(INode prevChild, INode nextChild) {
                int index = entity.getIndex(nextChild);
                tableModel.attachListeners((Entity) nextChild);

                EntityModel childModel = ((Entity) nextChild).model;
                childModel.getProperties(Access.Any).forEach(propName -> {
                        if (tableModel.findColumn(propName) >= 0) {
                            tableModel.setValueAt(childModel.getValue(propName), index, tableModel.findColumn(propName));
                        }
                });
            }
        });
        addAncestorListener(new AncestorAdapter() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                if (event.getAncestor() != event.getComponent()) {
                    refresh();
                }
            }
        });
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
                    command.setContext(Collections.singletonList(entity)); break;
                default:
                    context.forEach((contextItem) -> contextItem.removeNodeListener(this));
                    command.setContext(context);
                    context.forEach((contextItem) -> contextItem.addNodeListener(this));
            }
        });

        context.forEach(ctxEntity -> ctxEntity.removeNodeListener(this));
        contextCommands.forEach((command, ctxKind) -> {
            command.setContext(context);
        });
        context.forEach(ctxEntity -> ctxEntity.addNodeListener(this));
    }

    private Map<EntityCommand<Entity>, CommandContextKind> getSystemCommands() {
        final Map<EntityCommand<Entity>, CommandContextKind> commands = new LinkedHashMap<>();

        entity.getCommands().stream()
                .filter(command -> command.getKind() == EntityCommand.Kind.System)
                .forEach(command -> commands.put(command, CommandContextKind.Parent));

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
            new LinkedList<>(selectListeners).forEach(listener -> listener.selectedEntities(context.get()));
        }
    }

    @SuppressWarnings("unchecked")
    private static void addOverrideCommand(EntityModel parentModel, EntityModel childModel, List<String> props) {
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

    public void enableSorting() {
        if (!entity.allowModifyChild()) {
            table.setAutoCreateRowSorter(true);
        }
    }

    public void setColumnRenderer(int column, TableCellRenderer renderer) {
        table.getColumnModel().getColumn(column).setCellRenderer(renderer);
    }

    public void addSelectListener(IEntitySelectedListener listener) {
        selectListeners.add(listener);
    }

    public void removeSelectListener(IEntitySelectedListener listener) {
        selectListeners.remove(listener);
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
            Class<? extends Entity> createEntityClass = ClassSelector.select(context);
            if (createEntityClass == null) return;

            final Entity newEntity = SelectorPresentation.newEntity(createEntityClass, Entity.findOwner(context));
            if (newEntity == null) {
                return;
            }

            final EntityModel parentModel = context.model;
            final EntityModel childModel = newEntity.model;
            final List<String> overridableProps = newEntity.getOverrideProps(parentModel);
            addOverrideCommand(parentModel, childModel, overridableProps);

            boolean hasProps = !childModel.getProperties(Access.Edit).isEmpty();

            if (!hasProps) {
                try {
                    newEntity.model.commit(true);
                    newEntity.setTitle(newEntity.getPID());
                    context.attach(newEntity);

                    table.getSelectionModel().setSelectionInterval(
                            tableModel.getRowCount() - 1,
                            tableModel.getRowCount() - 1
                    );
                } catch (Exception e) {
                    newEntity.model.rollback();
                }
                return;
            }

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
                                context.attach(newEntity);

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

        @Override
        public boolean disableWithContext() {
            return false;
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
            final List<String> overridableProps = newEntity.getOverrideProps(parentModel);
            addOverrideCommand(parentModel, childModel, overridableProps);

            final List<String> overriddenProps = context.getOverride();
            Stream.concat(
                    Stream.of(EntityModel.OVR),
                    context.model.getProperties(Access.Edit).stream()
            ).forEach((propName) -> {
                if ("PID".equals(propName)) {
                    newEntity.model.setValue(propName, context.model.getValue(propName)+" (1)");
                } else {
                    if (!newEntity.model.isPropertyDynamic(propName)) {
                        if (!(overridableProps.contains(propName) && (overriddenProps == null || !overriddenProps.contains(propName)))) {
                            if (!newEntity.model.isStateProperty(propName)) {
                                newEntity.model.setValue(propName, context.model.getValue(propName));
                            }
                        }
                    }
                }
            });

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
                                context.getParent().attach(newEntity);

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
                    boolean allDisabled = entities.get(0).model.getProperties(Access.Edit).stream()
                            .noneMatch(
                                    (name) -> entities.get(0).model.getEditor(name).isEditable()
                            );
                    boolean hasProps = !entities.get(0).model.getProperties(Access.Edit).isEmpty();
                    boolean hasChild = ICatalog.class.isAssignableFrom(entities.get(0).getClass()) && entities.get(0).getChildCount() > 0;
                    return new CommandStatus(hasProps || hasChild, allDisabled || entities.get(0).islocked() ? IMAGE_VIEW : IMAGE_EDIT);
                } else {
                    return new CommandStatus(false, entity.allowModifyChild() ? IMAGE_EDIT : IMAGE_VIEW);
                }
            };
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2 && isActive()) {
                        execute(tableModel.getEntityForRow(table.convertRowIndexToModel(table.getSelectedRow())), null);
                    }
                }
            });
            activate();
        }


        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            EditorPresentation.EmbeddedEditor.show(context);
        }
    }
    
    class DeleteEntity extends EntityCommand<Entity> {
    
        DeleteEntity() {
            super(
                    "delete", null,
                    IMAGE_REMOVE, 
                    Language.get(SelectorPresentation.class, "command@delete"),
                    entity -> entity.getID() != null,
                    KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)
            );
        }

        @Override
        public String acquireConfirmation() {
            return ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).deleteConfirmRequired() ? MessageFormat.format(
                    Language.get(SelectorPresentation.class, getContext().size() == 1 ? "confirm@del.single" : "confirm@del.range"),
                    Entity.entitiesTable(getContext(), false)
            ) : null;
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            deleteInstance(entity, context);
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
            return (E) method.invoke(null, entityClass, Entity.findOwner(source.getValue().getParent()), null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Logger.getLogger().warn("Unable to clone entity", e);
            return null;
        }
    }

    private static <E extends Entity> void deleteInstance(E parentEntity, E entity) {
        try {
            Method method = getCleanerMethod(parentEntity.getClass());
            method.setAccessible(true);
            method.invoke(null, entity, true, true);
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
                return creator.getDeclaredMethod("deleteInstance", Entity.class, boolean.class, boolean.class);
            } catch (NoSuchMethodException e) {
                creator = creator.getSuperclass();
            }
        }
    }

    private enum CommandContextKind {
        Parent, Child
    }

    public interface IEntitySelectedListener {
        void selectedEntities(List<Entity> selected);
    }
}
