
import codex.log.Logger;
import codex.model.AbstractModel;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.type.StringList;
import java.util.Arrays;
import org.apache.log4j.Level;

public class Manager {
    
    private final static Logger logger = Logger.getLogger();
    static {
        logger.setLevel(Level.ALL);
    }
    
    public Manager() {
        try {
            AbstractModel model = new AbstractModel("Demo");
            model.addProperty(new PropertyHolder(Access.class, "access", "Access level", Access.Edit, true), Access.Select);
            model.addProperty(new PropertyHolder(String.class, "test", "TEST", null, true), Access.Select);
            model.addProperty(new PropertyHolder(StringList.class, "month", "Month", new StringList(
                    Arrays.asList("January", "February", "March", "April", "May"), null
            ), true), Access.Select);
            
            model.getProperty("access").setValue(Access.Select);
            model.getProperty("test").setValue("TEST");
            model.getProperty("test").setValue("TEST TEST");
            model.getProperty("month").setValue("April");
            
            
            for (PropertyHolder propHolder : model.getProperties(Access.Any)) {
                logger.info("Property ''{0}'' = ''{1}''", propHolder.getName(), propHolder.toString());
            }
        } catch (Exception | Error e) {
            logger.error("Unable to init model", e);
        }
    }
    
}
