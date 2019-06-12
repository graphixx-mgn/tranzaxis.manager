package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.editor.IEditor;
import codex.model.ParamModel;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.Database;
import manager.nodes.Environment;
import manager.nodes.Offshoot;
import manager.type.WCStatus;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EntityCommand.Definition(parentCommand = RunDesigner.class)
public class DebugProfile extends EntityCommand<Offshoot> {

    private static final ImageIcon WARN_ICON = ImageUtils.getByPath("/images/warn.png");
    private static final String    WARN_MESS = Language.get("warn@text");
    private static final Iconified WARN_VAL  = new Iconified() {
        @Override
        public ImageIcon getIcon() {
            return WARN_ICON;
        }

        @Override
        public String toString() {
            return WARN_MESS;
        }
    };

    private static final String PARAM_ENVIRONMENT = "env";
    private static final String PARAM_STATUS      = "status";

    private static final String CONFIG_ROOT             = "DebuggerLaunchOptions";
    private static final String DEFAULT_PROFILE_NAME    = "Default";
    private static final String PROP_NAME_PROFILES      = "profiles";
    private static final String PROP_NAME_CURR_PROFILE  = "currProf";
    private static final String PROP_NAME_SERVER_NODE   = "server4";
    private static final String PROP_NAME_EXPLORER_NODE = "explorer4";

    private static final String PROP_NAME_JVM_ARGS   = "jvmArgs";
    private static final String PROP_NAME_CLASSPATH  = "classpath";
    private static final String PROP_NAME_TOP_LAYER  = "topLayer";
    private static final String PROP_NAME_WORK_DIR   = "workdir";
    private static final String PROP_NAME_START_ARGS = "starterArgs";
    private static final String PROP_NAME_APP_ARGS   = "appArgs";

    private enum NodeKind {Server, Explorer}

    public DebugProfile() {
        super(
                "set debug profile",
                Language.get("title"),
                ImageUtils.getByPath("/images/debugger.png"),
                Language.get("title"),
                (offshoot) -> offshoot.getWCStatus().equals(WCStatus.Succesfull)
        );

        // Parameters
        PropertyHolder propEnv = new PropertyHolder<>(PARAM_ENVIRONMENT, new EntityRef(Environment.class, entity -> {
            Environment env = (Environment) entity;
            return env.canStartExplorer() || env.canStartExplorer();
        }), true);
        PropertyHolder propStatus = new PropertyHolder<>(PARAM_STATUS, new AnyType(), false);
        setParameters(propEnv, propStatus);

        // Handlers
        propEnv.addChangeListener((name, oldValue, newValue) -> {
            if (newValue != null) {
                Environment env = (Environment) newValue;
                propStatus.setValue(env.canStartServer() ? null : WARN_VAL);
            } else {
                propStatus.setValue(null);
            }
        });
    }

    @Override
    protected void preprocessParameters(ParamModel paramModel) {
        IEditor statusEditor = paramModel.getEditor(PARAM_STATUS);
        statusEditor.setVisible(false);

        paramModel.getProperty(PARAM_STATUS).addChangeListener((name, oldValue, newValue) -> {
            statusEditor.setVisible(newValue != null);
            FocusManager.getCurrentManager().getActiveWindow().pack();
        });
    }

    @Override
    public void execute(Offshoot context, Map<String, IComplexType> params) {
        Environment env = (Environment) ((EntityRef) params.get(PARAM_ENVIRONMENT)).getValue();
        String profileName = env.getPID();

        File branchFile = new File(context.getLocalPath(), "branch.xml");
        String normalizedName  = branchFile.getPath().replace(File.separator, "$").replace(".", "$").replace("/", "$");
        String branchRoot      = UUID.nameUUIDFromBytes(normalizedName.getBytes(Charset.forName("UTF-8"))).toString();
        Preferences branchNode = Preferences.userRoot().node(CONFIG_ROOT).node(branchRoot);

        try {
            for (String profile : getProfiles(branchNode)) {
                branchNode.node(profile).removeNode();
            }
            branchNode.put(PROP_NAME_CURR_PROFILE, profileName);
            branchNode.put(PROP_NAME_PROFILES,     profileName);

            Preferences profileNode = branchNode.node(profileName);
            if (env.canStartServer()) {
                final Preferences serverPrefs = profileNode.node(PROP_NAME_SERVER_NODE);
                getParameters(context, env, NodeKind.Server).forEach(serverPrefs::put);
            }
            if (env.canStartExplorer()) {
                final Preferences explorerPrefs = profileNode.node(PROP_NAME_EXPLORER_NODE);
                getParameters(context, env, NodeKind.Explorer).forEach(explorerPrefs::put);
            }

            context.getCommand(RunDesigner.class).execute(context, Collections.emptyMap());
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    private final List<String> getProfiles(Preferences  node) {
        if (node != null) {
            final String[] profs = node.get(PROP_NAME_PROFILES, DEFAULT_PROFILE_NAME).split("\\*");
            return Arrays.asList(profs);
        } else {
            return Collections.emptyList();
        }
    }

    private Map<String, String>  getParameters(Offshoot offshoot, Environment environment, NodeKind kind) {
        return new HashMap<String, String>() {{
            Database db = environment.getDataBase(false);

            put(PROP_NAME_JVM_ARGS,   "-server ".concat(String.join(" ", offshoot.getJvmDesigner())));
            put(PROP_NAME_CLASSPATH,  "");
            put(PROP_NAME_TOP_LAYER,  environment.getLayerUri(false));
            put(PROP_NAME_WORK_DIR,   "");
            put(PROP_NAME_START_ARGS, Stream.concat(
                    Collections.singletonList("-workDir=" + environment.getBinaries().getLocalPath()).stream(),
                    environment.getStarterFlags(false).stream()
            ).collect(Collectors.joining(" ")));

            put(PROP_NAME_APP_ARGS,   String.join(" ", new LinkedList<String>(){{
                if (kind.equals(NodeKind.Server)) {
                    add("-dbUrl");
                    add("jdbc:oracle:thin:@" + db.getDatabaseUrl(false));
                    add("-user");
                    add(db.getDatabaseUser(false));
                    add("-pwd");
                    add(db.getDatabasePassword(false));
                    add("-dbSchema");
                    add(db.getDatabaseUser(false));
                    add("-instance");
                    add(environment.getInstanceId().toString());
                    addAll(environment.getServerFlags(false));
                } else {
                    addAll(environment.getExplorerFlags(false));
                }
            }}));
        }};
    }
}
