package codex.presentation;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.editor.AbstractEditor;
import codex.explorer.browser.BrowseMode;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.model.Access;
import codex.model.Entity;
import codex.model.ICatalog;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Презентация редактора сущности. Реализует как функциональность редактирования
 * непосредственно свойств сущности при помощи редакторов в составе страницы
 * редактора, так и обеспечивает работу команд сущностей.
 */
public final class EditorPresentation extends JPanel {

    private final Class        entityClass;
    private final CommandPanel commandPanel;
    private final Supplier<? extends Entity> context;
    private final List<EntityCommand<Entity>> systemCommands  = new LinkedList<>();
    private final List<EntityCommand<Entity>> contextCommands = new LinkedList<>();
    
    /**
     * Конструктор презентации. 
     * @param entity Редактируемая сущность.
     */
    public EditorPresentation(Entity entity) {
        super(new BorderLayout());
        entityClass = entity.getClass();
        context = () -> entity;

        commandPanel = new CommandPanel(Collections.emptyList());

        entity.addNodeListener(new INodeListener() {
            @Override
            public void childChanged(INode node) {
                Entity changed = (Entity) node;
                changed.model.getProperties(Access.Edit).stream()
                        .filter((propName) -> !changed.model.isPropertyDynamic(propName))
                        .forEach((propName) -> ((AbstractEditor) changed.model.getEditor(propName)).setLocked(changed.islocked()));
                activateCommands();
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

        updateCommands();
        activateCommands();
        add(commandPanel, BorderLayout.NORTH);
        add(context.get().getEditorPage(), BorderLayout.CENTER);
    }

    /**
     * Обновление презентации и панели команд.
     */
    public final void refresh() {
        EditorPage page = context.get().getEditorPage();
        AncestorEvent event = new AncestorEvent(page, AncestorEvent.ANCESTOR_ADDED, this, this.getParent());
        for (AncestorListener listener : page.getAncestorListeners()) {
            listener.ancestorAdded(event);
        }
        if (!this.equals(context.get().getEditorPage().getParent())) {
            add(context.get().getEditorPage(), BorderLayout.CENTER);
        }
        updateCommands();
        activateCommands();
    }

    public Class getEntityClass() {
        return entityClass;
    }

    private void updateCommands() {
        systemCommands.clear();
        systemCommands.addAll(getSystemCommands());
        commandPanel.setSystemCommands(systemCommands);

        contextCommands.clear();
        contextCommands.addAll(getContextCommands().stream()
                .filter(command -> command.getKind() != EntityCommand.Kind.System)
                .collect(Collectors.toList())
        );
        commandPanel.setContextCommands(contextCommands);
    }
    
    /**
     * Актуализация состояния доступности команд.
     */
    private void activateCommands() {
        systemCommands.forEach(sysCommand -> sysCommand.setContext(Collections.singletonList(context.get())));
        contextCommands.forEach(ctxCommand -> ctxCommand.setContext(Collections.singletonList(context.get())));
    }

    private List<EntityCommand<Entity>> getSystemCommands() {
        final List<EntityCommand<Entity>> commands = new LinkedList<>();
        if (isEditable()) {
            final EntityCommand<Entity> commitCmd = findCommand(systemCommands, CommitEntity.class, new CommitEntity());
            commands.add(commitCmd);

            final EntityCommand<Entity> rollbackCmd = findCommand(systemCommands, RollbackEntity.class, new RollbackEntity());
            commands.add(rollbackCmd);
        }
        if (context.get().model.hasExtraProps()) {
            final EntityCommand<Entity> showExtraCmd = findCommand(systemCommands, ShowExtraProps.class, new ShowExtraProps());
            commands.add(showExtraCmd);
        }
        getContextCommands().stream()
                .filter(command -> command.getKind() == EntityCommand.Kind.System)
                .forEach(commands::add);
        return commands;
    }

    private List<EntityCommand<Entity>> getContextCommands() {
        List<EntityCommand<Entity>> commands = context.get().getCommands();
        commands.removeIf(command -> command.getKind() == EntityCommand.Kind.System);
        return commands;
    }

    private EntityCommand<Entity> findCommand(
            Collection<EntityCommand<Entity>> commands,
            Class<? extends EntityCommand<Entity>> commandClass,
            EntityCommand<Entity> defCommand
    ) {
        return commands.stream().filter(command -> command.getClass().equals(commandClass)).findFirst().orElse(defCommand);
    }

    private boolean isEditable() {
        return context.get().model.getProperties(Access.Edit).stream()
               .anyMatch(propName -> !context.get().model.isPropertyDynamic(propName));
    }


    public static final class EmbeddedEditor {

        public final static ImageIcon IMAGE_EDIT  = ImageUtils.getByPath("/images/edit.png");
        public final static ImageIcon IMAGE_VIEW  = ImageUtils.getByPath("/images/view.png");

        public static void show(Entity context) {
            SwingUtilities.invokeLater(() -> {
                boolean allDisabled = context.model.getProperties(Access.Edit).stream().noneMatch((name) -> context.model.getEditor(name).isEditable());

                final codex.component.dialog.Dialog editor = new codex.component.dialog.Dialog(
                        Dialog.findNearestWindow(),
                        allDisabled ? IMAGE_VIEW : IMAGE_EDIT,
                        Language.get(SelectorPresentation.class, allDisabled ? "viewer@title" : "editor@title"),
                        new JPanel(new BorderLayout()) {{
                            add(context.getEditorPage(), BorderLayout.NORTH);

                            if (context.getChildCount() > 0) {
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
                            if (event.getID() == codex.component.dialog.Dialog.OK) {
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
                                new DialogButton[] { codex.component.dialog.Dialog.Default.BTN_CLOSE.newInstance() } :
                                new DialogButton[] { codex.component.dialog.Dialog.Default.BTN_OK.newInstance(), codex.component.dialog.Dialog.Default.BTN_CANCEL.newInstance() }
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
            });
        }

    }
}
