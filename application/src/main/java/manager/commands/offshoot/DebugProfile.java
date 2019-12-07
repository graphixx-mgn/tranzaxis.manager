package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.editor.IEditor;
import codex.editor.IEditorFactory;
import codex.editor.TextView;
import codex.model.ParamModel;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.Database;
import manager.nodes.Environment;
import manager.nodes.Offshoot;
import manager.type.WCStatus;
import javax.swing.*;
import java.io.File;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EntityCommand.Definition(parentCommand = RunDesigner.class)
public class DebugProfile extends EntityCommand<Offshoot> {
    private static final String    WARN_MESS = Language.get("warn@text");

    private static final String PARAM_ENVIRONMENT = "env";
    private static final String PARAM_STATUS      = "status";

    private static final String CONFIG_ROOT             = "DebuggerLaunchOptions";
    private static final String DEFAULT_PROFILE_NAME    = "Default";
    private static final String PROP_NAME_PROFILES      = "profiles";
    private static final String PROP_NAME_CURR_PROFILE  = "currProf";
    private static final String PROP_NAME_SERVER_NODE   = "server4";
    private static final String PROP_NAME_EXPLORER_NODE = "explorer4";

    private static final String PROP_NAME_JVM_ARGS      = "jvmArgs";
    private static final String PROP_NAME_CLASSPATH     = "classpath";
    private static final String PROP_NAME_TOP_LAYER     = "topLayer";
    private static final String PROP_NAME_WORK_DIR      = "workdir";
    private static final String PROP_NAME_START_ARGS    = "starterArgs";
    private static final String PROP_NAME_APP_ARGS      = "appArgs";

    private static final String PROP_NAME_SERV_URL      = "-dbUrl";
    private static final String PROP_NAME_SERV_USER     = "-user";
    private static final String PROP_NAME_SERV_PASS     = "-pwd";
    private static final String PROP_NAME_SERV_SCHEMA   = "-dbSchema";
    private static final String PROP_NAME_SERV_INSTANCE = "-instance";

    private enum NodeKind {Server, Explorer}

    public DebugProfile() {
        super(
                "set debug profile",
                Language.get("title"),
                ImageUtils.getByPath("/images/debugger.png"),
                Language.get("title"),
                (offshoot) -> offshoot.getWCStatus().isOperative()
        );

        // Parameters
        PropertyHolder propEnv = new PropertyHolder<EntityRef<Environment>, Environment>(PARAM_ENVIRONMENT, new EntityRef<>(Environment.class), true) {
            @Override
            public boolean isValid() {
                return !(isRequired() && isEmpty());
            }
        };
        PropertyHolder<AnyType, Object> propStatus = new PropertyHolder<>(PARAM_STATUS, new AnyType() {
            @Override
            public IEditorFactory<AnyType, Object> editorFactory() {
                return TextView::new;
            }
        }, false);
        setParameters(propEnv, propStatus);

        // Handlers
        propEnv.addChangeListener((name, oldValue, newValue) -> {
            if (newValue != null) {
                Environment env = (Environment) newValue;
                List<String> problematicParams = new LinkedList<>();
                problematicParams.addAll(getCommonParameters(getContext().get(0), env).entrySet().stream()
                        .filter(entry -> entry.getValue() == null)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList())
                );
                problematicParams.addAll(getAppParameters(env, NodeKind.Server).entrySet().stream()
                        .filter(entry -> entry.getValue() == null)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList())
                );
                if (!problematicParams.isEmpty()) {
                    List<String> propNames = problematicParams.stream()
                            .map(propName -> MessageFormat.format(
                                    "&nbsp;&bull;&nbsp;{0}<br>",
                                    Language.get(DebugProfile.class, propName.replaceAll("^-", "")+".title")
                            ))
                            .collect(Collectors.toList());
                    propStatus.setValue(MessageFormat.format(
                            WARN_MESS,
                            String.join("", propNames)
                    ));
                    return;
                }
            }
            propStatus.setValue(null);
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

            final Preferences serverPrefs = profileNode.node(PROP_NAME_SERVER_NODE);
            getCommonParameters(context, env).forEach(serverPrefs::put);
            serverPrefs.put(PROP_NAME_APP_ARGS, getAppParameters(env, NodeKind.Server).entrySet().stream()
                    .map(entry -> Stream.of(entry.getKey(), entry.getValue()))
                    .flatMap(x -> x)
                    .collect(Collectors.joining(" "))
            );

            final Preferences explorerPrefs = profileNode.node(PROP_NAME_EXPLORER_NODE);
            getCommonParameters(context, env).forEach(explorerPrefs::put);
            explorerPrefs.put(PROP_NAME_APP_ARGS, getAppParameters(env, NodeKind.Explorer).entrySet().stream()
                    .map(entry -> Stream.of(entry.getKey(), entry.getValue()))
                    .flatMap(x -> x)
                    .collect(Collectors.joining(" "))
            );

            context.getCommand(RunDesigner.class).execute(context, Collections.emptyMap());
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    private List<String> getProfiles(Preferences  node) {
        if (node != null) {
            final String[] profs = node.get(PROP_NAME_PROFILES, DEFAULT_PROFILE_NAME).split("\\*");
            return Arrays.asList(profs);
        } else {
            return Collections.emptyList();
        }
    }

    private Map<String, String> getCommonParameters(Offshoot offshoot, Environment environment) {
        return new LinkedHashMap<String, String>() {{
            put(PROP_NAME_JVM_ARGS,   "-server ".concat(String.join(" ", offshoot.getJvmDesigner())));
            put(PROP_NAME_CLASSPATH,  "");
            put(PROP_NAME_TOP_LAYER,  environment.getLayerUri(false));
            put(PROP_NAME_WORK_DIR,   "");
            put(PROP_NAME_START_ARGS, Stream.concat(
                    Stream.of("-workDir=" + offshoot.getLocalPath()),
                    environment.getStarterFlags(false).stream()
            ).collect(Collectors.joining(" ")));
        }};
    }

    private Map<String, String> getAppParameters(Environment environment, NodeKind kind) {
        return new LinkedHashMap<String, String>() {{
            Database db = environment.getDataBase(false);
            if (kind.equals(NodeKind.Server)) {
                put(PROP_NAME_SERV_URL,
                        db != null && db.getDatabaseUrl(false) != null ?
                                "jdbc:oracle:thin:@" + db.getDatabaseUrl(false) : null
                );
                put(PROP_NAME_SERV_USER,
                        db != null && db.getDatabaseUser(false) != null ? db.getDatabaseUser(false) : null
                );
                put(PROP_NAME_SERV_PASS,
                        db != null && db.getDatabasePassword(false) != null ? db.getDatabasePassword(false) : null
                );
                put(PROP_NAME_SERV_SCHEMA,
                        db != null && db.getDatabaseUser(false) != null ? db.getDatabaseUser(false) : null
                );
                put(PROP_NAME_SERV_INSTANCE,
                        environment.getInstanceId() != null ? environment.getInstanceId().toString() : null
                );
                put("", String.join(" ", environment.getServerFlags(false)));
            } else {
                put("", String.join(" ", environment.getExplorerFlags(false)));
            }
        }};
    }
}
