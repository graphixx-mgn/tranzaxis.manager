package codex.model;

import codex.log.Logger;
import codex.property.PropertyHolder;
import java.util.LinkedList;
import java.util.List;

public class AbstractModel {
    
    final List<PropertyHolder> properties = new LinkedList<>();
    
    public AbstractModel() {
        Logger.getLogger().debug("AbstractModel initialized");
        properties.add(new PropertyHolder(String.class, this.getClass().getCanonicalName()+"@Title", "Title", "Test", true));
    }
    
    
}
