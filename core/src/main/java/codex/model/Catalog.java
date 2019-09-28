package codex.model;

import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.EntityRef;
import codex.utils.Language;
import org.atteo.classindex.ClassIndex;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.swing.ImageIcon;

/**
 * Католог сущностей проводника. Основное отличие от {@link Entity} - каталог 
 * должен существовать в дереве в единственном экземпляре.
 */
public abstract class Catalog extends Entity {

    private final static ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    public Catalog(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        if (parent != null && getChildClass() != null) {
            loadChildren();
        }
    }
    
    @Override
    public abstract Class<? extends Entity> getChildClass();

    public void loadChildren() {
        Map<Class<? extends Entity>, Collection<String>> childrenPIDs = getChildrenPIDs();
        if (!childrenPIDs.isEmpty()) {
            TES.quietTask(new LoadChildren(childrenPIDs) {
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

    /**
     * Возвращает список классов, разрешенных для создания и загрузки в данном каталоге.
     * Каждый из этих классов наследуется от одного класса {@link ClassCatalog}.
     */
    public final List<Class<? extends Entity>> getClassCatalog() {
        Class<? extends Entity> childClass = getChildClass();
        if (childClass.isAnnotationPresent(ClassCatalog.Definition.class)) {
            return StreamSupport.stream(ClassIndex.getSubclasses(codex.model.ClassCatalog.class).spliterator(), false)
                    .filter(aClass -> childClass.isAssignableFrom(aClass) && !Modifier.isAbstract(aClass.getModifiers()))
                    .collect(Collectors.toList());
        } else {
            return Collections.singletonList(childClass);
        }
    }

    protected Map<Class<? extends Entity>, Collection<String>> getChildrenPIDs() {
        return getClassCatalog().stream()
                .collect(Collectors.toMap(
                        catalogClass -> catalogClass,
                        catalogClass -> {
                            Entity owner = this.getOwner();
                            Integer ownerId = owner == null ? null : owner.getID();
                            final IConfigStoreService CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
                            return CAS.readCatalogEntries(ownerId, catalogClass).values();
                        }
                ));

    }
    
    private class LoadChildren extends AbstractTask<Void> {
        
        private final Map<Class<? extends Entity>, Collection<String>> childrenPIDs;

        LoadChildren(Map<Class<? extends Entity>, Collection<String>> childrenPIDs) {
            super(MessageFormat.format(
                    Language.get(Catalog.class, "task@load"),
                    getParent() != null ? Catalog.this.getPathString() : Catalog.this.getPID()
            ));
            this.childrenPIDs = childrenPIDs;
        }

        @Override
        public Void execute() throws Exception {
            EntityRef ownerRef = Entity.findOwner(Catalog.this);
            childrenPIDs.forEach((catalogClass, PIDs) -> PIDs.forEach(PID -> {
                Entity instance = Entity.newInstance(catalogClass, ownerRef, PID);
                if (!childrenList().contains(instance)) {
                    insert(instance);
                }
            }));
            return null;
        }

        @Override
        public void finished(Void result) {}
    
    }

}
