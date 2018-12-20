package manager.nodes;

import codex.model.Access;
import codex.model.Catalog;
import codex.type.ArrStr;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.List;

public class EnvironmentRoot extends Catalog {
    
    public final static String PROP_JVM_SERVER   = "jvmServer";
    public final static String PROP_JVM_EXPLORER = "jvmExplorer";
    
    public EnvironmentRoot() {
        super(null, ImageUtils.getByPath("/images/system.png"), "title", Language.get("desc"));
        
        // Properties
        model.addUserProp(PROP_JVM_SERVER,   new ArrStr("-Xmx2G"), false, Access.Select);
        model.addUserProp(PROP_JVM_EXPLORER, new ArrStr("-Xmx1G"), false, Access.Select);
    }
    
    public final List<String> getJvmServer() {
        return (List<String>) model.getValue(PROP_JVM_SERVER);
    }
    
    public final List<String> getJvmExplorer() {
        return (List<String>) model.getValue(PROP_JVM_EXPLORER);
    }
    
    public final void setJvmServer(List<String> value) {
        model.setValue(PROP_JVM_SERVER, value);
    }
    
    public final void setJvmExplorer(List<String> value) {
        model.setValue(PROP_JVM_EXPLORER, value);
    }

    @Override
    public Class getChildClass() {
        return Environment.class;
    }
    
}
