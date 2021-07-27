package codex.utils;

import codex.command.EditorCommand;
import codex.mask.IMask;
import codex.model.Access;
import codex.model.Entity;
import codex.model.PropSetEditor;
import codex.property.PropertyHolder;
import codex.type.*;
import codex.type.Enum;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Класс вспомогательных методов для работы с сетью.
 */
@ThreadSafe
public class NetTools {

    private final static Map<String, Object> syncMap = new HashMap<>();
    
    /**
     * Проверка доступности сетевого порта.
     * @param host Символьное имя хоста или IP-адрес.
     * @param port Номер порта.
     * @param timeout Таймаут подключения в миллисекундах.
     */
    public static boolean isPortAvailable(String host, int port, int timeout) throws IllegalStateException {
        if (!checkPort(port)) {
            throw new IllegalStateException("Invalid port number");
        }
        if (!checkAddress(host)) {
            throw new IllegalStateException("Invalid host address: "+host);
        }

        Object syncVal;
        String syncKey = host.concat(":").concat(String.valueOf(port));
        synchronized (syncMap) {
            syncVal = syncMap.computeIfAbsent(syncKey, s -> new Object());
        }
        synchronized (syncVal) {
            SocketAddress remoteAddr = new InetSocketAddress(host, port);
            try (Socket socket = new Socket()) {
                int attempt = 0;
                while (!socket.isConnected() && attempt < 3) {
                    try {
                        socket.connect(remoteAddr, timeout);
                    } catch (IOException ignore) {}
                    attempt++;
                }
                return socket.isConnected();
            } catch (IOException e) {
                return false;
            } finally {
                syncMap.remove(syncKey);
            }
        }
    }
    
    private static boolean checkPort(int port) {
        return !(port < 1 || port > 65535);
    }
    
    private static boolean checkAddress(String host) {
        return 
                host.matches("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))$") &&
                host.matches("^[^\\s]+$");
    }

    public static boolean checkUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }


    public static final class PortMask implements IMask<Integer> {

        @Override
        public boolean verify(Integer value) {
            return checkPort(value);
        }
    }

    public static final class ProxyHandler {

        private final static ImageIcon ICON_AUTH = ImageUtils.getByPath("/images/auth.png");

        private final static String PROP_PROXY_MODE  = "proxyMode";
        private final static String PROP_PROXY_VIEW  = "proxyView";
        private final static String PROP_PROXY_HOST  = "proxyHost";
        private final static String PROP_PROXY_PORT  = "proxyPort";
        private final static String PROP_PROXY_AUTH  = "proxyAuth";
        private final static String PROP_PROXY_USER  = "proxyUser";
        private final static String PROP_PROXY_PASS  = "proxyPass";

        private static <T extends IComplexType<V, ? extends IMask<V>>, V> PropertyHolder<T,V> createProperty(String propName, T value) {
            final String title = Language.get(NetTools.class, propName.concat(PropertyHolder.PROP_NAME_SUFFIX));
            return new PropertyHolder<>(propName, title, null, value, false);
        }

        // Properties
        private final PropertyHolder<Enum<ProxyMode>, ProxyMode> proxyMode = createProperty(PROP_PROXY_MODE, new Enum<>(ProxyMode.None));
        private final PropertyHolder<Str, String>                proxyHost = createProperty(PROP_PROXY_HOST, new Str());
        private final PropertyHolder<Int, Integer>               proxyPort = createProperty(PROP_PROXY_PORT, new Int(){{ setMask(new PortMask()); }});

        private final PropertyHolder<Enum<ProxyAuth>, ProxyAuth> proxyAuth = createProperty(PROP_PROXY_AUTH, new Enum<>(ProxyAuth.None));
        private final PropertyHolder<Str, String>                proxyUser = createProperty(PROP_PROXY_USER, new Str());
        private final PropertyHolder<Str, String>                proxyPass = createProperty(PROP_PROXY_PASS, new Str());

        // Getters
        private final Supplier<Entity>  entity;
        private final Function<Boolean, ProxyMode> mode;
        private final Function<Boolean, String>    host;
        private final Function<Boolean, Integer>   port;
        private final Function<Boolean, ProxyAuth> auth;
        private final Function<Boolean, String>    user;
        private final Function<Boolean, String>    pass;

        public ProxyHandler(Entity entity, Access access) {
            this(entity, access, Language.get(NetTools.class, "group@proxy"));
        }

        public ProxyHandler(Entity entity, Access access, String groupTitle) {
            this.entity = () -> entity;

            entity.model.addUserProp(proxyMode, access);
            entity.model.addUserProp(proxyHost, access);
            entity.model.addUserProp(proxyPort, access);
            entity.model.addDynamicProp(
                    PROP_PROXY_VIEW, Language.get(NetTools.class, PROP_PROXY_VIEW.concat(PropertyHolder.PROP_NAME_SUFFIX)), null,
                    new AnyType(), access,
                    () -> new Iconified() {
                        @Override
                        public ImageIcon getIcon() {
                            return Runtime.OS.proxy.get() == Proxy.NO_PROXY || Runtime.OS.proxy.get().type() == Proxy.Type.DIRECT ? ProxyMode.None.icon : ProxyMode.Automatic.icon;
                        }
                        @Override
                        public String toString() {
                            return Runtime.OS.proxy.get().toString();
                        }
                    }
            );
            entity.model.addUserProp(proxyAuth, Access.Any);
            entity.model.addUserProp(proxyUser, Access.Any);
            entity.model.addUserProp(proxyPass, Access.Any);

            //noinspection unchecked
            entity.model.getEditor(PROP_PROXY_HOST).addCommand(new ProxyAuthSettings());
            if (groupTitle != null) {
                entity.model.addPropertyGroup(groupTitle, PROP_PROXY_MODE, PROP_PROXY_HOST, PROP_PROXY_PORT, PROP_PROXY_VIEW);
            }

            // Getters
            final PropertyAccessor accessor = (name, unsaved) -> unsaved ? entity.model.getUnsavedValue(name) : entity.model.getValue(name);
            mode = unsaved -> (ProxyMode) accessor.getValue(PROP_PROXY_MODE, unsaved);
            host = unsaved -> (String)    accessor.getValue(PROP_PROXY_HOST, unsaved);
            port = unsaved -> (Integer)   accessor.getValue(PROP_PROXY_PORT, unsaved);
            auth = unsaved -> (ProxyAuth) accessor.getValue(PROP_PROXY_AUTH, unsaved);
            user = unsaved -> (String)    accessor.getValue(PROP_PROXY_USER, unsaved);
            pass = unsaved -> (String)    accessor.getValue(PROP_PROXY_PASS, unsaved);

            // Handlers
            final Consumer<ProxyMode> onChangeMode = value -> {
                boolean editable  = value == ProxyMode.Manual;
                boolean automatic = value == ProxyMode.Automatic;
                entity.model.getEditor(PROP_PROXY_VIEW).setVisible(automatic);
                entity.model.getEditor(PROP_PROXY_HOST).setVisible(editable);
                entity.model.getEditor(PROP_PROXY_PORT).setVisible(editable);
                proxyHost.setRequired(editable);
                proxyPort.setRequired(editable);
            };
            final Consumer<ProxyAuth> onChangeAuth = value -> {
                boolean authenticate = value == ProxyAuth.Password;
                entity.model.getEditor(PROP_PROXY_USER).setVisible(authenticate);
                entity.model.getEditor(PROP_PROXY_PASS).setVisible(authenticate);
                proxyUser.setRequired(authenticate);
                proxyPass.setRequired(authenticate);
            };

            entity.model.addChangeListener((name, oldValue, newValue) -> {
                switch (name) {
                    case PROP_PROXY_MODE:
                        onChangeMode.accept((ProxyMode) newValue);
                        break;
                    case PROP_PROXY_AUTH:
                        onChangeAuth.accept((ProxyAuth) newValue);
                        break;
                }
            });

            // Init view
            onChangeMode.accept(mode.apply(true));
            onChangeAuth.accept(auth.apply(true));
        }

        public final Proxy getProxy() {
            ProxyMode mode = this.mode.apply(false);
            if (mode == ProxyMode.None) {
                return Proxy.NO_PROXY;
            } else if (mode == ProxyMode.Automatic) {
                return Runtime.OS.proxy.get() == null || Runtime.OS.proxy.get().address() == null ?
                        Proxy.NO_PROXY :
                        new Proxy(Proxy.Type.HTTP, Runtime.OS.proxy.get().address());
            } else {
                return new Proxy(
                        Proxy.Type.HTTP,
                        new InetSocketAddress(host.apply(false), port.apply(false))
                );
            }
        }

        public final String getUser() {
            return user.apply(false);
        }

        public final String getPassword() {
            return pass.apply(false);
        }


        @FunctionalInterface
        interface PropertyAccessor {
            Object getValue(String name, Boolean unsaved);
        }


        public enum ProxyMode implements Iconified {

            None(ImageUtils.getByPath("/images/auth_none.png")),
            Automatic(ImageUtils.getByPath("/images/instance.png")),
            Manual(ImageUtils.getByPath("/images/edit.png"));

            protected final ImageIcon icon;
            protected final String    title;

            ProxyMode(ImageIcon icon) {
                this.title = Language.get(NetTools.class, "proxy@"+name().toLowerCase());
                this.icon  = icon;
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


        public enum ProxyAuth implements Iconified {

            None(ImageUtils.getByPath("/images/auth_none.png")),
            Password(ImageUtils.getByPath("/images/auth_pass.png"));

            private final String    title;
            private final ImageIcon icon;

            ProxyAuth(ImageIcon icon) {
                this.title  = Language.get(NetTools.class, "auth@"+name().toLowerCase());
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


        class ProxyAuthSettings extends EditorCommand<Str, String> {

            private final List<String> props = Arrays.asList(PROP_PROXY_AUTH, PROP_PROXY_USER, PROP_PROXY_PASS);

            ProxyAuthSettings() {
                super(ICON_AUTH, Language.get(NetTools.class, "group@auth"));
            }

            @Override
            public void execute(PropertyHolder<Str, String> context) {
                new PropSetEditor(
                        ICON_AUTH,
                        Language.get(NetTools.class, "group@auth"),
                        entity.get(),
                        props::contains
                ).open();
            }
        }
    }
}
