package codex.model;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.component.panel.HTMLView;
import codex.config.IConfigStoreService;
import codex.editor.AbstractEditor;
import codex.editor.BoolEditor;
import codex.editor.IEditor;
import codex.explorer.tree.AbstractNode;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.presentation.AncestorAdapter;
import codex.presentation.EditorPage;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Абстракная сущность, базовый родитель прикладных сущностей приложения.
 * Также является узлом дерева проводника, реализуя интерфейс {@link INode}.
 */
@EntityDefinition
public abstract class Entity extends AbstractNode implements IPropertyChangeListener, Iconified {

    private static final ImageIcon ICON_ERROR   = ImageUtils.getByPath("/images/stop.png");
    private static final ImageIcon ICON_INVALID = ImageUtils.getByPath("/images/warn.png");
    private static final ImageIcon ICON_LOCKED  = ImageUtils.getByPath("/images/lock.png");
    private static final ImageIcon ICON_REMOVE  = ImageUtils.getByPath("/images/close.png");
   
    private static final Boolean DEV_MODE = "1".equals(java.lang.System.getProperty("showSysProps"));
    private static final EntityCache     CACHE = EntityCache.getInstance();
    private static final CommandRegistry COMMAND_REGISTRY = CommandRegistry.getInstance();

    private       String    title;
    private final ImageIcon icon;
    private final String    hint;

    Boolean isPrototype = false;

    private EditorPage           editorPage;
    private EditorPresentation   editorPresentation;
    private SelectorPresentation selectorPresentation;
     
    /**
     * Модель сущности, контейнер всех её свойств.
     */
    public final EntityModel model;
    
    /**
     * Конструктор сущности.
     * @param icon Иконка для отображения в дереве проводника.
     * @param title Название сущности, уникальный ключ.
     * @param hint Описание сущности.
     */
    public Entity(EntityRef owner, ImageIcon icon, String title, String hint) {
        String PID;
        if (title == null) {
            if (!Catalog.class.isAssignableFrom(getClass())) {
                PID = null;
            } else {
                PID = Language.get(this.getClass(), "title", new java.util.Locale("en", "US"));
                this.title = Language.get(this.getClass(), "title");
            }
        } else {
            PID = title;
            this.title = title;
        }
        this.icon = new ImageIcon(icon != null ?
                icon.getImage() :
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        );
        this.hint  = hint;
        this.model = new EntityModel(
                owner,
                this.getClass().asSubclass(Entity.class),
                PID
        ) {
            @Override
            public IEditor getEditor(String name) {
                AbstractEditor editor = (AbstractEditor) super.getEditor(name);
                editor.setLocked(islocked());
                return editor;
            }

            @Override
            boolean remove(boolean readAfter) {
                // TODO: Перенести в Entity.remove (чтобы работало в PolyMorph)
                if (getID() == null) {
                    //System.out.println("Remove from cache (ID==null): "+Entity.this);
                    CACHE.remove(Entity.this);
                    if (readAfter) read();
                    return true;
                } else {
                    boolean success = super.remove(readAfter);
                    if (success) {
                        //System.out.println("Remove from cache (ID==??): "+Entity.this);
                        CACHE.remove(Entity.this);
                    }
                    return success;
                }
            }
        };
        this.model.addChangeListener(this);
        this.model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                if (changes.contains(EntityModel.PID)) {
                    setTitle(model.getPID(false));
                }
                ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).findReferencedEntries(Entity.this.getClass(), getID()).stream()
                        .filter(link -> link.isIncoming)
                        .forEach(link -> {
                            EntityRef ref = EntityRef.build(link.entryClass, link.entryID);
                            ref.getValue().fireChangeEvent();
                        });
            }
        });
        if (getPID() != null && !Language.NOT_FOUND.equals(getPID())) {
            synchronized (this.getClass()) {
                final Entity found = CACHE.find(
                    this.getClass(),
                    owner == null ? null : owner.getId(),
                    PID
                );
                if (found == null) {
                    CACHE.cache(this, PID, owner == null ? null : owner.getId());
                }
            }
        }

        //Properties
        model.addDynamicProp(EntityModel.THIS, new AnyType(),
                Access.Edit,
                () -> new Iconified() {
                    @Override
                    public ImageIcon getIcon() {
                        return Entity.this.getIcon();
                    }

                    @Override
                    public String toString() {
                        return Entity.this.toString();
                    }
                },
                EntityModel.PID
        );
    }
    
    /**
     * Возвращает наименование сущности.
     */
    public final String getTitle() {
        return title;
    }
    
    /**
     * Установить наименование сущности.
     */
    public final void setTitle(String title) {
        this.title = title;
        fireChangeEvent();
    }
    
    /**
     * Возвращает иконку сущности.
     */
    @Override
    public final ImageIcon getIcon() {
        if (isPrototype) {
            return icon;
        } else if (islocked()) {
            return ImageUtils.combine(ImageUtils.grayscale(icon), ICON_LOCKED);
        } else if (!model.isValid()) {
            return ImageUtils.combine(icon, ICON_INVALID);
        } else {
            return (getMode() & INode.MODE_ENABLED) == INode.MODE_ENABLED ? icon : ImageUtils.grayscale(icon);
        }
    }

    protected final void setIcon(ImageIcon icon) {
        if (icon != null) {
            this.icon.setImage(icon.getImage());
            fireChangeEvent();
        }
    }
    
    /**
     * Возвращает описание сущности.
     */
    public final String getHint() {
        return hint;
    }
    
    public final Integer getID() {
        return model.getID();
    }
    
    public final String getPID() {
        return model.getPID(false);
    }
    
    public final Integer getSEQ() {
        return model.getSEQ();
    }
    
    public final Entity getOwner() {
        return model.getOwner();
    }
    
    public final List<String> getOverride() {
        return model.getOverride();
    }
    
    public final Entity setID(Integer id) {
        model.setID(id);
        return this;
    }
    
    public final Entity setPID(String pid) {
        model.setPID(pid);
        return this;
    }
    
    public final Entity setSEQ(Integer seq) {
        model.setSEQ(seq);
        return this;
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        if (parent != null && getChildClass() != null) {
            loadChildren();
        }
        if (parent != null && isOverridable()) {
            EntityModel parentModel = ((Entity) getParent()).model;
            List<String> overrideProps = getOverrideProps(parentModel);
            if (!overrideProps.isEmpty()) {
                addOverrideCommand(parentModel, model, overrideProps);
            }
        }
    }

    public void loadChildren() {
        Map<Class<? extends Entity>, Collection<String>> childrenPIDs = getChildrenPIDs();
        childrenPIDs.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        if (!childrenPIDs.isEmpty()) {
            ITask loadChildren = new LoadChildren(childrenPIDs);
            final int prevMode = getMode();
            loadChildren.addListener(new ITaskListener() {
                @Override
                public void afterExecute(ITask task) {
                    setMode(prevMode);
                }
            });
            ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).quietTask(loadChildren);
            setMode(MODE_LOADING);
        }
    }

    protected Map<Class<? extends Entity>, Collection<String>> getChildrenPIDs() {
        Entity owner = ICatalog.class.isAssignableFrom(this.getClass()) ? this.getOwner() : this;
        return ClassCatalog.getClassCatalog(this).stream()
                .collect(Collectors.toMap(
                        catalogClass -> catalogClass,
                        catalogClass -> {
                            Integer ownerId = owner == null ? null : owner.getID();
                            return model.getConfigService().readCatalogEntries(ownerId, catalogClass).values();
                        }
                ));
    }

    public boolean isOverridable() {
        return true;
    }

    public List<String> getOverrideProps(EntityModel parentModel) {
        final EntityModel childModel = model;
        return parentModel.getProperties(Access.Edit).stream()
            .filter(
                    propName ->
                            childModel.hasProperty(propName) &&
                            !childModel.isPropertyDynamic(propName)  &&
                            !PolyMorph.SYSPROPS.contains(propName)   &&
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
    public void attach(INode child) {
        if (child.getParent() != this) {
            super.attach(child);
        }
    }

    final void remove(boolean cascade, boolean confirmation) {
        Collection<EntityModel.Reference> links = findReferences(true);

        if (links.isEmpty()) {
            // Already confirmed in selector command or confirmation == false
            Logger.getContextLogger(EntityModel.OrmContext.class).debug("Entity ''{0}'' does not have references", model.getQualifiedName());
        } else {
            if (cascade) {
                if (confirmation && !removalConfirmed(this, links, true)) {
                    return;
                }
            } else {
                if (confirmation) {
                    Logger.getContextLogger(EntityModel.OrmContext.class).debug("Entity ''{0}'' may not be removed due to references", model.getQualifiedName());
                    removalConfirmed(this, links, false);
                }
                return;
            }
        }
        remove();
    }

    protected void remove() {
        Collection<EntityModel.Reference> links = findReferences(false);
        links.forEach(link -> {
                if (link.incoming) {
                    link.unlink(this);
                } else {
                    link.getEntity().remove(true, false);
                }
        });

        Logger.getContextLogger(EntityModel.OrmContext.class).debug("Remove entity ''{0}''", model.getQualifiedName());
        if (model.remove(false)) {
            if (getParent() != null && !Entity.getDefinition(getClass()).autoGenerated()) {
                getParent().detach(this);
            }
            if (getParent() != null) {
                model.read();
                fireChangeEvent();
            }
        }
    }

    private static boolean removalConfirmed(Entity entity, Collection<EntityModel.Reference> links, boolean removalAllowed) {
        final AtomicBoolean result = new AtomicBoolean(false);
        final Collection<Entity> childEntities = links.stream()
                .filter(link -> !link.incoming)
                .map(EntityModel.Reference::getEntity)
                .collect(Collectors.toList());
        final Collection<Entity> extEntities = links.stream()
                .filter(link -> link.incoming)
                .map(EntityModel.Reference::getEntity)
                .distinct()
                .collect(Collectors.toList());

        PropertyHolder<Bool, Boolean> confirm = new PropertyHolder<>("cascade", new Bool(false), false);
        BoolEditor check = new BoolEditor(confirm);

        DialogButton next = Dialog.Default.BTN_OK.newInstance();
        DialogButton exit = Dialog.Default.BTN_CANCEL.newInstance();
        DialogButton[] buttonSet = new LinkedList<DialogButton>() {{
            if (removalAllowed) add(next);
            add(exit);
        }}.toArray(new DialogButton[]{});

        if (removalAllowed) {
            next.setEnabled(false);
            confirm.addChangeListener((name, oldValue, newValue) -> {
                next.setEnabled(newValue == Boolean.TRUE);
            });
        }

        new Dialog(
                FocusManager.getCurrentManager().getActiveWindow(),
                removalAllowed ? ICON_INVALID : ICON_ERROR,
                removalAllowed ? Language.get(MessageType.class, "message@warning") : MessageType.ERROR.toString(),
                new JPanel(new BorderLayout()) {{
                    setBorder(new EmptyBorder(10, 5, 15, 5));

                    JLabel infoLabel = new JLabel() {{
                        setBorder(new EmptyBorder(0, 5, 5, 5));
                        setIconTextGap(10);
                        if (removalAllowed) {
                            setIcon(ImageUtils.combine(
                                    Entity.newPrototype(entity.getClass()).getIcon(),
                                    ImageUtils.resize(ICON_REMOVE, 0.8f),
                                    SwingConstants.SOUTH_EAST
                            ));
                            setText(MessageFormat.format(
                                    Language.get(Entity.class, "cascade@warn"),
                                    String.join("<br>", new LinkedList<String>() {{
                                        if (!childEntities.isEmpty()) add(Language.get(Entity.class, "ref@child.info"));
                                        if (!extEntities.isEmpty()) add(Language.get(Entity.class, "ref@ext.info"));
                                    }})
                            ));
                        } else {
                            setIcon(ICON_ERROR);
                            setText(MessageFormat.format(Language.get(Entity.class, "error@notdeleted"), entity));
                        }
                    }};
                    add(infoLabel, BorderLayout.NORTH);

                    add(new JPanel() {{
                        setBorder(new EmptyBorder(
                                0, infoLabel.getIcon().getIconWidth()+infoLabel.getIconTextGap()+infoLabel.getInsets().left,
                                0, 5
                        ));
                        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                        if (!childEntities.isEmpty()) {
                            HTMLView view = new HTMLView() {
                                {
                                    setText("<html>"+entitiesTable(childEntities, false)+"</html>");
                                    setFont(IEditor.FONT_VALUE.deriveFont((float) (IEditor.FONT_VALUE.getSize()*0.9)));
                                }
                                @Override
                                public Dimension getPreferredScrollableViewportSize() {
                                    if (childEntities.size() > 4) {
                                        Dimension defSize = super.getPreferredScrollableViewportSize();
                                        int margin = getInsets().top + getInsets().bottom + 2;
                                        int itemHeight = (defSize.height - margin) / childEntities.size();
                                        return new Dimension(defSize.width, itemHeight * 4 + margin);
                                    } else {
                                        return super.getPreferredScrollableViewportSize();
                                    }
                                }
                            };

                            add(new JScrollPane(view) {{
                                SwingUtilities.invokeLater(() -> getViewport().setViewPosition(new Point(0, 0)));
                                setBorder(new TitledBorder(
                                    new LineBorder(Color.LIGHT_GRAY, 1),
                                    Language.get(Entity.class, "ref@child")
                                ));
                            }});
                        }
                        if (!extEntities.isEmpty()) {
                            HTMLView view = new HTMLView() {
                                {
                                    setText("<html>"+entitiesTable(extEntities, true)+"</html>");
                                    setFont(IEditor.FONT_VALUE.deriveFont((float) (IEditor.FONT_VALUE.getSize()*0.9)));
                                }
                                @Override
                                public Dimension getPreferredScrollableViewportSize() {
                                    if (extEntities.size() > 4) {
                                        Dimension defSize = super.getPreferredScrollableViewportSize();
                                        int margin = getInsets().top + getInsets().bottom + 2;
                                        int itemHeight = (defSize.height - margin) / extEntities.size();
                                        return new Dimension(defSize.width, itemHeight * 4 + margin);
                                    } else {
                                        return super.getPreferredScrollableViewportSize();
                                    }
                                }
                            };

                            add(new JScrollPane(view) {{
                                SwingUtilities.invokeLater(() -> getViewport().setViewPosition(new Point(0, 0)));
                                setBorder(new CompoundBorder(
                                        new EmptyBorder(5, 0, 0, 0),
                                        new TitledBorder(
                                                new LineBorder(Color.LIGHT_GRAY, 1),
                                                Language.get(Entity.class, "ref@ext")
                                        )
                                ));
                            }});
                        }
                    }}, BorderLayout.CENTER);

                    if (removalAllowed) {
                        add(new JPanel() {{
                            setBorder(new EmptyBorder(
                                    5, infoLabel.getIcon().getIconWidth()+infoLabel.getIconTextGap()+infoLabel.getInsets().left+2,
                                    0, 5
                            ));
                            setLayout(new BorderLayout(10, 5));
                            add(check.getEditor(), BorderLayout.WEST);
                            add(check.getLabel(),  BorderLayout.CENTER);
                        }}, BorderLayout.SOUTH);
                    }
                }},
                event -> result.set(event.getID() == Dialog.OK),
                buttonSet
        ){
            @Override
            public Dimension getPreferredSize() {
                Dimension defSize = super.getPreferredSize();
                return new Dimension(Math.max(defSize.width, 400), defSize.height);
            }
        }.setVisible(true);
        return result.get();
    }

    private static int TABLE_ITEM_HEIGHT = (int) (IEditor.FONT_VALUE.getSize()*1.7);
    public static String entitiesTable(Collection<Entity> entities, boolean showPath) {
        return entities.stream()
                .map(entity -> MessageFormat.format(
                        Language.get(Entity.class, "entity@html"),
                        ImageUtils.toBase64(entity.getIcon()),
                        TABLE_ITEM_HEIGHT,
                        showPath && entity.getParent() != null ? entity.getPathString() : entity.getTitle()
                ))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Поиск внешних ссылок на данную модель.
     * Внешняя ссылка - это поле типа EntityRef у другой сущности.
     * Внутренняя ссылка - это сущность, владельцем которой являетя эта сущность
     */
    private Collection<EntityModel.Reference> findReferences(boolean traceResult) {
        if (getID() == null) return Collections.emptyList();
        Collection<EntityModel.Reference> references = model.getReferences();

        references.removeIf(reference -> {
            if (reference.incoming) {
                return false;
            } else {
                if (Entity.getDefinition(reference.model.getEntityClass()).autoGenerated()) {
                    Entity refEntity = reference.getEntity();
                    Collection<EntityModel.Reference> depends = refEntity.findReferences(false);
                    if (depends.isEmpty() && traceResult) {
                        Logger.getContextLogger(EntityModel.OrmContext.class).debug("Skip auto generated independent entity ''{0}''", reference.model.getQualifiedName());
                    }
                    return depends.isEmpty();
                } else {
                    return false;
                }
            }
        });

        if (!references.isEmpty() && traceResult) {
            Logger.getContextLogger(EntityModel.OrmContext.class).debug(
                    "Entity ''{0}'' has references:\n{1}",
                    model.getQualifiedName(),
                    references.stream()
                            .map(reference -> {
                                Entity refEntity = reference.getEntity();
                                return MessageFormat.format(
                                        "* {0}: {1} ({2}@{3})",
                                        reference.incoming ? "Incoming" : "Outgoing",
                                        reference.model != null ? refEntity.getPathString() : refEntity.getTitle(),
                                        reference.model.getQualifiedName(),
                                        reference.property

                                );
                            }).collect(Collectors.joining("\n"))
            );
        }
        return references;
    }

    /**
     * Получение списка имеющихся команд сущности.
     */
    public final List<EntityCommand<Entity>> getCommands() {
        return getCommands(this);
    }

    @SuppressWarnings("unchecked")
    protected List<EntityCommand<Entity>> getCommands(Entity entity) {
        LinkedList<EntityCommand<Entity>> commands = new LinkedList<>();
        new LinkedList<>(COMMAND_REGISTRY.getRegisteredCommands(entity.getClass())).forEach(entityCommand -> {
            commands.add((EntityCommand<Entity>) entityCommand);
        });
        return commands;
    }

    /**
     * Получение команды по имени.
     * Устаревший метод. Следует использовать метод {@link Entity#getCommand(Class)}.
     */
    @Deprecated
    public final EntityCommand getCommand(String name) {
        return getCommands().stream()
                .filter(command -> command.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodError(MessageFormat.format("Entity does not have command ''{0}''", name)));
    }

    /**
     * Получение команды по классу.
     */
    @SuppressWarnings("unchecked")
    public final <E extends EntityCommand<? extends Entity>> E getCommand(Class<E> commandClass) {
        return (E) getCommands().stream()
                .filter(command -> command.getClass().equals(commandClass))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodError(MessageFormat.format("Entity does not have command of class ''{0}''", commandClass)));
    }

    /**
     * Возвраящает страницу редактора модели.
     */
    @Override
    public final EditorPage getEditorPage() {
        if (editorPage == null) {
            editorPage = new EditorPage(model);
            editorPage.addAncestorListener(new AncestorAdapter() {
                @Override
                public void ancestorAdded(AncestorEvent event) {
                    if (event.getAncestor() instanceof Dialog || Objects.equals(event.getAncestor(), editorPresentation)) {
                        onOpenPageView();
                    }
                }
            });
        }
        return editorPage;
    }
    
    @Override
    public final SelectorPresentation getSelectorPresentation() {
        if (getChildClass() == null) return null;
        if (selectorPresentation == null) {
            selectorPresentation = new SelectorPresentation(this);
        }
        return selectorPresentation;
    }

    @Override
    public final EditorPresentation getEditorPresentation() {
        if (editorPresentation == null && !model.getProperties(Access.Edit).isEmpty()) {
            editorPresentation = new EditorPresentation(this);
        }
        return editorPresentation;
    }
    
    public final List<String> getInvalidProperties() {
        return model.editors.entrySet().stream()
                .filter((entry) -> !entry.getValue().stopEditing())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    protected void setPropertyRestriction(String propName, Access access) {
        if (model.hasProperty(propName)) {
            model.restrictions.put(propName, access);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final void propertyChange(String name, Object oldValue, Object newValue) {
        if (!ISerializableType.class.isAssignableFrom(model.getPropertyType(name))) return;

        if (!Objects.equals(oldValue, newValue)) {
            Logger.getContextLogger(EntityModel.OrmContext.class).debug(
                    "Property ''{0}@{1}'' has been changed: {2} -> {3}",
                    model.getQualifiedName(), name,
                    model.getProperty(name).getPropValue().getQualifiedValue(
                            oldValue instanceof IComplexType ? ((IComplexType) oldValue).getValue() : oldValue
                    ),
                    model.getProperty(name).getPropValue().getQualifiedValue(
                            newValue instanceof IComplexType ? ((IComplexType) newValue).getValue() : newValue
                    )
            );
        }
    }
    
    /**
     * Проверка свойств на корректность значений.
     * При наличии некорректного свойсва вызывается диалог ошибки.
     */
    public final boolean validate() {
        List<String> invalidProps = getInvalidProperties().stream()
            .map((propName) -> model.getProperty(propName).getTitle())
            .collect(Collectors.toList());
        if (!invalidProps.isEmpty()) {
            // Имеются ошибки в значениях
            MessageBox.show(
                    MessageType.ERROR, null, 
                    MessageFormat.format(Language.get("error@invalidprop"), String.join("\n", invalidProps)),
                    (event) -> model.editors.get(getInvalidProperties().get(0)).getFocusTarget().requestFocus()
            );
            return false;
        } else {
            return true;
        }
    }
    
     /**
     * Проверка допустимости закрытия модели при переходе на другую сущность в дереве проводника.
     */
    public final boolean close() {
        if (validate() && model.hasChanges()) {
            // Предлагаем сохранить
            MessageBox.show(
                    MessageType.CONFIRMATION, null,
                    MessageFormat.format(
                            Language.get("error@unsavedprop"),
                            model.getChanges().stream()
                                .map(propName -> "&nbsp;&bull;&nbsp;"+model.getProperty(propName).getTitle()+"<br>")
                                .collect(Collectors.joining())
                    ),
                    (event) -> {
                        if (event.getID() == Dialog.OK) {
                            try {
                                model.commit(true);
                            } catch (Exception e) {
                                // Do nothing
                            }
                        }
                    }
            );
        }
        return !model.hasChanges();
    }
    
    public final void stopEditing() {
        model.editors.values().forEach(IEditor::stopEditing);
    }
    
    @Override
    public final String toString() {
        String title = getTitle();
        if (DEV_MODE && title != null && getID() != null) {
            title = title.concat(" (#").concat(Integer.toString(getID())).concat(")");
        }
        return IComplexType.coalesce(title, "<new "+getClass().getSimpleName()+">");
    }
    
    @SuppressWarnings("unchecked")
    public final EntityRef<Entity> toRef() {
        EntityRef ref = new EntityRef<>(this.getClass());
        ref.setValue(this);
        return ref;
    }

    protected void onOpenPageView() {}

    static <E extends Entity> EntityDefinition getDefinition(Class<E> entityClass) {
        return entityClass.getAnnotation(EntityDefinition.class);
    }
    
    public static <E extends Entity> E newPrototype(Class<E> entityClass) {
        if (entityClass.isMemberClass()) {
            throw new IllegalStateException(MessageFormat.format(
                    "Entity class ''{0}'' is inner class of ''{1}''", 
                    entityClass.getSimpleName(),
                    entityClass.getEnclosingClass().getCanonicalName()
            ));
        }
        try {
            Constructor<E> ctor = entityClass.getDeclaredConstructor(EntityRef.class, String.class);
            ctor.setAccessible(true);
            E prototype = ctor.newInstance(null, null);
            prototype.isPrototype = true;
            return prototype;
        } catch (InvocationTargetException | ExceptionInInitializerError | InstantiationException | IllegalAccessException e) {
            Throwable exception = e;
            do {
                Logger.getLogger().error(
                        MessageFormat.format("Unable instantiate entity prototype [Class: {0}]", entityClass.getCanonicalName()), exception
                );
            } while ((exception = exception.getCause()) != null);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(MessageFormat.format(
                    "Entity ''{0}'' does not have universal constructor (EntityRef, String)", 
                    entityClass.getCanonicalName()
            ));
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public static <E extends Entity> E newInstance(Class<E> entityClass, EntityRef owner, String PID) {
        synchronized (entityClass) {
            Class<E> implClass = entityClass;
            if (PolyMorph.class.isAssignableFrom(entityClass) && Modifier.isAbstract(entityClass.getModifiers())) {
                Map<String, String> dbValues = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).readClassInstance(
                        entityClass, PID, owner == null ? null : owner.getId()
                );
                try {
                    implClass = (Class<E>) Class.forName(dbValues.get(PolyMorph.PROP_IMPL_CLASS));
                } catch (ClassNotFoundException ignore) {
                    //
                }
            }

            final Entity found = CACHE.find(
                    implClass,
                    owner == null ? null : owner.getId(),
                    PID != null ? PID : Language.get(entityClass, "title", new java.util.Locale("en", "US"))
            );
            if (found == null) {
                try {
                    Constructor<E> ctor = implClass.getDeclaredConstructor(EntityRef.class, String.class);
                    ctor.setAccessible(true);
                    final E created = ctor.newInstance(owner, PID);
                    if (created.getPID() == null) {
                        created.model.addModelListener(new IModelListener() {
                            @Override
                            public void modelSaved(EntityModel model, List<String> changes) {
                                if (changes.contains(EntityModel.PID)) {
                                    CACHE.cache(created, created.getPID(), owner == null ? null : owner.getId());
                                }
                            }
                        });
                    } else if (Language.NOT_FOUND.equals(created.getPID())) {
                        throw new IllegalStateException(MessageFormat.format(
                                "Localization string 'title' not defined for class ''{0}''", implClass
                        ));
                    }
                    return created;
                } catch (InvocationTargetException | ExceptionInInitializerError | InstantiationException | IllegalAccessException e) {
                    Throwable exception = e;
                    do {
                        Logger.getLogger().error(
                                MessageFormat.format("Unable instantiate entity ''{0}'' / [Class: {1}]", PID, implClass.getCanonicalName()), exception
                        );
                    } while ((exception = exception.getCause()) != null);
                } catch (NoSuchMethodException e) {
                    Logger.getLogger().error(
                            "Entity ''{0}'' does not have universal constructor (EntityRef<owner>, String<PID>)",
                            implClass.getCanonicalName()
                    );
                }
            }
            //noinspection unchecked
            return (E) found;
        }
    }

    public static <E extends Entity> void deleteInstance(E entity, boolean cascade, boolean confirmation) {
        entity.remove(cascade, confirmation);
    }
    
    public static EntityRef findOwner(INode from) {
        INode next = from;
        while (next != null && ICatalog.class.isAssignableFrom(next.getClass())) {
            next = (INode) next.getParent();
        }
        if (next == null) {
            return null;
        } else {
            Entity found = (Entity) next;
            if (found.getID() == null) {
                Logger.getLogger().warn("Found uninitialized owner entity: {0}", found.model.getQualifiedName());
                return null;
            } else {
                return found.toRef();
            }
        }
    }


    private class LoadChildren extends AbstractTask<Void> {

        private final Map<Class<? extends Entity>, Collection<String>> childrenPIDs;

        LoadChildren(Map<Class<? extends Entity>, Collection<String>> childrenPIDs) {
            super(MessageFormat.format(
                    Language.get(Catalog.class, "task@load"),
                    getParent() != null ? Entity.this.getPathString() : Entity.this.getPID()
            ));
            this.childrenPIDs = childrenPIDs;
        }

        @Override
        public Void execute() {
            EntityRef ownerRef = Entity.findOwner(Entity.this);

            childrenPIDs.forEach((catalogClass, PIDs) -> PIDs.forEach(PID -> {
                final Class<? extends Entity> implClass;

                if (PolyMorph.class.isAssignableFrom(catalogClass)) {
                    Map<String, String> dbValues = model.getConfigService().readClassInstance(catalogClass, PID, ownerRef == null ? null : ownerRef.getId());
                    try {
                        implClass = Class.forName(dbValues.get(PolyMorph.PROP_IMPL_CLASS)).asSubclass(Entity.class);
                    } catch (ClassNotFoundException e) {
                        return;
                    }
                } else {
                    implClass = catalogClass;
                }

                Entity instance = Entity.newInstance(implClass, ownerRef, PID);
                if (!childrenList().contains(instance)) {
                    attach(instance);
                }
            }));
            return null;
        }

        @Override
        public void finished(Void result) {}
    }
    
}
