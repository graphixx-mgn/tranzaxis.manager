package codex.presentation;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.component.render.GeneralRenderer;
import codex.log.Logger;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.IComplexType;
import codex.type.Int;
import codex.type.Str;
import codex.type.StringList;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
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
public final class SelectorPresentation extends JPanel implements ListSelectionListener {
    
    private final CommandPanel        commandPanel = new CommandPanel();
    private final List<EntityCommand> commands = new LinkedList<>();
    private final Class               entityClass;
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
        Entity prototype = Entity.newInstance(entityClass, null);
        
        commands.add(new EditEntity());
        commands.add(new CreateEntity() {
            
        });
        commands.add(new CloneEntity());
        commands.add(new DeleteEntity());
        commandPanel.addCommands(commands.toArray(new EntityCommand[]{}));
        commandPanel.addSeparator();
        
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
        
        table.setDefaultRenderer(Str.class,  new GeneralRenderer());
        table.setDefaultRenderer(Int.class,  new GeneralRenderer());
        table.setDefaultRenderer(Bool.class, new GeneralRenderer());
        table.setDefaultRenderer(Enum.class, new GeneralRenderer());
        table.setDefaultRenderer(StringList.class, new GeneralRenderer());
        table.setDefaultRenderer(EntityRef.class,  new GeneralRenderer());
        table.getTableHeader().setDefaultRenderer(new GeneralRenderer());
        
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
    }

    @Override
    public void valueChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) return;

        Entity[] entities = Arrays
                .stream(table.getSelectedRows())
                .boxed()
                .collect(Collectors.toList())
                .stream()
                .map((rowIdx) -> {
                    return tableModel.getEntityAt(rowIdx);
                })
                .collect(Collectors.toList())
                .toArray(new Entity[]{});
        
        commands.forEach((command) -> {
            if (!(command instanceof CreateEntity)) {
                command.setContext(entities);
            }
        });
    }

    class CreateEntity extends EntityCommand {
    
        CreateEntity() {
            super(
                    "create", null,
                    ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 28, 28), 
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@create"),
                    null,
                    KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_MASK)
            );
            activator = (entities) -> {};
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            Entity newEntity = Entity.newInstance(entityClass, null);
 
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
                            newEntity.model.init();
                            newEntity.model.commit();

                            tableModel.addRow(
                                    newEntity.model.getProperties(Access.Select).stream().map((propName) -> {
                                        return newEntity.model.getValue(propName);
                                    }).toArray()
                            );
                            newEntity.model.addModelListener(tableModel);
                            newEntity.model.addChangeListener((name, oldValue, newValue) -> {
                                if (newEntity.model.isPropertyDynamic(name)) {
                                    final int entityIdx = tableModel.getRowCount() - 1;
                                    int propIdx = newEntity.model.getProperties(Access.Select).indexOf(name);
                                    tableModel.setValueAt(newValue, entityIdx, propIdx);
                                }
                            });

                            context.insert(newEntity);
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
                    return new Dimension(550, super.getPreferredSize().height);
                }
            };
            editor.setResizable(false);
            editor.setVisible(true);
        }

    }
    
    class CloneEntity extends EntityCommand {
        
        CloneEntity() {
            super(
                    "clone", null,
                    ImageUtils.resize(ImageUtils.getByPath("/images/clone.png"), 28, 28), 
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@clone"),
                    (entity) -> true,
                    KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK)
            );
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            Entity newEntity = Entity.newInstance(context.getParent().getChildClass(), null);
            
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
                            newEntity.model.init();
                            newEntity.model.commit();

                            tableModel.addRow(
                                    newEntity.model.getProperties(Access.Select).stream().map((propName) -> {
                                        return newEntity.model.getValue(propName);
                                    }).toArray()
                            );
                            newEntity.model.addModelListener(tableModel);
                            newEntity.model.addChangeListener((name, oldValue, newValue) -> {
                                if (newEntity.model.isPropertyDynamic(name)) {
                                    final int entityIdx = tableModel.getRowCount() - 1;
                                    int propIdx = newEntity.model.getProperties(Access.Select).indexOf(name);
                                    tableModel.setValueAt(newValue, entityIdx, propIdx);
                                }
                            });

                            context.getParent().insert(newEntity);
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
                    return new Dimension(550, super.getPreferredSize().height);
                }
            };
            editor.setResizable(false);
            editor.setVisible(true);
        }

    }
    
    class EditEntity extends EntityCommand {
    
        EditEntity() {
            super(
                    "edit", null,
                    ImageUtils.resize(ImageUtils.getByPath("/images/edit.png"), 28, 28), 
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@edit"),
                    (entity) -> true
            );
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
            
            Dialog editor = new Dialog(
                    SwingUtilities.getWindowAncestor(SelectorPresentation.this),
                    ImageUtils.getByPath("/images/edit.png"),
                    Language.get(SelectorPresentation.class.getSimpleName(), "editor@title"), page,
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
                    return new Dimension(550, super.getPreferredSize().height);
                }
            };
            editor.setResizable(false);
            editor.setVisible(true);
        }

    }
    
    class DeleteEntity extends EntityCommand {
    
        DeleteEntity() {
            super(
                    "delete", null,
                    ImageUtils.resize(ImageUtils.getByPath("/images/minus.png"), 28, 28), 
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
