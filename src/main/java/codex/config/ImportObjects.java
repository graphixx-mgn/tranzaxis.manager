package codex.config;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.log.Logger;
import codex.mask.FileMask;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityModel;
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
import java.util.stream.Stream;

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
            dialog.setVisible(true);
        });
    }


    class ImportEntities extends AbstractTask<Void> {

        private final Path filePath;
        private ConfigurationDocument xmlDoc = null;
        private Set<Catalog> updateCatalogs = new HashSet<>();
        private Set<Entity>  importedEntities = new HashSet<>();
        private Set<Entity>  updatedEntities  = new HashSet<>();
        private final AtomicInteger total     = new AtomicInteger(0);
        private final AtomicInteger processed = new AtomicInteger(0);

        ImportEntities(Path filePath) {
            super(Language.get(ImportObjects.class, "task@title"));
            this.filePath = filePath;
        }

        private String fillStepResult(String step, String result, Throwable error) {
            int htmlLength = result.replaceAll("\\<[^>]*>","").length();
            return ""
                    .concat(step)
                    .concat(String.join("", Collections.nCopies(80-step.length()-htmlLength, ".")))
                    .concat(result)
                    .concat(error == null ? "" : MessageFormat.format("\n   &#9888; {0}", error.getMessage()));
        }

        @Override
        public Void execute() throws Exception {
            updateCatalogs.clear();
            try {
                xmlDoc = ConfigurationDocument.Factory.parse(filePath.toFile());

                Logger.getLogger().info(fillStepResult(
                        Language.get(ImportObjects.class, "step@parse"),
                        MessageFormat.format(
                                Language.get(ImportObjects.class, "step@parse.result"),
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
            Entity importedEntity = importedEntities.parallelStream()
                    .filter(entity -> entity.getClass().equals(entityClass) && entity.getPID().equals(PID))
                    .findFirst().orElse(null);
            if (importedEntity != null) {
                return importedEntity;
            }

            try {
                if (!Catalog.class.isAssignableFrom(entityClass)) {
                    processed.addAndGet(1);

                    Catalog entityParent = findEntityParent(entityClass.getCanonicalName(), PID);
                    Entity  entity = Entity.newInstance(entityClass, null, PID);
                    String  step   = MessageFormat.format(
                            Language.get(ImportObjects.class, "step@import"),
                            String.format("%2d", processed.get()),
                            entityParent.getPID(),
                            entity.getPID()
                    );

                    if (entity.getID() == null) {
                        updateCatalogs.add(entityParent);

                        Map<String, IComplexType> propDefs = getPropDefinitions(entity.model);
                        CAS.initClassInstance(entityClass, PID, propDefs,null).forEach(entity.model::setValue);

                        Map<String, Object> propVals = getPropertyValues(entity, xmlProperties);
                        propVals.forEach(entity.model::setValue);
                        entity.model.commit(true);

                        String result = fillStepResult(step, Language.get(ImportObjects.class, "step@import.new"), null);
                        List<String> refProps = xmlProperties.stream()
                                .filter(xmlProperty ->
                                        EntityRef.class.isAssignableFrom(propDefs.get(xmlProperty.getName()).getClass()) &&
                                        propVals.get(xmlProperty.getName()) == null
                                )
                                .map(Property::getName)
                                .collect(Collectors.toList());
                        if (!refProps.isEmpty()) {
                            result = Stream.concat(
                                    Stream.of(result),
                                    refProps.stream()
                                        .map(refPropName -> fillStepResult(
                                                MessageFormat.format(
                                                        Language.get(ImportObjects.class, "step@ref.title"),
                                                        Language.get(entityClass, refPropName+PropertyHolder.PROP_NAME_SUFFIX)
                                                ),
                                                MessageFormat.format(
                                                        Language.get(ImportObjects.class, "step@ref.notfound"),
                                                        xmlProperties.stream()
                                                                .filter(xmlProperty -> xmlProperty.getName().equals(refPropName))
                                                                .findFirst().get().getValue()
                                                ),
                                                null
                                        ))
                            ).collect(Collectors.joining("\n"));
                        }
                        Logger.getLogger().info(result);
                        importedEntities.add(entity);

                    } else {
                        Map<String, Object> changes = getPropertyValues(entity, xmlProperties).entrySet().stream()
                                .filter(entry -> !Objects.equals(entry.getValue(), entity.model.getValue(entry.getKey())))
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue
                                ));

                        if (!changes.isEmpty()) {
                            if (MessageBox.confirmation(
                                    ImageUtils.combine(
                                            Entity.newPrototype(entityClass).getIcon(),
                                            ImageUtils.resize(ICON_INVALID, 20, 20),
                                            SwingConstants.SOUTH_EAST
                                    ),
                                    MessageType.CONFIRMATION.toString(),
                                    MessageFormat.format(
                                            Language.get(ImportObjects.class, "step@import.overwrite"),
                                            PID,
                                            changes.keySet().stream().map(
                                                    propName -> "&nbsp;&bull;&nbsp;"+Language.get(entityClass, propName+PropertyHolder.PROP_NAME_SUFFIX)
                                            )
                                            .collect(Collectors.joining("<br>"))
                                    )
                            )) {
                                changes.forEach(entity.model::setValue);
                                entity.model.commit(true);

                                String result = fillStepResult(step, Language.get(ImportObjects.class, "step@import.update"), null);
                                List<String> refProps = xmlProperties.stream()
                                        .filter(xmlProperty ->
                                                EntityRef.class.isAssignableFrom(entity.model.getPropertyType(xmlProperty.getName())) &&
                                                        changes.get(xmlProperty.getName()) == null &&
                                                        xmlProperty.isSetValue() && (
                                                        entity.model.getValue(xmlProperty.getName()) == null ||
                                                                !((Entity) entity.model.getValue(xmlProperty.getName())).getPID().equals(xmlProperty.getValue())
                                                )
                                        )
                                        .map(Property::getName)
                                        .collect(Collectors.toList());
                                if (!refProps.isEmpty()) {
                                    result = Stream.concat(
                                            Stream.of(result),
                                            refProps.stream()
                                                    .map(refPropName -> fillStepResult(
                                                            MessageFormat.format(
                                                                    Language.get(ImportObjects.class, "step@ref.title"),
                                                                    Language.get(entityClass, refPropName+PropertyHolder.PROP_NAME_SUFFIX)
                                                            ),
                                                            MessageFormat.format(
                                                                    Language.get(ImportObjects.class, "step@ref.notfound"),
                                                                    xmlProperties.stream()
                                                                            .filter(xmlProperty -> xmlProperty.getName().equals(refPropName))
                                                                            .findFirst().get().getValue()
                                                            ),
                                                            null
                                                    ))
                                    ).collect(Collectors.joining("\n"));
                                }
                                Logger.getLogger().info(result);
                                updatedEntities.add(entity);
                            } else {
                                Logger.getLogger().info(fillStepResult(step, Language.get(ImportObjects.class, "step@import.cancel"), null));
                            }
                        } else {
                            Logger.getLogger().info(fillStepResult(step, Language.get(ImportObjects.class, "step@import.skip"), null));
                        }
                    }
                    setProgress(100 * processed.get() / total.get(), getDescription());
                    return entity;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            return null;
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
                    if (refEntity != null && refEntity.getID() != null) {
                        values.put(propName, refEntity);
                    }
                } else {
                    Object defValue = entity.model.getProperty(propName).getOwnPropValue().getValue();
                    entity.model.getProperty(propName).getOwnPropValue().valueOf(xmlProperty.getValue());
                    Object newValue = entity.model.getProperty(propName).getOwnPropValue().getValue();
                    entity.model.getProperty(propName).getOwnPropValue().setValue(defValue);
                    values.put(propName, newValue);
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
                if (ref.getID() != null) {
                    return ref;
                }
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


    class GroupKey {

        final String clazz, pid;

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