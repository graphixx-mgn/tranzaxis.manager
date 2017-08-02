
import codex.log.Logger;
import codex.model.AbstractModel;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.type.StringList;
import java.io.File;
import java.nio.file.Path;
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
//            model.getProperty("enum").setValue(null);

            model.addProperty(new PropertyHolder(String.class, "string", "Test string", "TEST 1", true), Access.Select);
            model.getProperty("string").setValue("TEST 2");
//            model.getProperty("string").setValue(null);
            
            model.addProperty(new PropertyHolder(Integer.class, "integer", "Test integer", 1000, true), Access.Select);
            model.getProperty("integer").setValue(1500);
//            model.getProperty("integer").setValue(null);
            
            model.addProperty(new PropertyHolder(Path.class, "path", "Test path", new File("C:\\temp").toPath(), true), Access.Select);
            model.getProperty("path").setValue(new File("C:\\temp\\temp").toPath());
//            model.getProperty("path").setValue(null);

            model.addProperty(new PropertyHolder(StringList.class, "month", "Month", new StringList(
                    Arrays.asList("January", "February", "March", "April", "May", "June", "July"), null
            ), true), Access.Select);
            model.getProperty("month").setValue("April");
            model.getProperty("month").setValue("July");
            model.getProperty("month").setValue(new StringList(
                    Arrays.asList("July", "August", "September", "October", "November", "December"), "July"
            ));

            for (PropertyHolder propHolder : model.getProperties(Access.Edit)) {
                logger.info("Property {0}: [{1}]", propHolder.getName(), propHolder.toString());
            }
        } catch (Exception | Error e) {
            logger.error("Unable to init model", e);
        }
    }
    
}
