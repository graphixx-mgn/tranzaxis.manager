package manager.type;

import codex.editor.AnyTypeView;
import codex.editor.IEditorFactory;
import codex.type.ArrStr;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.offshoot.BuildWC;
import javax.swing.*;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public final class BuildStatus extends ArrStr implements Iconified {
    
    private StatusHolder value = null;
    
    public BuildStatus() {
    }
    
    public BuildStatus(Long revision, Boolean failed) {
        setValue(new StatusHolder(revision, failed));
    }
    
    @Override
    public void setValue(List<String> value) {
        if (value != null) {
            this.value = new StatusHolder(value);
        } else {
            this.value = null;
        }
    }

    @Override
    public ImageIcon getIcon() {
        return value == null ? null : value.getIcon();
    }

    public Long getRevision() {
        return value == null ? null : Long.valueOf(value.get(0));
    }

    public String getText() {
        return value == null ? null : value.toString();
    }
    
    @Override
    public List<String> getValue() {
        return value;
    }

    @Override
    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }

    @Override
    public String toString() {
        return merge(value);
    }
    
    @Override
    public String getQualifiedValue(List<String> val) {
        return val == null ? "<NULL>" :  MessageFormat.format("({0})'", val);
    }
    
    @Override
    public IEditorFactory editorFactory() {
        return AnyTypeView::new;
    }

    class StatusHolder extends LinkedList<String> implements Iconified {

        StatusHolder(Collection<? extends String> c) {
            super(c);
        }

        StatusHolder(Long revision, Boolean failed) {
            super(Arrays.asList(revision.toString(), failed ? "1" : "0"));
        }
        
        @Override
        public String toString() {
            return get(0).concat(" / ").concat((get(1).equals("1") ? StatusIcon.Fail : StatusIcon.Success).toString());
        }
        
        @Override
        public ImageIcon getIcon() {
            return (get(1).equals("1") ? StatusIcon.Fail : StatusIcon.Success).getIcon();
        }
    }
    
    enum StatusIcon implements Iconified {

        Fail(
            Language.get(BuildWC.class, "command@fail"),
            ImageUtils.getByPath("/images/svn_erroneous.png")
        ),
        Success(
            Language.get(BuildWC.class, "command@success"),
            ImageUtils.getByPath("/images/svn_successful.png"
        ));
        
        private final String    title;
        private final ImageIcon icon;

        StatusIcon(String title, ImageIcon icon) {
            this.title = title;
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
    
}
