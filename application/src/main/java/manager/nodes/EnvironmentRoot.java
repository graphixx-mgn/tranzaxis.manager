package manager.nodes;

import codex.mask.DataSetMask;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.supplier.IDataSupplier;
import codex.supplier.RowSelector;
import codex.type.ArrStr;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.Runtime;
import java.util.*;
import java.util.stream.Collectors;

public class EnvironmentRoot extends Catalog {

    public final static String   PROP_JVM_NAME    = "jvm@name";
    public final static String   PROP_JVM_VERSION = "jvm@version";
    public final static String   PROP_JVM_PATH    = "jvm@path";
    public final static String[] PROP_JVM_ATTRS   = new String[] {PROP_JVM_NAME, PROP_JVM_VERSION, PROP_JVM_PATH};

    public final static String PROP_JVM_SOURCE    = "jvmSource";
    public final static String PROP_JVM_SERVER    = "jvmServer";
    public final static String PROP_JVM_EXPLORER  = "jvmExplorer";
    public final static String PROP_STARTER_OPTS  = "starterOpts";
    public final static String PROP_SERVER_OPTS   = "serverOpts";
    public final static String PROP_EXPLORER_OPTS = "explorerOpts";

    private final DataSetMask jvmSelector = new DataSetMask(
            RowSelector.Multiple.newInstance(IDataSupplier.MapSupplier.build(
                    Arrays.stream(PROP_JVM_ATTRS)
                            .map(optName -> Language.get(EnvironmentRoot.class, optName))
                            .collect(Collectors.toCollection(LinkedList::new))
                            .toArray(new String[]{}),
                    () -> Runtime.JVM.list.get().stream()
                            .map(javaInfo -> new Vector<String>() {{
                                add(javaInfo.name);
                                add(javaInfo.version);
                                add(javaInfo.path);
                            }}).collect(Collectors.toList())
            )),
            "{0} ({1})"
    );
    
    public EnvironmentRoot() {
        super(null, ImageUtils.getByPath("/images/system.png"), null, Language.get("desc"));
        
        // Properties
        model.addUserProp(PROP_JVM_SOURCE,   new ArrStr().setMask(jvmSelector), false, Access.Select);
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
        model.addPropertyGroup(Language.get("group@jvm"), PROP_JVM_SOURCE, PROP_JVM_SERVER, PROP_JVM_EXPLORER);
        model.addPropertyGroup(Language.get("group@app"), PROP_STARTER_OPTS, PROP_SERVER_OPTS, PROP_EXPLORER_OPTS);
    }

    @SuppressWarnings("unchecked")
    public final Map<String, String> getJvmSource() {
        List<String> values = (List<String>) model.getValue(PROP_JVM_SOURCE);
        if (values.isEmpty()) {
            return Collections.emptyMap();
        } else {
            return values.stream().collect(Collectors.toMap(
                    attribute -> PROP_JVM_ATTRS[values.indexOf(attribute)],
                    attribute -> attribute
            ));
        }
    }

    @SuppressWarnings("unchecked")
    public final List<String> getJvmServer() {
        return (List<String>) model.getValue(PROP_JVM_SERVER);
    }
    
    @SuppressWarnings("unchecked")
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
    public Class<? extends Entity> getChildClass() {
        return Environment.class;
    }
    
}
