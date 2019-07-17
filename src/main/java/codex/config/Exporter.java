package codex.config;

import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.type.EntityRef;
import codex.xml.ConfigurationDocument;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Exporter {

    private Map<Class<? extends Entity>, List<? extends Entity>> entitiesMap;

    final void loadEntities(Map<Class<? extends Entity>, List<? extends Entity>> entitiesMap) {
        this.entitiesMap = entitiesMap;
    }

    protected Predicate<Class<? extends Entity>> getClassFilter() {
        return entityClass -> true;
    }

    protected Predicate<Entity> getEntityFilter() {
        return entity -> true;
    }

    public Map<Class<? extends Entity>, List<? extends Entity>> getEntities() {
        return Collections.unmodifiableMap(entitiesMap);
    }

    public ConfigurationDocument getConfiguration() {
        ConfigurationDocument xmlDoc = ConfigurationDocument.Factory.newInstance();
        codex.xml.Configuration xmlConfig = xmlDoc.addNewConfiguration();

        entitiesMap.forEach((catalog, entities) -> {
            if (entities.size() > 0) {
                codex.xml.Catalog xmlCatalog = xmlConfig.addNewCatalog();
                xmlCatalog.setClassName(entities.get(0).getClass().getCanonicalName());

                entities.forEach(entity -> {
                    codex.xml.Entity xmlEntity = xmlCatalog.addNewEntity();
                    xmlEntity.setPid(entity.getPID());

                    if (entity.getParent() != null) {
                        Entity parent = (Entity) entity.getParent();
                        codex.xml.Ref xmlParent = xmlEntity.addNewParent();
                        xmlParent.setClassName(parent.getClass().getCanonicalName());
                        xmlParent.setPid(parent.getPID());
                    }

                    codex.xml.Entity.Properties xmlProps = xmlEntity.addNewProperties();
                    entity.model.getProperties(Access.Edit).stream()
                            .filter(propName -> !entity.model.isPropertyDynamic(propName) && !EntityModel.PID.equals(propName))
                            .forEach(propName -> {
                                Object value = entity.model.getValue(propName);
                                if (value != null) {
                                    codex.xml.Property xmlProperty = xmlProps.addNewProperty();
                                    xmlProperty.setName(propName);
                                    if (EntityRef.class.isAssignableFrom(entity.model.getPropertyType(propName))) {
                                        Entity refEntity = (Entity) entity.model.getValue(propName);
                                        xmlProperty.setValue(refEntity.getPID());
                                        if (refEntity.getOwner() != null) {
                                            codex.xml.Ref xmlOwner = xmlProperty.addNewOwner();
                                            xmlOwner.setClassName(refEntity.getOwner().getClass().getCanonicalName());
                                            xmlOwner.setPid(refEntity.getOwner().getPID());
                                        }
                                    } else {
                                        xmlProperty.setValue(entity.model.getProperty(propName).getOwnPropValue().toString());
                                    }
                                }
                            });
                });
            }
        });
        return xmlDoc;
    }

}
