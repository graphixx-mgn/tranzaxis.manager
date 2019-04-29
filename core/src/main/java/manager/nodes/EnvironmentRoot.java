package manager.nodes;

import codex.model.Access;
import codex.model.Catalog;
import codex.type.ArrStr;
import codex.utils.ImageUtils;
import codex.utils.Language;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EnvironmentRoot extends Catalog {
    
    public final static String PROP_JVM_SERVER    = "jvmServer";
    public final static String PROP_JVM_EXPLORER  = "jvmExplorer";
    public final static String PROP_STARTER_OPTS  = "starterOpts";
    public final static String PROP_SERVER_OPTS   = "serverOpts";
    public final static String PROP_EXPLORER_OPTS = "explorerOpts";
    
    public EnvironmentRoot() {
        super(null, ImageUtils.getByPath("/images/system.png"), "title", Language.get("desc"));
        
        // Properties
        model.addUserProp(PROP_JVM_SERVER,   new ArrStr("-Xmx2G"), false, Access.Select);
        model.addUserProp(PROP_JVM_EXPLORER, new ArrStr("-Xmx1G"), false, Access.Select);
        model.addUserProp(PROP_STARTER_OPTS, new ArrStr(Collections.singletonList("-disableHardlinks")), true, Access.Select);
        model.addUserProp(PROP_SERVER_OPTS,  new ArrStr(Arrays.asList(
                "-switchEasVerChecksOff",
                "-useLocalJobExecutor",
                "-ignoreDdsWarnings",
                "-development",
                "-autostart"
        )), true, Access.Select);
        model.addUserProp(PROP_EXPLORER_OPTS,  new ArrStr(Arrays.asList(
                "-language=en",
                "-development"
        )), true, Access.Select);

        // Property settings
        model.addPropertyGroup(Language.get("group@jvm"), PROP_JVM_SERVER, PROP_JVM_EXPLORER);
        model.addPropertyGroup(Language.get("group@app"), PROP_STARTER_OPTS, PROP_SERVER_OPTS, PROP_EXPLORER_OPTS);
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
