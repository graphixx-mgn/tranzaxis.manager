package manager.nodes;

import codex.model.Catalog;
import codex.model.Entity;
import codex.type.EntityRef;
import javax.swing.*;
import java.util.Comparator;

public abstract class BinarySource extends Catalog {
    
    static final Comparator<String> VERSION_SORTER = new Comparator<String>() {
        @Override
        public int compare(String prev, String next) {
            if ("trunk".equals(prev)) {
                return 1;
            } else if ("trunk".equals(next)) {
                return -1;
            } else {
                String[] components1 = prev.split("\\.");
                String[] components2 = next.split("\\.");
                int length = Math.min(components1.length, components2.length);
                for(int i = 0; i < length; i++) {
                    int result = new Integer(components1[i]).compareTo(Integer.parseInt(components2[i]));
                    if(result != 0) {
                        return result;
                    }
                }
                return Integer.compare(components1.length, components2.length);
            }
        }
    };

    BinarySource(EntityRef parent, ImageIcon icon, String title) {
        super(parent, icon, title, null);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
    
    public Repository getRepository() {
        return (Repository) this.getOwner();
    }
    
    public abstract String getLocalPath();
    
    public abstract String getRemotePath();
    
}
