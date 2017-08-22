package codex.model;

import codex.property.PropertyHolder;
import java.util.LinkedList;
import java.util.List;

public class EntityModel extends AbstractModel {
    
    private final List<String> persistent = new LinkedList<>();

    public EntityModel(String title) {
        super(title);
    }
    
    public final void addPersistProperty(PropertyHolder propHolder, Access restriction) {
        super.addProperty(propHolder, restriction);
        persistent.add(propHolder.getName());
    }
    
}
