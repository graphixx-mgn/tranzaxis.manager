package manager.type;

import codex.editor.AbstractEditor;
import codex.type.Enum;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.BinarySource;
import manager.nodes.Environment;
import javax.swing.*;
import java.util.function.Function;

public enum SourceType implements Iconified {

    @Enum.Undefined
    None(AbstractEditor.NOT_DEFINED, ImageUtils.getByPath("/images/explorer.png"), environment -> null),
    Offshoot(Language.get("offshoot"), ImageUtils.getByPath("/images/branch.png"), environment -> environment.getOffshoot(true)),
    Release(Language.get("release"), ImageUtils.getByPath("/images/release.png"),  environment -> environment.getRelease(true));

    private final String    title;
    private final ImageIcon icon;
    Function<Environment, BinarySource> provider;

    SourceType(String title, ImageIcon icon, Function<Environment, BinarySource> binProvider) {
        this.title    = title;
        this.icon     = icon;
        this.provider = binProvider;
    }

    @Override
    public ImageIcon getIcon() {
        return icon;
    }

    @Override
    public String toString() {
        return title;
    }

    public BinarySource getBinarySource(Environment env) {
        return provider.apply(env);
    }
}
