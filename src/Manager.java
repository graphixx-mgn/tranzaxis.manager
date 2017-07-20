
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
            
            model.addProperty(new PropertyHolder(Access.class, "enum", "Test enum", Access.Edit, true), Access.Select);
            model.getProperty("enum").setValue(Access.Select);
            //model.getProperty("enum").setValue(null);
            
            model.addProperty(new PropertyHolder(String.class, "string", "Test string", "TEST 1", true), Access.Select);
            model.getProperty("string").setValue("TEST 2");
//            model.getProperty("string").setValue(null);
            
            
            model.addProperty(new PropertyHolder(StringList.class, "month", "Month", new StringList(
                    Arrays.asList("January", "February", "March", "April", "May", "June", "July"), null
            ), true), Access.Select);
            model.getProperty("month").setValue("April");
            model.getProperty("month").setValue("July");
            
            model.getProperty("month").setValue(new StringList(
                    Arrays.asList("July", "August", "September", "October", "November", "December"), "July"
            ));

            for (PropertyHolder propHolder : model.getProperties(Access.Any)) {
                logger.info("Property {0}: value={1}, class={2}", propHolder.getName(), propHolder.toString(), propHolder.getValue().getClass().getCanonicalName());
            }
        } catch (Exception | Error e) {
            logger.error("Unable to init model", e);
        }
    }
    
}
