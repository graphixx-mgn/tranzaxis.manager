package codex.presentation;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.button.IButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.component.render.GeneralRenderer;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.log.Logger;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.OverrideProperty;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.DropMode;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

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
    
    private final CommandPanel        commandPanel = new CommandPanel();
    private final List<EntityCommand> commands = new LinkedList<>();
    private final Class               entityClass;
    private final Entity              entity;
    private final SelectorTableModel  tableModel;
    private final JTable              table;
    
    /**
     * Конструктор презентации. 
     */
    public SelectorPresentation(Entity entity) {
        super(new BorderLayout());
        if (!entity.model.getProperties(Access.Edit).isEmpty()) {
            setBorder(new CompoundBorder(
                    new EmptyBorder(0, 5, 3, 5), 
                    new LineBorder(Color.GRAY, 1)
            ));
        }
        entityClass = entity.getChildClass();
        this.entity = entity;
        Entity prototype = Entity.newPrototype(entityClass);
        
        commands.add(new EditEntity());
        if (entity.allowModifyChild()) {
            commands.add(new CreateEntity());
            commands.add(new CloneEntity());
            commands.add(new DeleteEntity());
        }
        commandPanel.addCommands(commands.toArray(new EntityCommand[]{}));
        if (!prototype.getCommands().isEmpty()) {
            commandPanel.addSeparator();
        }
        
        commands.addAll(prototype.getCommands());
        commandPanel.addCommands(prototype.getCommands().toArray(new EntityCommand[]{}));
        
        commands.forEach((command) -> {
            if (command instanceof CreateEntity) {
                command.setContext(entity);
            } else {
                command.getButton().setEnabled(false);
            }
        });
        
        add(commandPanel, BorderLayout.NORTH);
        
        tableModel = new SelectorTableModel(entity, prototype);
        table = new SelectorTable(tableModel);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        GeneralRenderer renderer = new GeneralRenderer();
        table.setDefaultRenderer(Bool.class,         renderer);
        table.setDefaultRenderer(IComplexType.class, renderer);
        table.getTableHeader().setDefaultRenderer(renderer);
        
        if (entity.allowModifyChild()) {
            table.setDragEnabled(true);
            table.setDropMode(DropMode.INSERT_ROWS);
            table.setTransferHandler(new TableTransferHandler(table));
        }
        
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
                IButton cmdButton = commands.get(0).getButton();
                if (event.getClickCount() == 2 && cmdButton.isEnabled() && !cmdButton.isInactive()) {
                    commands.get(0).execute(tableModel.getEntityAt(table.getSelectedRow()), null);
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
            }
        });
        
        entity.addNodeListener(new INodeListener() {
            @Override
            public void childDeleted(INode parentNode, INode childNode, int index) {
                tableModel.removeRow(index);
                if (index < tableModel.getRowCount()) {
                    table.getSelectionModel().setSelectionInterval(index, index);
                } else if (index == tableModel.getRowCount()) {
                    table.getSelectionModel().setSelectionInterval(index-1, index-1);
                } else {
                    table.getSelectionModel().clearSelection();
                }
            }
        });
    }

    @Override
    public void valueChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) return;
        
        List<Entity> context = Arrays
                .stream(table.getSelectedRows())
                .boxed()
                .map((rowIdx) -> {
                    return tableModel.getEntityAt(rowIdx);
                })
                .collect(Collectors.toList());
        
        commands.forEach((command) -> {
            if (!(command instanceof CreateEntity)) {
                context.forEach((contextItem) -> {
                    contextItem.removeNodeListener(this);
                });
                command.setContext(context);
                context.forEach((contextItem) -> {
                    contextItem.addNodeListener(this);
                });
            }
        });
    }
    
    @Override
    public void childChanged(INode node) {
        commands.forEach((command) -> {
            command.activate();
        });
    }

    class CreateEntity extends EntityCommand<Entity> {
    
        CreateEntity() {
            super(
                    "create", null,
                    IMAGE_CREATE, 
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@create"),
                    null,
                    KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_MASK)
            );
            activator = (entities) -> {};
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
            
            EditorPage page = new EditorPage(newEntity.model);
            page.setBorder(new CompoundBorder(
                    new EmptyBorder(10, 5, 5, 5), 
                    new TitledBorder(
                            new LineBorder(Color.LIGHT_GRAY, 1), 
                            Language.get(SelectorPresentation.class.getSimpleName(), "creator@desc")
                    )
            ));
            
            Dialog editor = new Dialog(
                    SwingUtilities.getWindowAncestor(SelectorPresentation.this),
                    ImageUtils.getByPath("/images/plus.png"),
                    Language.get(SelectorPresentation.class.getSimpleName(), "creator@title"), page,
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
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@clone"),
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
                                    !EntityModel.SYSPROPS.contains(propName) &&
                                    parentModel.getPropertyType(propName) == childModel.getPropertyType(propName)
                    ).collect(Collectors.toList());
            List<String> overriddenProps = (List<String>) context.getOverride();

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
            
            EditorPage page = new EditorPage(newEntity.model);
            page.setBorder(new CompoundBorder(
                    new EmptyBorder(10, 5, 5, 5), 
                    new TitledBorder(
                            new LineBorder(Color.LIGHT_GRAY, 1), 
                            Language.get(SelectorPresentation.class.getSimpleName(), "copier@desc")
                    )
            ));
            
            Dialog editor = new Dialog(
                    SwingUtilities.getWindowAncestor(SelectorPresentation.this),
                    ImageUtils.getByPath("/images/clone.png"),
                    Language.get(SelectorPresentation.class.getSimpleName(), "copier@title"), page,
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
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@edit"),
                    (entity) -> true
            );
            activator = (entities) -> {
                if (entities != null && entities.size() > 0 && !(entities.size() > 1 && !multiContextAllowed())) {
                    boolean allDisabled = entities.get(0).model.getProperties(Access.Edit).parallelStream().allMatch((name) -> {
                        return !entities.get(0).model.getEditor(name).isEditable();
                    });
                    boolean hasProps = !entities.get(0).model.getProperties(Access.Edit).isEmpty();
                    getButton().setIcon(allDisabled || /*!entity.allowModifyChild() ||*/ entities.get(0).islocked() ? IMAGE_VIEW : IMAGE_EDIT);
                    getButton().setEnabled(hasProps);
                } else {
                    getButton().setIcon(entity.allowModifyChild() ? IMAGE_EDIT : IMAGE_VIEW);
                    getButton().setEnabled(false);
                }
            };
            activate();
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
            DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();
            
//            if (!entity.allowModifyChild()) {
//                context.model.getProperties(Access.Edit).stream().filter((propName) -> {
//                    return !context.model.isPropertyDynamic(propName);
//                }).forEach((propName) -> {
//                    ((AbstractEditor) context.model.getEditor(propName)).setEditable(false);
//                });
//            }
            
            EditorPage page = new EditorPage(context.model);
            page.setBorder(new CompoundBorder(
                    new EmptyBorder(10, 5, 5, 5), 
                    new TitledBorder(new LineBorder(Color.LIGHT_GRAY, 1), context.toString())
            ));
            
            confirmBtn.setEnabled(getButton().getIcon().equals(IMAGE_EDIT));
            
            Dialog editor = new Dialog(
                    SwingUtilities.getWindowAncestor(SelectorPresentation.this),
                    getIcon(),
                    Language.get(
                            SelectorPresentation.class.getSimpleName(), 
                            getIcon().equals(IMAGE_EDIT) ? "editor@title" : "viewer@title"
                    ), 
                    page,
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
                    confirmBtn, declineBtn
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
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@delete"),
                    (entity) -> true,
                    KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)
            );
        }
        
        @Override
        public void actionPerformed(ActionEvent event) {
            SwingUtilities.invokeLater(() -> {
                String message; 
                if (getContext().size() == 1) {
                    message = MessageFormat.format(
                            Language.get(
                                    SelectorPresentation.class.getSimpleName(), 
                                    "confirm@del.single"
                            ), 
                            getContext().get(0)
                    );
                } else {
                    StringBuilder msgBuilder = new StringBuilder(
                            Language.get(
                                    SelectorPresentation.class.getSimpleName(), 
                                    "confirm@del.range"
                            )
                    );
                    getContext().forEach((entity) -> {
                        msgBuilder.append("<br>&emsp;&#9913&nbsp;&nbsp;").append(entity.toString());
                    });
                    message = msgBuilder.toString();
                }
                MessageBox.show(
                        MessageType.CONFIRMATION, null, message,
                        (close) -> {
                            if (close.getID() == Dialog.OK) {
                                Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), getContext());
                                getContext().forEach((entity) -> {
                                    execute(entity, null);
                                });
                                activate();
                            }
                        }
                );
            });
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
