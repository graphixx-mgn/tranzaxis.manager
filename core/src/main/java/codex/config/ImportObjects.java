package codex.config;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.log.Logger;
import codex.mask.FileMask;
import codex.mask.IMask;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.AbstractTaskView;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.EntityRef;
import codex.type.FilePath;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.xml.ConfigurationDocument;
import codex.xml.Property;
import org.apache.xmlbeans.XmlException;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ImportObjects extends EntityCommand<ConfigServiceOptions> {

    private final static ConfigStoreService CAS = (ConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);

    private static final ImageIcon ICON_IMPORT  = ImageUtils.getByPath("/images/import.png");
    private static final ImageIcon ICON_INVALID = ImageUtils.getByPath("/images/warn.png");
    private final static String    PARAM_FILE = "file";

    public ImportObjects() {
        super(
                "import config",
                Language.get("title"),
                ICON_IMPORT,
                Language.get("title"),
                null
        );
        setParameters(
                new PropertyHolder<>(PARAM_FILE, new FilePath(null).setMask(
                        new FileMask(new FileNameExtensionFilter("XML file", "xml"))
                ), true)
        );
    }

    @Override
    public void execute(ConfigServiceOptions context, Map<String, IComplexType> params) {
        ImportEntities importTask = new ImportEntities(((FilePath) params.get(PARAM_FILE)).getValue());

        Dialog dialog = new Dialog(
                null,
                ICON_IMPORT,
                Language.get("title"),
                new JPanel(new BorderLayout()) {{
                    setBorder(new EmptyBorder(5, 5, 5, 5));
                    AbstractTaskView taskView = importTask.createView(null);
                    taskView.setBorder(new CompoundBorder(
                            new LineBorder(Color.LIGHT_GRAY, 1),
                            new EmptyBorder(5, 5, 5, 5)
                    ));
                    add(taskView, BorderLayout.NORTH);
                    add(importTask.createLogPane(), BorderLayout.CENTER);
                }},
                null,
                Dialog.Default.BTN_CLOSE.newInstance()
        );
        SwingUtilities.invokeLater(() -> {
            dialog.setPreferredSize(new Dimension(610, 500));
            dialog.setResizable(false);
            ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).quietTask(importTask);
            SwingUtilities.invokeLater(() -> dialog.setVisible(true));
        });
    }


    class ImportEntities extends AbstractTask<Void> {

        private final Path filePath;
        private ConfigurationDocument xmlDoc = null;
        private Set<Catalog> updateCatalogs = new HashSet<>();
        private Map<GroupKey, Entity> importedEntities = new HashMap<>();
        private final AtomicInteger total     = new AtomicInteger(0);
        private final AtomicInteger processed = new AtomicInteger(0);

        ImportEntities(Path filePath) {
            super(Language.get(ImportObjects.class, "task@title"));
            this.filePath = filePath;
        }

        private String formatStepResult(String step, String result, List<ImportProblem> problems) {
            int htmlLength =
                    step.replaceAll("<[^>]*>","").length() +
                    result.replaceAll("<[^>]*>","").length();
            String message = " "
                    .concat(step)
                    .concat(String.join("", Collections.nCopies(80-htmlLength, ".")))
                    .concat(result);
            if (problems != null && !problems.isEmpty()) {
                message = message
                        .concat("<br>")
                        .concat(problems.stream().map(problem -> " "+problem.toString()).collect(Collectors.joining("<br>")));
            }
            return message+"<br>";
        }

        private boolean confirmedOverwrite(Entity entity, Collection<String> changedProps) {
            return MessageBox.confirmation(
                    ImageUtils.combine(
                            Entity.newPrototype(entity.getClass()).getIcon(),
                            ImageUtils.resize(ICON_INVALID, 20, 20),
                            SwingConstants.SOUTH_EAST
                    ),
                    MessageType.CONFIRMATION.toString(),
                    MessageFormat.format(
                            Language.get(ImportObjects.class, "confirm@overwrite"),
                            entity.getPID(),
                            changedProps.stream().map(
                                    propName -> "&nbsp;&bull;&nbsp;"+Language.get(entity.getClass(), propName+PropertyHolder.PROP_NAME_SUFFIX)
                            )
                            .collect(Collectors.joining("<br>"))
                    )
            );
        }

        @Override
        public Void execute() throws Exception {
            updateCatalogs.clear();
            try {
                xmlDoc = ConfigurationDocument.Factory.parse(filePath.toFile());
                Logger.getLogger().info(formatStepResult(
                        Language.get(ImportObjects.class, "step@parse"),
                        MessageFormat.format(
                                Language.get(ImportObjects.class, "parse@result"),
                                total.addAndGet(
                                        Arrays.stream(xmlDoc.getConfiguration().getCatalogArray())
                                                .mapToInt(xmlCatalog -> xmlCatalog.getEntityArray().length)
                                                .sum()
                                )
                        ),
                        null
                ));

                Arrays.asList(xmlDoc.getConfiguration().getCatalogArray()).forEach(this::importCatalog);
                updateCatalogs.forEach(Catalog::loadChildren);
            } catch (XmlException | IOException e) {
                Logger.getLogger().warn("Unable to load configuration file", e);
                MessageBox.show(MessageType.WARNING, e.getMessage());
            }

            return null;
        }

        private void importCatalog(codex.xml.Catalog xmlCatalog) {
            Map<GroupKey, List<codex.xml.Entity>> group =
                    Arrays.stream(xmlCatalog.getEntityArray())
                    .collect(Collectors.groupingBy(
                            xmlEntity -> new GroupKey(xmlEntity.getParent())
                    ));

            for (Map.Entry<GroupKey, List<codex.xml.Entity>> groupEntry : group.entrySet()) {
                for (codex.xml.Entity xmlEntity : groupEntry.getValue()) {
                    try {
                        importEntity(
                                Class.forName(xmlCatalog.getClassName()).asSubclass(Entity.class),
                                xmlEntity.getPid(),
                                xmlEntity.getProperties() == null ?
                                        Collections.emptyList() :
                                        Arrays.asList(xmlEntity.getProperties().getPropertyArray())
                        );
                    } catch (ClassNotFoundException e) {
                        System.err.println(MessageFormat.format("Class '{0}' not found", xmlCatalog.getClassName()));
                    }
                }
            }
        }

        private Entity importEntity(Class<? extends Entity> entityClass, String PID, List<codex.xml.Property> xmlProperties) {
            List<ImportProblem> problems = new LinkedList<>();

            Entity importedEntity = importedEntities.entrySet().parallelStream()
                    .filter(entry -> entry.getKey().pid.equals(PID) && entry.getKey().clazz.equals(entityClass.getCanonicalName()))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            if (importedEntity != null) {
                return importedEntity;
            }
            try {
                if (!Catalog.class.isAssignableFrom(entityClass)) {
                    String  result;
                    Catalog parent = findEntityParent(entityClass.getCanonicalName(), PID);
                    Entity  entity = prepareEntity(entityClass, PID);
                    boolean isNew  = entity.getID() == null;

                    Map<String, Object> propValues = getPropertyValues(entity, xmlProperties);
                    propValues.entrySet().removeIf(entry -> {
                        if (entry.getValue() instanceof ImportProblem) {
                            problems.add((ImportProblem) entry.getValue());
                            return true;
                        }
                        return false;
                    });
                    Map<String, Object> changedProps = propValues.entrySet().stream()
                            .filter(entry -> !Objects.equals(entry.getValue(), entity.model.getValue(entry.getKey())))
                            .collect(
                                    LinkedHashMap::new,
                                    (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                                    Map::putAll
                            );

                    if (isNew) {
                        Map<String, IComplexType> propDefinitions = getPropDefinitions(entity.model);
                        CAS.initClassInstance(entityClass, PID, propDefinitions,null).forEach(entity.model::setValue);
                        if (parent != null) {
                            updateCatalogs.add(parent);
                        }
                    }

                    if (isNew) {
                        result = Language.get(ImportObjects.class, "result@new");
                        updateEntity(entity, changedProps);
                        problems.addAll(checkEntityValues(entity, changedProps, xmlProperties));
                        entity.model.commit(true);

                    } else {
                        if (changedProps.isEmpty()) {
                            result = Language.get(ImportObjects.class, "result@skip");
                            problems.clear();

                        } else {
                            updateEntity(entity, changedProps);
                            if (entity.model.getChanges().isEmpty()) {
                                result = Language.get(ImportObjects.class, "result@skip");
                                problems.clear();

                            } else if (confirmedOverwrite(entity, entity.model.getChanges())) {
                                result = Language.get(ImportObjects.class, "result@update");
                                problems.addAll(checkEntityValues(entity, changedProps, xmlProperties));
                                entity.model.commit(true);

                            } else {
                                result = Language.get(ImportObjects.class, "result@cancel");
                                entity.model.rollback();
                                problems.clear();
                            }
                        }
                    }

                    importedEntities.put(new GroupKey(entityClass.getCanonicalName(), PID), entity);
                    processed.addAndGet(1);
                    String stepName = MessageFormat.format(
                            Language.get(ImportObjects.class, "step@import"),
                            String.format("%2d", processed.get()),
                            parent == null ? "" : parent.getPID(),
                            entity.getPID()
                    );
                    Logger.getLogger().info(formatStepResult(stepName, result, problems));
                    setProgress(100 * processed.get() / total.get(), getDescription());
                    return entity;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private Entity prepareEntity(Class<? extends Entity> entityClass, String PID) {
            Entity newEntity = Entity.newInstance(entityClass, null, PID);
            if (newEntity.getID() == null) {
                codex.xml.Entity xmlEntity = findEntity(entityClass.getCanonicalName(), PID);
                if (xmlEntity != null) {
                    List<codex.xml.Property> softUniqueKeys = getSoftUniqueKeys(
                            newEntity,
                            Arrays.asList(xmlEntity.getProperties().getPropertyArray())
                    );
                    if (!softUniqueKeys.isEmpty()) {
                        Set<Entity> foundByUnqKeys = findEntityByUniqueKeys(entityClass, softUniqueKeys);
                        if (foundByUnqKeys.size() == 1) {
                            return foundByUnqKeys.iterator().next();
                        }
                    }
                }
            }
            return newEntity;
        }

        private void updateEntity(Entity entity, Map<String, Object> changedProps) throws Exception {
            final Set<String> recurseChanges = new HashSet<>();
            IPropertyChangeListener restoreDeps = (name, oldValue, newValue) -> {
                if (oldValue != null && oldValue.equals(changedProps.get(name)) && !recurseChanges.contains(name)) {
                    recurseChanges.add(name);
                    entity.model.setValue(name, oldValue);
                }
            };
            changedProps.keySet().forEach(propName -> entity.model.getProperty(propName).addChangeListener(restoreDeps));
            changedProps.forEach(entity.model::setValue);
            changedProps.keySet().forEach(propName -> entity.model.getProperty(propName).removeChangeListener(restoreDeps));
        }

        @SuppressWarnings("unchecked")
        private List<ImportProblem> checkEntityValues(Entity entity, Map<String, Object> changedProps, List<codex.xml.Property> xmlProperties) {
            List<ImportProblem> problems = new LinkedList<>();
            changedProps.forEach((propName, propValue) -> {
                if (entity.model.getProperty(propName).getOwnPropValue().getMask() != null) {
                    IMask mask = entity.model.getProperty(propName).getOwnPropValue().getMask();
                    if (!mask.verify(propValue)) {
                        problems.add(new CheckMaskError(
                                Language.get(entity.getClass(), propName+PropertyHolder.PROP_NAME_SUFFIX),
                                xmlProperties.stream()
                                        .filter(property -> property.getName().equals(propName))
                                        .map(Property::getValue)
                                        .findFirst().get()
                        ));
                        entity.model.rollback(propName);
                    }
                }
            });
            return problems;
        }

        private List<codex.xml.Property> getSoftUniqueKeys(Entity entity, List<codex.xml.Property> xmlProperties) {
            return xmlProperties.stream()
                    .filter(xmlProperty -> entity.model.isPropUnique(xmlProperty.getName()))
                    .collect(Collectors.toList());
        }

        private Set<Entity> findEntityByUniqueKeys(Class<? extends Entity> entityClass, List<codex.xml.Property> uniqueKeys) {
            Set<Entity> found = new HashSet<>();
            List<Entity> existEntities = CAS.readCatalogIDs(entityClass).stream()
                    .map(ID -> EntityRef.build(entityClass, ID).getValue())
                    .collect(Collectors.toList());

            for (codex.xml.Property uniqueKey : uniqueKeys) {
                for (Entity entity : existEntities) {
                    if (uniqueKey.getValue().equals(entity.model.getProperty(uniqueKey.getName()).getOwnPropValue().toString())) {
                        found.add(entity);
                    }
                }
            }
            return found;
        }

        private Map<String, IComplexType> getPropDefinitions(EntityModel model) {
            return model.getProperties(Access.Any)
                    .stream()
                    .filter((propName) -> !model.isPropertyDynamic(propName) || EntityModel.ID.equals(propName))
                    .collect(Collectors.toMap(
                            propName -> propName,
                            propName -> model.getProperty(propName).getPropValue()
                    ));
        }

        private Map<String, Object> getPropertyValues(Entity entity, List<codex.xml.Property> xmlProperties) {
            Map<String, Object> values = new LinkedHashMap<>();
            xmlProperties.forEach(xmlProperty -> {
                String propName = xmlProperty.getName();
                if (EntityRef.class.isAssignableFrom(entity.model.getPropertyType(propName))) {
                    EntityRef ref = (EntityRef) entity.model.getProperty(propName).getOwnPropValue();
                    Class<? extends Entity> refEntityClass = ref.getEntityClass();

                    Entity refEntity = getReference(
                            refEntityClass,
                            xmlProperty.getOwner(),
                            xmlProperty.getValue()
                    );
                    if (refEntity != null/* && refEntity.getID() != null*/) {
                        values.put(propName, refEntity);
                    } else {
                        values.put(propName, new RefNotFound(
                                Language.get(entity.getClass(), propName+PropertyHolder.PROP_NAME_SUFFIX),
                                xmlProperty.getValue())
                        );
                    }
                } else {
                    try {
                        Object defValue = entity.model.getProperty(propName).getOwnPropValue().getValue();
                        entity.model.getProperty(propName).getOwnPropValue().valueOf(xmlProperty.getValue());
                        Object newValue = entity.model.getProperty(propName).getOwnPropValue().getValue();
                        entity.model.getProperty(propName).getOwnPropValue().setValue(defValue);
                        values.put(propName, newValue);
                    } catch (Exception e) {
                        values.put(propName, new InvalidValue(
                                Language.get(entity.getClass(), propName+PropertyHolder.PROP_NAME_SUFFIX),
                                xmlProperty.getValue()
                        ));
                    }
                }
            });
            return values;
        }

        private Entity getReference(Class<? extends Entity> entityClass, codex.xml.Ref xmlOwner, String PID) {
            Entity owner = null;
            if (xmlOwner != null) {
                owner = getOwner(xmlOwner);
            }

            if (CAS.isInstanceExists(entityClass, PID, owner == null ? null : owner.getID())) {
                Entity ref = Entity.newInstance(entityClass, owner == null ? null : owner.toRef(), PID);
                //if (ref.getID() != null) {
                    return ref;
                //}
            }
            codex.xml.Entity foundXmlEntity = findEntity(entityClass.getCanonicalName(), PID);
            if (foundXmlEntity != null) {
                return importEntity(
                        entityClass,
                        PID,
                        foundXmlEntity.getProperties() == null ?
                                Collections.emptyList() :
                                Arrays.asList(foundXmlEntity.getProperties().getPropertyArray())
                );
            }
            if (Entity.getDefinition(entityClass).autoGenerated()) {
                return Entity.newInstance(entityClass, owner == null ? null : owner.toRef(), PID);
            }
            return null;
        }

        private Entity getOwner(codex.xml.Ref xmlOwner) {
            try {
                Class entityClass = Class.forName(xmlOwner.getClassName());
                return CAS.readCatalogIDs(entityClass).parallelStream()
                        .filter(entityId -> CAS.readClassInstance(entityClass, entityId).get(EntityModel.PID).equals(xmlOwner.getPid()))
                        .map(entityId -> EntityRef.build(entityClass, entityId).getValue())
                        .findFirst().orElseGet(() -> {
                            codex.xml.Entity xmlEntity = findEntity(xmlOwner.getClassName(), xmlOwner.getPid());
                            return xmlEntity == null ? null : importEntity(entityClass, xmlOwner.getPid(), Arrays.asList(xmlEntity.getProperties().getPropertyArray()));
                        });
            } catch (ClassNotFoundException e) {
                System.err.println(MessageFormat.format("Class '{0}' not found", xmlOwner.getClassName()));
                return null;
            }
        }

        private Catalog findEntityParent(String className, String pid) {
            codex.xml.Entity xmlEntity = findEntity(className, pid);
            if (xmlEntity != null && xmlEntity.getParent() != null) {
                try {
                    return Entity.newInstance(
                            Class.forName(xmlEntity.getParent().getClassName()).asSubclass(Entity.class),
                            null, xmlEntity.getParent().getPid()
                    );
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        private codex.xml.Entity findEntity(String className, String PID) {
            if (xmlDoc != null) {
                return Arrays.asList(xmlDoc.getConfiguration().getCatalogArray()).stream()
                        .filter(xmlCatalog -> xmlCatalog.getClassName().equals(className))
                        .map(xmlCatalog -> Arrays.asList(xmlCatalog.getEntityArray()))
                        .flatMap(Collection::stream)
                        .filter(xmlEntity -> xmlEntity.getPid().equals(PID))
                        .findFirst().orElse(null);
            }
            return null;
        }

        @Override
        public void finished(Void result) {

        }
    }


    enum Severity {
        Warning(Language.get(ImportObjects.class, "problem@warn")),
        Error(Language.get(ImportObjects.class, "problem@error"));

        final String format;
        Severity(String format) {
            this.format = format;
        }
    }

    abstract class ImportProblem {
        final String propName, propValue;
        final Severity severity;

        ImportProblem(Severity severity, String propName, String propValue) {
            this.propName  = propName;
            this.propValue = propValue;
            this.severity  = severity;
        }
    }

    class InvalidValue extends ImportProblem {
        InvalidValue(String propName, String propValue) {
            super(Severity.Warning, propName, propValue);
        }
        @Override
        public String toString() {
            return MessageFormat.format(
                    severity.format,
                    MessageFormat.format(Language.get(ImportObjects.class, "problem@invalid"), propName, propValue)
            );
        }
    }

    class RefNotFound extends ImportProblem {
        RefNotFound(String propName, String propValue) {
            super(Severity.Warning, propName, propValue);
        }
        @Override
        public String toString() {
            return MessageFormat.format(
                    severity.format,
                    MessageFormat.format(Language.get(ImportObjects.class, "problem@notfound"), propName, propValue)
            );
        }
    }

    class CheckMaskError extends ImportProblem {
        CheckMaskError(String propName, String propValue) {
            super(Severity.Warning, propName, propValue);
        }
        @Override
        public String toString() {
            return MessageFormat.format(
                    severity.format,
                    MessageFormat.format(Language.get(ImportObjects.class, "problem@notaccepted"), propName, propValue)
            );
        }
    }


    class GroupKey {
        final String clazz, pid;

        GroupKey(String clazz, String PID) {
            this.clazz = clazz;
            this.pid   = PID;
        }

        GroupKey(codex.xml.Ref parent) {
            this.clazz = parent != null ? parent.getClassName() : null;
            this.pid   = parent != null ? parent.getPid() : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroupKey groupKey = (GroupKey) o;
            return Objects.equals(clazz, groupKey.clazz) &&
                    Objects.equals(pid, groupKey.pid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, pid);
        }
    }
}