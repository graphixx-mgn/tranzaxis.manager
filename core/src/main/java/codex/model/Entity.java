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
import codex.explorer.IExplorerAccessService;
import codex.explorer.tree.AbstractNode;
import codex.explorer.tree.INode;
import codex.log.Logger;
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
import org.atteo.classindex.ClassIndex;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
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
import java.util.stream.StreamSupport;

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
        this.icon  = icon != null ? icon : new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
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
            model.updateDynamicProps(EntityModel.THIS);
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

    /**
     * Возвращает список классов, разрешенных для создания и загрузки в данном каталоге.
     * Каждый из этих классов наследуется от одного класса {@link ClassCatalog}.
     */
    public final List<Class<? extends Entity>> getClassCatalog() {
        Class<? extends Entity> childClass = getChildClass();
        if (childClass.isAnnotationPresent(ClassCatalog.Definition.class)) {
            return StreamSupport.stream(ClassIndex.getSubclasses(codex.model.ClassCatalog.class).spliterator(), false)
                    .filter(aClass -> childClass.isAssignableFrom(aClass) && !Modifier.isAbstract(aClass.getModifiers()))
                    .sorted(Comparator.comparing(Class::getTypeName))
                    .collect(Collectors.toList());
        } else {
            return Collections.singletonList(childClass);
        }
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        if (parent != null && getChildClass() != null) {
            loadChildren();
        }
    }

    public void loadChildren() {
        Map<Class<? extends Entity>, Collection<String>> childrenPIDs = getChildrenPIDs();
        childrenPIDs.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        if (!childrenPIDs.isEmpty()) {
            ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).quietTask(new LoadChildren(childrenPIDs) {
                private int mode = getMode();
                {
                    addListener(new ITaskListener() {
                        @Override
                        public void beforeExecute(ITask task) {
                            try {
                                if (!islocked()) {
                                    mode = getMode();
                                    setMode(MODE_NONE);
                                    getLock().acquire();
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }

                @Override
                public void finished(Void result) {
                    getLock().release();
                    setMode(mode);
                }
            });
        }
    }

    protected Map<Class<? extends Entity>, Collection<String>> getChildrenPIDs() {
        Entity owner = ICatalog.class.isAssignableFrom(this.getClass()) ? this.getOwner() : this;
        return getClassCatalog().stream()
                .collect(Collectors.toMap(
                        catalogClass -> catalogClass,
                        catalogClass -> {
                            Integer ownerId = owner == null ? null : owner.getID();
                            return model.getConfigService().readCatalogEntries(ownerId, catalogClass).values();
                        }
                ));
    }
    
    public final void setOverride(List<String> value) {
        model.setOverride(value);
    }

    public boolean isOverridable() {
        return true;
    }

    @Override
    public void attach(INode child) {
        if (child.getParent() != this) {
            super.attach(child);
        }

        if (isOverridable()) {
            Entity childEntity = (Entity) child;
            EntityModel childModel = childEntity.model;
            EntityModel parentModel = this.model;

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
        }
    }

    final void remove(boolean cascade, boolean confirmation) {
        Collection<IConfigStoreService.ForeignLink> links = findReferences(true);
        if (links.isEmpty()) {
            // Already confirmed in selector command or confirmation == false
            EntityModel.OrmContext.debug("Entity ''{0}'' does not have references", model.getQualifiedName());
        } else {
            if (cascade) {
                if (confirmation && !removalConfirmed(this, links, true)) {
                    return;
                }
            } else {
                if (confirmation) {
                    EntityModel.OrmContext.debug("Entity ''{0}'' may not be removed due to references", model.getQualifiedName());
                    removalConfirmed(this, links, false);
                }
                return;
            }
        }
        remove();
    }

    protected void remove() {
        Collection<IConfigStoreService.ForeignLink> links = findReferences(false);
        links.forEach(link -> {
                    if (link.isIncoming) {
                        Entity extRef = EntityRef.build(link.entryClass, link.entryID).getValue();
                        extRef.model.getProperties(Access.Any).forEach(propName -> {
                            if (this.equals(extRef.model.getValue(propName))) {
                                extRef.model.setValue(propName, null);
                                try {
                                    extRef.model.commit(false, propName);
                                } catch (Exception ignore) {
                                    //
                                }
                            }
                        });
                    } else {
                        EntityRef.build(link.entryClass, link.entryID).getValue().remove(true, false);
                    }
                });

        EntityModel.OrmContext.debug("Remove entity ''{0}''", model.getQualifiedName());
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

    private static boolean removalConfirmed(Entity entity, Collection<IConfigStoreService.ForeignLink> links, boolean removalAllowed) {
        final AtomicBoolean result = new AtomicBoolean(false);
        final Collection<Entity> childEntities = links.stream()
                .filter(link -> !link.isIncoming)
                .map(link -> EntityRef.build(link.entryClass, link.entryID).getValue())
                .collect(Collectors.toList());
        final Collection<Entity> extEntities = links.stream()
                .filter(link -> link.isIncoming)
                .map(link -> EntityRef.build(link.entryClass, link.entryID).getValue())
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
                            add(new HTMLView() {{
                                setBorder(new TitledBorder(
                                        new LineBorder(Color.LIGHT_GRAY, 1),
                                        Language.get(Entity.class, "ref@child")
                                ));
                                setText("<html>"+entitiesTable(childEntities, false)+"</html>");
                                setFont(IEditor.FONT_VALUE.deriveFont((float) (IEditor.FONT_VALUE.getSize()*0.9)));
                            }});
                        }
                        if (!extEntities.isEmpty()) {
                            add(new HTMLView() {{
                                setBorder(new CompoundBorder(
                                        new EmptyBorder(5, 0, 0, 0),
                                        new TitledBorder(
                                                new LineBorder(Color.LIGHT_GRAY, 1),
                                                Language.get(Entity.class, "ref@ext")
                                        )
                                ));
                                setText("<html>"+entitiesTable(extEntities, true)+"</html>");
                                setFont(IEditor.FONT_VALUE.deriveFont((float) (IEditor.FONT_VALUE.getSize()*0.9)));
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
        ).setVisible(true);
        return result.get();
    }

    public static String entitiesTable(Collection<Entity> entities, boolean showPath) {
        return entities.stream()
                .map(entity -> MessageFormat.format(
                        Language.get(Entity.class, "entity@html"),
                        ImageUtils.toBase64(entity.getIcon()),
                        (int) (IEditor.FONT_VALUE.getSize()*1.7),
                        showPath && entity.getParent() != null ? entity.getPathString() : entity.getTitle()
                ))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Поиск внешних ссылок на данную модель.
     * Внешняя ссылка - это поле типа EntityRef у другой сущности.
     * Внутренняя ссылка - это сущность, владельцем которой являетя эта сущность
     */
    private Collection<IConfigStoreService.ForeignLink> findReferences(boolean traceResult) {
        //TODO: вызов метода сразу после создания дочерних сущностей Polymorph владельца приводит к ошибке
        if (getID() == null) return Collections.emptyList();
        Class<? extends Entity> tableClass = PolyMorph.class.isAssignableFrom(getClass()) ? PolyMorph.getPolymorphClass(getClass()) : getClass();

        IConfigStoreService ICS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
        List<IConfigStoreService.ForeignLink> links = ICS.findReferencedEntries(tableClass, getID());
        links.removeIf(link -> {
            if (link.isIncoming) {
                return false;
            } else {
                EntityRef ref = EntityRef.build(link.entryClass, link.entryID);
                if (ref.getValue() != null && Entity.getDefinition(ref.getValue().getClass()).autoGenerated()) {
                    List<IConfigStoreService.ForeignLink> depends = ICS.findReferencedEntries(link.entryClass, link.entryID);
                    if (depends.isEmpty()) {
                        EntityModel.OrmContext.debug("Skip auto generated independent entity ''{0}''", ref.getValue().model.getQualifiedName());
                    }
                    return depends.isEmpty();
                } else {
                    return false;
                }
            }
        });
        if (!links.isEmpty() && traceResult) {
            EntityModel.OrmContext.debug(
                    "Entity ''{0}'' has references:\n{1}",
                    model.getQualifiedName(),
                    links.stream()
                            .map(link -> {
                                Entity referenced = ServiceRegistry.getInstance().lookupService(IExplorerAccessService.class).getEntity(link.entryClass, link.entryID);
                                return MessageFormat.format(
                                        "* {0}: {1}",
                                        link.isIncoming ? "Incoming" : "Outgoing",
                                        referenced != null ? referenced.getPathString() : link.entryPID
                                );
                            }).collect(Collectors.joining("\n"))
            );
        }
        return links;
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
    //TODO: Возможно уже не нужно
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
            editorPage.addAncestorListener(new AncestorListener() {
                @Override
                public void ancestorAdded(AncestorEvent event) {
                    onOpenPageView();
                }

                @Override
                public void ancestorRemoved(AncestorEvent event) {}

                @Override
                public void ancestorMoved(AncestorEvent event) {}
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

    @SuppressWarnings("unchecked")
    @Override
    public final void propertyChange(String name, Object oldValue, Object newValue) {
        if (!ISerializableType.class.isAssignableFrom(model.getPropertyType(name))) return;

        if (
                (oldValue == null && newValue != null) ||
                (oldValue != null && newValue == null) ||
                (oldValue != null && !oldValue.equals(newValue))
        ) {
            EntityModel.OrmContext.debug(
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
                    Language.get("error@unsavedprop"), 
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

    public static <E extends Entity> EntityDefinition getDefinition(Class<E> entityClass) {
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
    
    public static <E extends Entity> E  newInstance(Class<E> entityClass, EntityRef owner, String PID) {
        synchronized (entityClass) {
            final Entity found = CACHE.find(
                    entityClass,
                    owner == null ? null : owner.getId(),
                    PID != null ? PID : Language.get(entityClass, "title", new java.util.Locale("en", "US"))
            );
            if (found == null) {
                try {
                    Constructor<E> ctor = entityClass.getDeclaredConstructor(EntityRef.class, String.class);
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
                                "Localization string 'title' not defined for class ''{0}''", entityClass
                        ));
                    }
                    return created;
                } catch (InvocationTargetException | ExceptionInInitializerError | InstantiationException | IllegalAccessException e) {
                    Throwable exception = e;
                    do {
                        Logger.getLogger().error(
                                MessageFormat.format("Unable instantiate entity ''{0}'' / [Class: {1}]", PID, entityClass.getCanonicalName()), exception
                        );
                    } while ((exception = exception.getCause()) != null);
                } catch (NoSuchMethodException e) {
                    Logger.getLogger().error(
                            "Entity ''{0}'' does not have universal constructor (EntityRef<owner>, String<PID>)", 
                            entityClass.getCanonicalName()
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
        public Void execute() throws Exception {
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
