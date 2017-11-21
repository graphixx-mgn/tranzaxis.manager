package codex.presentation;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.component.render.DefaultRenderer;
import codex.editor.IEditor;
import codex.log.Logger;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

/**
 * Презентация селектора сущности. Реализует как функциональность отображения и 
 * редактирования дочерних сущностей, так и обеспечивает работу команд по созданию
 * новых сущностей.
 */
public final class SelectorPresentation extends JPanel implements IModelListener, ListSelectionListener {
    
    private final CommandPanel        commandPanel = new CommandPanel();
    private final List<EntityCommand> commands = new LinkedList<>();
    private final DefaultTableModel   tableModel;
    private final JTable              table;
    
    /**
     * Конструктор презентации. 
     */
    public SelectorPresentation(Entity entity) {
        super(new BorderLayout());
        if (!entity.model.getProperties(Access.Edit).isEmpty()) {
            setBorder(new CompoundBorder(
                    new EmptyBorder(0, 5, 0, 5), 
                    new LineBorder(Color.GRAY, 1)
            ));
        }
        Entity prototype = Entity.newInstance(entity.getChildClass(), null);
        
        commands.add(new EditEntity());
        commands.add(new CreateEntity(entity, entity.getChildClass()));
        commands.add(new CloneEntity());
        commands.add(new DeleteEntity());
        commandPanel.addCommands(commands.toArray(new EntityCommand[]{}));
        commandPanel.addSeparator();
        
        commands.addAll(prototype.getCommands());
        commandPanel.addCommands(prototype.getCommands().toArray(new EntityCommand[]{}));
        
        commands.forEach((command) -> {
            command.setContext();
        });
        
        add(commandPanel, BorderLayout.NORTH);
        
        Vector dataVector = new Vector();
        entity.childrenList().forEach((node) -> {
            Vector rowVector = new Vector<>();
            Entity child = (Entity) node;
            child.model.getProperties(Access.Select).forEach((String propName) -> {
                rowVector.add(propName.equals(EntityModel.PID) ? child : child.model.getValue(propName));
            });
            dataVector.addElement(rowVector);
            child.model.addModelListener(this);
        });
        
        tableModel = new DefaultTableModel(
                dataVector,
                new Vector<>(
                        prototype.model.getProperties(Access.Select).stream().map((propName) -> {
                            return prototype.model.getPropertyTitle(propName);
                        }).collect(Collectors.toList())
                )
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
            
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight((int) (IEditor.FONT_VALUE.getSize() * 2));
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0,0));
        table.setPreferredScrollableViewportSize(getPreferredSize());
        table.setDefaultRenderer(String.class, new DefaultRenderer());
        table.getTableHeader().setDefaultRenderer(new DefaultRenderer());
        
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
                        commands.get(0).execute(context);
                    }
                }
            }
        });
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        int rowCount = this.tableModel.getRowCount();
        for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
            if (((Entity) this.tableModel.getValueAt(rowIdx, 0)).model.equals(model)) {
                final int entityIdx = rowIdx;
                List<String> selectorProps = model.getProperties(Access.Select);
                selectorProps.forEach((propName) -> {
                    if (changes.contains(propName)) {
                        int propIdx = selectorProps.indexOf(propName);
                        this.tableModel.setValueAt(model.getValue(propName), entityIdx, propIdx);
                    }
                });
                this.tableModel.fireTableRowsUpdated(rowIdx, rowIdx);
                break;
            }
        }
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
                    return (Entity) tableModel.getValueAt(rowIdx, 0);
                })
                .collect(Collectors.toList())
                .toArray(new Entity[]{});
        
        commands.forEach((command) -> {
            command.setContext(entities);
        });
    }
    
    
    public class CreateEntity extends EntityCommand {
    
        private final Entity parent;
        private final Class  entityClass;

        public CreateEntity(Entity parent, Class entityClass) {
            super(
                    "create", null,
                    ImageUtils.resize(ImageUtils.getByPath("/images/plus.png"), 28, 28), 
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@create"),
                    null
            );
            activator = (entities) -> {};
            this.entityClass = entityClass;
            this.parent = parent;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            SwingUtilities.invokeLater(() -> {
                Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), parent.toString());
                execute(null);
                activate();
            });
        }

        @Override
        public void execute(Entity context) {
            Entity newEntity = Entity.newInstance(entityClass, null);
            
            DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
            DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();
            
            EditorPage page = new EditorPage(newEntity.model, EditorPage.Mode.Create);
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
                    new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            if (event.getID() == Dialog.OK) {
                                newEntity.setTitle(newEntity.model.getPID());
                                newEntity.model.init(newEntity.model.getPID());
                                newEntity.model.commit();
                                
                                tableModel.addRow(
                                        newEntity.model.getProperties(Access.Select).stream().map((propName) -> {
                                            return propName.equals(EntityModel.PID) ? newEntity : newEntity.model.getValue(propName);
                                        }).toArray()
                                );
                                table.getSelectionModel().setSelectionInterval(
                                        tableModel.getRowCount() - 1, 
                                        tableModel.getRowCount() - 1
                                );
                                parent.insert(newEntity);
                            }
                        }
                    },
                    confirmBtn, declineBtn
            ) {
                {
                    // Перекрытие обработчика кнопок
                    Function<DialogButton, AbstractAction> defaultHandler = handler;
                    handler = (button) -> {
                        return new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                if (button.getID() != Dialog.OK || newEntity.getInvalidProperties().isEmpty()) {
                                    defaultHandler.apply(button).actionPerformed(event);
                                }
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
    
    public class CloneEntity extends EntityCommand {

        public CloneEntity() {
            super(
                    "clone", null,
                    ImageUtils.resize(ImageUtils.getByPath("/images/clone.png"), 28, 28), 
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@clone"),
                    (entity) -> true
            );
        }

        @Override
        public void execute(Entity context) {
            Entity newEntity = Entity.newInstance(context.getParent().getChildClass(), null);
            
            context.model.getProperties(Access.Edit).forEach((propName) -> {
                newEntity.model.setValue(propName, context.model.getValue(propName));
            });
            
            DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
            DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();
            
            EditorPage page = new EditorPage(newEntity.model, EditorPage.Mode.Create);
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
                    new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            if (event.getID() == Dialog.OK) {
                                newEntity.setTitle(newEntity.model.getPID());
                                newEntity.model.init(newEntity.model.getPID());
                                newEntity.model.commit();
                                
                                tableModel.addRow(
                                        newEntity.model.getProperties(Access.Select).stream().map((propName) -> {
                                            return propName.equals(EntityModel.PID) ? newEntity : newEntity.model.getValue(propName);
                                        }).toArray()
                                );
                                table.getSelectionModel().setSelectionInterval(
                                        tableModel.getRowCount() - 1, 
                                        tableModel.getRowCount() - 1
                                );
                                context.getParent().insert(newEntity);
                            }
                        }
                    },
                    confirmBtn, declineBtn
            ) {
                {
                    // Перекрытие обработчика кнопок
                    Function<DialogButton, AbstractAction> defaultHandler = handler;
                    handler = (button) -> {
                        return new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                if (button.getID() != Dialog.OK || newEntity.getInvalidProperties().isEmpty()) {
                                    defaultHandler.apply(button).actionPerformed(event);
                                }
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
    
    public class EditEntity extends EntityCommand {
    
        public EditEntity() {
            super(
                    "edit", null,
                    ImageUtils.resize(ImageUtils.getByPath("/images/edit.png"), 28, 28), 
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@edit"),
                    (entity) -> true
            );
        }

        @Override
        public void execute(Entity context) {
            DialogButton confirmBtn = Dialog.Default.BTN_OK.newInstance();
            DialogButton declineBtn = Dialog.Default.BTN_CANCEL.newInstance();
            
            EditorPage page = new EditorPage(context.model, EditorPage.Mode.Edit);
            page.setBorder(new CompoundBorder(
                    new EmptyBorder(10, 5, 5, 5), 
                    new TitledBorder(new LineBorder(Color.LIGHT_GRAY, 1), context.toString())
            ));
            
            Dialog editor = new Dialog(
                    SwingUtilities.getWindowAncestor(SelectorPresentation.this),
                    ImageUtils.getByPath("/images/edit.png"),
                    Language.get(SelectorPresentation.class.getSimpleName(), "editor@title"), page,
                    new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            if (event.getID() == Dialog.OK) {
                                //TODO: При изменении перекрытия свойств изменений нет - ошибка сохранения
                                context.model.commit();
                            } else {
                                context.model.rollback();
                            }
                        }
                    },
                    confirmBtn, declineBtn
            ) {
                {
                    // Перекрытие обработчика кнопок
                    Function<DialogButton, AbstractAction> defaultHandler = handler;
                    handler = (button) -> {
                        return new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                if (button.getID() != Dialog.OK || context.getInvalidProperties().isEmpty()) {
                                    defaultHandler.apply(button).actionPerformed(event);
                                }
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
    
    public class DeleteEntity extends EntityCommand {
    
        public DeleteEntity() {
            super(
                    "delete", null,
                    ImageUtils.resize(ImageUtils.getByPath("/images/minus.png"), 28, 28), 
                    Language.get(SelectorPresentation.class.getSimpleName(), "command@delete"),
                    (entity) -> true
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
                        msgBuilder.append("<br>&emsp;&#9900&nbsp;&nbsp;").append(entity.toString());
                    });
                    message = msgBuilder.toString();
                }
                MessageBox msgBox = new MessageBox(
                        MessageType.CONFIRMATION, null, message,
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                                if(event.getID() == Dialog.OK) {
                                    Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), Arrays.asList(getContext()));
                                    for (Entity entity : getContext()) {
                                        execute(entity);
                                    }
                                    activate();
                                }
                            }
                        }
                );
                msgBox.setVisible(true);
            });
        }

        @Override
        public void execute(Entity context) {
            if (!context.model.remove()) {
                return;
            }
            int rowCount = tableModel.getRowCount();
            for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
                if (((Entity) tableModel.getValueAt(rowIdx, 0)).model.equals(context.model)) {
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
