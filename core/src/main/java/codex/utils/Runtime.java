package codex.utils;

import net.jcip.annotations.ThreadSafe;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.function.Supplier;

@ThreadSafe
public class Runtime {

    public static class JVM {
        public final static Supplier<String> name = () -> System.getProperty("java.runtime.name");
        public final static Supplier<String> version = () -> System.getProperty("java.vm.specification.version");
        public final static Supplier<String> location = () -> System.getProperty("java.home");
        public final static Supplier<File>   compiler = () -> {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler != null) {
                URL url = compiler.getClass().getProtectionDomain().getCodeSource().getLocation();
                try {
                    String urlDecoded = URLDecoder.decode(url.getPath(), "UTF-8");
                    return new File(urlDecoded);
                } catch (UnsupportedEncodingException ignore) {}
            }
            return null;
        };
    }


    public static class APP {
        public final static Supplier<Class> mainClass = () -> Caller.getInstance().getClassStack().stream()
                .reduce((first, second) -> second)
                .orElse(null);
        public final static Supplier<File> jarFile = () -> new File(
                Runtime.class.getProtectionDomain().getCodeSource().getLocation().getFile()
        );
        public final static Supplier<Boolean> devMode = () -> !jarFile.get().isFile();
    }
}
