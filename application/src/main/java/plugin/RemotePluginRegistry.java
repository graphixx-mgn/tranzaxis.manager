package plugin;

import codex.command.EditorCommand;
import codex.log.Logger;
import codex.mask.IMask;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityDefinition;
import codex.model.PropSetEditor;
import codex.property.PropertyHolder;
import codex.type.Enum;
import codex.type.Iconified;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.NetTools;
import manager.upgrade.UpgradeService;
import manager.xml.Version;
import manager.xml.VersionsDocument;
import org.apache.http.client.utils.URIBuilder;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.xmlbeans.XmlException;
import org.atteo.classindex.ClassIndex;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@EntityDefinition(icon="/images/server.png")
class RemotePluginRegistry extends PluginRegistry implements IPluginRegistry {

    private final static Integer CONNECT_TIMEOUT = 1500;

    private final static ImageIcon    ICON_AUTH = ImageUtils.getByPath("/images/auth.png");
    private final static List<String> GROUP_ID  = Arrays.asList("com", "compassplus");
    private final static List<String> RQ_STATUS = Arrays.asList("service", "rest", "v1", "status");
    private final static List<String> RQ_SEARCH = Arrays.asList("service", "rest", "v1", "search");

    private final static String PROP_ADDR  = "repoAddr";
    private final static String PROP_AUTH  = "repoAuth";
    private final static String PROP_USER  = "repoUser";
    private final static String PROP_PASS  = "repoPass";

    private static RemotePluginRegistry getInstance() {
        // Retrieve cached object
        return Entity.newInstance(RemotePluginRegistry.class, null, Language.get(RemotePluginRegistry.class, "title"));
    }

    private final NetTools.ProxyHandler proxyHandler;

    private RemotePluginRegistry() {
        super(Language.get("title"));

        // Properties
        PropertyHolder<Str, String> repoAddr = new PropertyHolder<Str, String>(
                PROP_ADDR,
                (Str) new Str().setMask(new IMask<String>() {
                    @Override
                    public boolean verify(String value) {
                        return NetTools.checkUrl(value) && checkServer(URI.create(value));
                    }

                    @Override
                    public String getErrorHint() {
                        return Language.get(PluginCatalog.class, "plugin.repository.invalid");
                    }
                }),
                true
        ) {
            @Override
            public boolean isValid() {
                return super.isValid() && getPropValue().getMask().verify(getPropValue().getValue());
            }
        };
        PropertyHolder<Enum<RepositoryAuth>, RepositoryAuth>
                repoAuth = new PropertyHolder<>(PROP_AUTH, new Enum<>(RepositoryAuth.None), false);
        PropertyHolder<Str, String> repoUser = new PropertyHolder<>(PROP_USER, new Str(null), true);
        PropertyHolder<Str, String> repoPass = new PropertyHolder<>(PROP_PASS, new Str(null), true);

        model.addUserProp(repoAddr, Access.Select);
        model.addUserProp(repoAuth, Access.Any);
        model.addUserProp(repoUser, Access.Any);
        model.addUserProp(repoPass, Access.Any);

        proxyHandler = new NetTools.ProxyHandler(this, Access.Select);

        // Editors
        //noinspection unchecked
        model.getEditor(PROP_ADDR).addCommand(new RepositoryAuthEditor());

        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            if (PROP_AUTH.equals(name)) {
                onChangeRepositoryAuth();
            }
        });
        onChangeRepositoryAuth();
    }

    @Override
    public Collection<PackageDescriptor> filterPackages(List<PackageDescriptor> packages) {
        final Version appVersion = UpgradeService.getBuildVersion();
        return packages.stream()
                .filter(descriptor -> descriptor.compatibleWith(appVersion))
                .collect(Collectors.toMap(
                        PackageDescriptor::getId,
                        descriptor -> descriptor,
                        BinaryOperator.maxBy((o1, o2) -> UpgradeService.VER_COMPARATOR.compare(o1.getVersion(), o2.getVersion()))
                )).values();
    }

    @Override
    public List<PackageDescriptor> readPackages() {
        final URI serverUri = getServerUri();
        if (serverUri != null && checkServer(serverUri)) {
            Logger.getContextLogger(PluginProvider.class).debug("Load plugin registry [{0}]: {1}", getTitle(), serverUri);
            return getArtifacts(serverUri, String.join(".", GROUP_ID));
        }
        return Collections.emptyList();
    }

    private void onChangeRepositoryAuth() {
        boolean usePass = getAuthMode() == RepositoryAuth.Password;
        model.getEditor(PROP_USER).setVisible(usePass);
        model.getEditor(PROP_PASS).setVisible(usePass);
        model.getProperty(PROP_USER).setRequired(usePass);
        model.getProperty(PROP_PASS).setRequired(usePass);
    }

    private URI getServerUri() {
        try {
            return URI.create(((String) model.getValue(PROP_ADDR)).replaceAll("/$", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private RepositoryAuth getAuthMode() {
        return (RepositoryAuth) model.getUnsavedValue(PROP_AUTH);
    }

    private String getServerUser() {
        return (String) model.getValue(PROP_USER);
    }

    private String getServerPassword() {
        return (String) model.getValue(PROP_PASS);
    }

    private boolean checkServer(URI serverURI) {
        try {
            final URIBuilder builder = new URIBuilder(serverURI, StandardCharsets.UTF_8);
            builder.setPathSegments(Stream.concat(
                    builder.getPathSegments().stream(),
                    RQ_STATUS.stream()
            ).collect(Collectors.toList()));
            final HttpURLConnection con = getConnection(builder.build());

            int rsCode = con.getResponseCode();
            if (rsCode != HttpURLConnection.HTTP_OK) {
                Logger.getContextLogger(PluginProvider.class).warn(
                        "Remote plugin registry not available: HTTP: {0}/{1}",
                        rsCode,
                        con.getResponseMessage()
                );
            }
            return rsCode == HttpURLConnection.HTTP_OK;
        } catch (URISyntaxException | IOException e) {
            Logger.getContextLogger(PluginProvider.class).warn("Remote registry server not available: {0}", e.getMessage());
        }
        return false;
    }

    private List<PackageDescriptor> getArtifacts(URI serverURI, String groupId) {
        String token = null;
        final List<PackageDescriptor> result = new LinkedList<>();
        do {
            try {
                final URIBuilder builder = new URIBuilder(serverURI, StandardCharsets.UTF_8);
                builder.setPathSegments(Stream.concat(
                        builder.getPathSegments().stream(),
                        RQ_SEARCH.stream()
                ).collect(Collectors.toList()));

                if (token != null) {
                    builder.addParameter("continuationToken", token);
                }
                builder.addParameter("maven.groupId", groupId);
                final HttpURLConnection con = getConnection(builder.build());

                final int rsCode = con.getResponseCode();
                if (rsCode == HttpURLConnection.HTTP_OK) {
                    final StringBuilder response = new StringBuilder();
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                    }
                    final JSONObject jsonResponse = new JSONObject(response.toString());
                    if (!jsonResponse.isNull("continuationToken")) {
                        token = jsonResponse.getString("continuationToken");
                    }
                    final JSONArray items = jsonResponse.getJSONArray("items");
                    for (int idx = 0; idx < items.length(); idx++) {
                        result.add(new RemotePackageDescriptor(items.getJSONObject(idx)));
                    }
                } else {
                    Logger.getContextLogger(PluginProvider.class).warn(
                            "Remote registry not available: HTTP: {0}/{1}",
                            rsCode,
                            con.getResponseMessage()
                    );
                    break;
                }
            } catch (URISyntaxException | IOException e) {
                Logger.getContextLogger(PluginProvider.class).warn("Retrieve plugin list error", e);
            }
        } while (token != null);
        if (result.isEmpty()) {
            Logger.getContextLogger(PluginProvider.class).warn("Remote plugin registry does not contain packages");
        }
        return result;
    }

    static HttpURLConnection getConnection(URI uri) throws IOException {
        final RemotePluginRegistry registry = RemotePluginRegistry.getInstance();
        final Proxy proxy = registry.proxyHandler.getProxy();
        final HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection(proxy);

        connection.setReadTimeout(CONNECT_TIMEOUT);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json");

        String serverCredentials;
        if ((serverCredentials = getServerCredentials(registry)) != null) {
            connection.setRequestProperty("Authorization", serverCredentials);
        }
        String proxyCredentials;
        if ((proxyCredentials = getProxyCredentials(registry)) != null) {
            connection.setRequestProperty("Proxy-Authorization", proxyCredentials);
        }
        return connection;
    }

    private static String getServerCredentials(RemotePluginRegistry registry) {
        return registry.getAuthMode() == RepositoryAuth.Password ? encodeBasicCredentials(registry.getServerUser(), registry.getServerPassword()) : null;
    }

    private static String getProxyCredentials(RemotePluginRegistry registry) {
        Proxy proxy = registry.proxyHandler.getProxy();
        return proxy != Proxy.NO_PROXY ? encodeBasicCredentials(registry.proxyHandler.getUser(), registry.proxyHandler.getPassword()) : null;
    }

    private static String encodeBasicCredentials(String user, String pass) {
        return MessageFormat.format(
                "Basic {0}",
                Base64.getEncoder().encodeToString(MessageFormat.format("{0}:{1}", user, pass).getBytes())
        );
    }

    private static PluginClassLoader getLoader(URI uri) throws IOException {
        return getLoader(uri, null);
    }

    private static PluginClassLoader getLoader(URI uri, ClassLoader parent) throws IOException {
        return new PluginClassLoader(uri, parent) {
            @Override
            protected URLConnection getConnection(URI uri) throws IOException {
                return RemotePluginRegistry.getConnection(uri);
            }
        };
    }


    private static class RemotePackageDescriptor extends PackageDescriptor {

        private final static String VERSION_PATH = "version.xml";

        private static Map<AssetKind, Asset> loadAssets(JSONArray assetItems) {
            return StreamSupport.stream(assetItems.spliterator(), false)
                    .map(assetItem -> (JSONObject) assetItem)
                    .filter(assetItem -> {
                        for (AssetKind kind : AssetKind.values()) {
                            if (kind.code.equals(assetItem.getJSONObject("maven2").getString("extension"))) {
                                return true;
                            }
                        }
                        return false;
                    }).collect(Collectors.toMap(
                            assetItem -> AssetKind.byCode(assetItem.getJSONObject("maven2").getString("extension")),
                            Asset::new
                    ));
        }

        private final static Function<Asset, Version> COMPATIBLE_GETTER = asset -> {
            if (asset == null) return null;
            try {
                final HttpURLConnection con = getConnection(asset.loadUri);
                final Model model = new MavenXpp3Reader().read(con.getInputStream());
                return PackageDescriptor.createVersion(model.getProperties().getProperty("compatible"));
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            }
            return null;
        };

        private final static Function<Asset, String> CHECKSUM_GETTER = asset -> {
            if (asset == null) return null;
            try {
                final HttpURLConnection con = getConnection(asset.loadUri);
                try (
                        final InputStream    is = con.getInputStream();
                        final BufferedReader br = new BufferedReader(new InputStreamReader(is))
                ) {
                    return br.readLine().toUpperCase();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        };

        private final Map<AssetKind, Asset> assets = new HashMap<>();
        private final Version    compatibleWith;
        private final String     checksum;
        private VersionsDocument history;

        RemotePackageDescriptor(JSONObject artifactItem) {
            this(artifactItem, loadAssets(artifactItem.getJSONArray("assets")));
        }

        private RemotePackageDescriptor(JSONObject artifactItem, Map<AssetKind, Asset> assets) {
            super(
                    assets.get(AssetKind.JAR).loadUri,
                    artifactItem.getString("group"),
                    artifactItem.getString("name"),
                    artifactItem.getString("version"),
                    false
            );
            this.assets.putAll(assets);
            this.compatibleWith = getCompatibleAppVersion();
            this.checksum = CHECKSUM_GETTER.apply(assets.get(AssetKind.MD5));
            this.history  = buildHistory();
        }

        private Version getCompatibleAppVersion() {
            Asset  pom = assets.get(AssetKind.POM);
            return pom == null ? null : COMPATIBLE_GETTER.apply(pom);
        }

        @Override
        protected VersionsDocument getHistory() {
            return history;
        }

        @Override
        public void close()  {}

        @Override
        public String checksum() {
            return checksum;
        }

        @Override
        public Version compatibleWith() {
            return compatibleWith;
        }

        @Override
        public boolean compatibleWith(Version version) {
            return UpgradeService.VER_COMPARATOR.compare(version, compatibleWith) >= 0;
        }

        @Override
        protected ClassLoader getClassLoader(URI uri) throws IOException {
            return getLoader(uri, ClassLoader.getSystemClassLoader());
        }

        @Override
        protected List<String> listPluginClasses() throws IOException {
            return StreamSupport.stream(
                    ClassIndex.getAnnotatedNames(Pluggable.class, getLoader(getUri())).spliterator(),
                    false
            ).collect(Collectors.toList());
        }

        @Override
        protected final List<PluginHandler<? extends IPlugin>> loadPlugins() {
            return loadPluginsByUri(getUri());
        }

        private VersionsDocument buildHistory() {
            try (
                PluginClassLoader loader = getLoader(getUri());
                InputStream stream = loader.getResourceAsStream(VERSION_PATH)
            ) {
                return VersionsDocument.Factory.parse(stream);
            } catch (IOException | XmlException ignore) {}
            return null;
        }
    }


    private static class Asset {

        private final URI loadUri;
        Asset(JSONObject assetItem) {
            loadUri = URI.create(assetItem.getString("downloadUrl"));
        }
    }


    private enum AssetKind {
        POM("pom"), JAR("jar"), MD5("jar.md5");

        private final String code;
        AssetKind(String code) {
            this.code = code;
        }

        static AssetKind byCode(String code) {
            for (AssetKind kind : AssetKind.values()) {
                if (kind.code.equals(code)) {
                    return kind;
                }
            }
            throw new IllegalStateException("There is no suitable kind for '"+code+"'");
        }
    }


    public enum RepositoryAuth implements Iconified {

        None(ImageUtils.getByPath("/images/auth_none.png")),
        Password(ImageUtils.getByPath("/images/auth_pass.png"));

        private final String    title;
        private final ImageIcon icon;

        RepositoryAuth(ImageIcon icon) {
            this.title  = Language.get(RemotePluginRegistry.class, "auth@"+name().toLowerCase());
            this.icon   = icon;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    class RepositoryAuthEditor extends EditorCommand<Str, String> {

        private final List<String> props = Arrays.asList(PROP_AUTH, PROP_USER, PROP_PASS);

        RepositoryAuthEditor() {
            super(ICON_AUTH, Language.get(RemotePluginRegistry.class, "group@auth"));
        }

        @Override
        public void execute(PropertyHolder<Str, String> context) {
            new PropSetEditor(
                    ICON_AUTH,
                    Language.get(RemotePluginRegistry.class, "group@auth"),
                    RemotePluginRegistry.this,
                    props::contains
            ).open();
        }
    }

}
