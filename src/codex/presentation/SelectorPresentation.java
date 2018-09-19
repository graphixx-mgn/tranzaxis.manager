package codex.presentation;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
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
import codex.type.ArrStr;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.IComplexType;
import codex.type.Int;
import codex.type.Str;
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
    
    private final static ImageIcon IMAGE_EDIT   = ImageUtils.resize(ImageUtils.getByPath("/images/edit.png"), 28, 28);
    private final static ImageIcon IMAGE_VIEW   = ImageUtils.resize(ImageUtils.getByPath("/images/view.png"), 28, 28);
    private final static ImageIcon IMAGE_CREATE = ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 28, 28);
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
        Entity prototype = Entity.newInstance(entityClass, null, null);
        
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
        table.setDefaultRenderer(Str.class,  renderer);
        table.setDefaultRenderer(Int.class,  renderer);
        table.setDefaultRenderer(Bool.class, renderer);
        table.setDefaultRenderer(Enum.class, renderer);
        table.setDefaultRenderer(ArrStr.class,    renderer);
        table.setDefaultRenderer(EntityRef.class, renderer);
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
                if (event.getClickCount() == 2) {
                    for (Entity context : commands.get(0).getContext()) {
                        commands.get(0).execute(context, null);
                    }
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
                command.setContext(context.toArray(new Entity[]{}));
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

    class CreateEntity extends EntityCommand {
    
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
            Entity newEntity = Entity.newInstance(entityClass, null, null);
 
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
                            newEntity.setTitle(newEntity.model.getPID());
                            context.insert(newEntity);
                            newEntity.model.commit();

                            tableModel.addRow(
                                    newEntity.model.getProperties(Access.Select).stream().map((propName) -> {
                                        return newEntity.model.getValue(propName);
                                    }).toArray()
                            );
                            newEntity.model.addModelListener(tableModel);
                            newEntity.model.addChangeListener((name, oldValue, newValue) -> {
                                List<String> selectorProps = newEntity.model.getProperties(Access.Select);
                                if (newEntity.model.isPropertyDynamic(name) && selectorProps.contains(name)) {
                                    final int entityIdx = tableModel.getRowCount() - 1;
                                    int propIdx = selectorProps.indexOf(name);
                                    tableModel.setValueAt(newValue, entityIdx, propIdx);
                                }
                            });

                            table.getSelectionModel().setSelectionInterval(
                                    tableModel.getRowCount() - 1, 
                                    tableModel.getRowCount() - 1
                            );
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
                    return new Dimension(650, super.getPreferredSize().height);
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
            
            editor.setResizable(false);
            editor.setVisible(true);
        }

    }
    
    class CloneEntity extends EntityCommand {
        
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
            Entity newEntity = Entity.newInstance(context.getParent().getChildClass(), /*SelectorPresentation.this.entity*/null, null);
            
            context.model.getProperties(Access.Edit).forEach((propName) -> {
                if (EntityModel.PID.equals(propName)) {
                    newEntity.model.setValue(propName, context.model.getValue(propName)+" (1)");
                } else {
                    newEntity.model.setValue(propName, context.model.getValue(propName));
                }
            });
            
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
                            newEntity.setTitle(newEntity.model.getPID());
                            context.getParent().insert(newEntity);
                            newEntity.model.commit();

                            tableModel.addRow(
                                    newEntity.model.getProperties(Access.Select).stream().map((propName) -> {
                                        return newEntity.model.getValue(propName);
                                    }).toArray()
                            );
                            newEntity.model.addModelListener(tableModel);
                            newEntity.model.addChangeListener((name, oldValue, newValue) -> {
                                List<String> selectorProps = newEntity.model.getProperties(Access.Select);
                                if (newEntity.model.isPropertyDynamic(name) && selectorProps.contains(name)) {
                                    final int entityIdx = tableModel.getRowCount() - 1;
                                    int propIdx = selectorProps.indexOf(name);
                                    System.err.println(newEntity.model.getProperties(Access.Select));
                                    tableModel.setValueAt(newValue, entityIdx, propIdx);
                                }
                            });

                            table.getSelectionModel().setSelectionInterval(
                                    tableModel.getRowCount() - 1, 
                                    tableModel.getRowCount() - 1
                            );
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
                    return new Dimension(650, super.getPreferredSize().height);
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
            
            editor.setResizable(false);
            editor.setVisible(true);
        }

    }
    
    class EditEntity extends EntityCommand {
    
        EditEntity() {
            super(
                    "edit", null,
                    IMAGE_EDIT, 
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@edit"),
                    (entity) -> true
            );
            activator = (entities) -> {
                if (entities != null && entities.length > 0 && !(entities.length > 1 && !multiContextAllowed())) {
                    boolean allDisabled = entities[0].model.getProperties(Access.Edit).parallelStream().allMatch((name) -> {
                        return !entities[0].model.getEditor(name).isEditable();
                    });
                    getButton().setIcon(allDisabled || !entity.allowModifyChild() || entities[0].islocked() ? IMAGE_VIEW : IMAGE_EDIT);
                    getButton().setEnabled(true);
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
                            //TODO: При изменении перекрытия свойств изменений нет - ошибка сохранения
                            context.model.commit();
                        } else {
                            context.model.rollback();
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
                    return new Dimension(650, super.getPreferredSize().height);
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
            
            editor.setResizable(false);
            editor.setVisible(true);
        }

    }
    
    class DeleteEntity extends EntityCommand {
    
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
                if (getContext().length == 1) {
                    message = MessageFormat.format(
                            Language.get(
                                    SelectorPresentation.class.getSimpleName(), 
                                    "confirm@del.single"
                            ), 
                            getContext()[0]
                    );
                } else {
                    StringBuilder msgBuilder = new StringBuilder(
                            Language.get(
                                    SelectorPresentation.class.getSimpleName(), 
                                    "confirm@del.range"
                            )
                    );
                    Arrays.asList(getContext()).forEach((entity) -> {
                        msgBuilder.append("<br>&emsp;&#9913&nbsp;&nbsp;").append(entity.toString());
                    });
                    message = msgBuilder.toString();
                }
                MessageBox.show(
                        MessageType.CONFIRMATION, null, message,
                        (close) -> {
                            if (close.getID() == Dialog.OK) {
                                Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), Arrays.asList(getContext()));
                                for (Entity entity : getContext()) {
                                    execute(entity, null);
                                }
                                activate();
                            }
                        }
                );
            });
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            if (!context.model.remove()) {
                return;
            }
            int rowCount = tableModel.getRowCount();
            for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
                if (tableModel.getEntityAt(rowIdx).model.equals(context.model)) {
                    tableModel.removeRow(rowIdx);
                    if (rowIdx < tableModel.getRowCount()) {
                        table.getSelectionModel().setSelectionInterval(rowIdx, rowIdx);
                    } else if (rowIdx == tableModel.getRowCount()) {
                        table.getSelectionModel().setSelectionInterval(rowIdx-1, rowIdx-1);
                    } else {
                        table.getSelectionModel().clearSelection();
                    }
                    break;
                }
            }
            context.getParent().delete(context);
        }

        @Override
        public boolean multiContextAllowed() {
            return true;
        }

    }

}
