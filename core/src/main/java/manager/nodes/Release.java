package manager.nodes;

import codex.model.Entity;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;
import manager.svn.SVN;
import manager.xml.ReleaseDocument;
import org.apache.xmlbeans.XmlException;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Release extends BinarySource {

    private final static Pattern LAYER = Pattern.compile("\\sBaseLayerURIs=\"([\\w\\.]*)\"\\s");
    private final static String LOCAL_DIR = "releases";
    
    public  final static String PROP_VERSION = "version";

    public Release(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/release.png"), title);
        
        // Properties
        model.addDynamicProp(PROP_VERSION, new Str(null), null, this::getPID);
    }
    
    public final String getVersion() {
        return (String) model.getValue(PROP_VERSION);
    }

    @Override
    public Class<? extends Entity>  getChildClass() {
        return null;
    }

    @Override
    protected Class<? extends RepositoryBranch> getParentClass() {
        return ReleaseList.class;
    }

    @Override
    protected String getLocalDir() {
        return LOCAL_DIR;
    }
    
    public Map<String, Path> getRequiredLayers(String topLayer, boolean online) {
        if (online) {
            try {
                ISVNAuthenticationManager authMgr = getRepository().getAuthManager();
                InputStream in = SVN.readFile(getRemotePath(), "release.xml", authMgr);
                ReleaseDocument releaseDoc = ReleaseDocument.Factory.parse(in);
                manager.xml.Release.Branch.Layer[] layers = releaseDoc.getRelease().getBranch().getLayerArray();
                return createLayerChain(layers, topLayer);
            } catch (SVNException | XmlException | IOException e) {
                return new LinkedHashMap<String, Path>(){{
                    put(topLayer, null);
                }};
            }
        } else {
            return createLayerChain(topLayer);
        }
    }

    private Map<String, Path> createLayerChain(String nextLayer) {
        Map<String, Path> chain = new LinkedHashMap<>();
        Path layerDef = Paths.get(getLocalPath()+File.separator+nextLayer+File.separator+"layer.xml");
        if (Files.exists(layerDef)) {
            try {
                chain.put(nextLayer, layerDef);
                String content = new String(Files.readAllBytes(layerDef), Charset.forName("UTF-8"));
                Matcher layerMatcher = LAYER.matcher(content);
                if (layerMatcher.find()) {
                    chain.putAll(createLayerChain(layerMatcher.group(1)));
                }
            } catch (IOException e) {
                // Do nothing
            }
        } else {
            chain.put(nextLayer, null);
        }
        return chain;
    }
    
    private Map<String, Path> createLayerChain(manager.xml.Release.Branch.Layer[] layers, String nextLayer) {
        Map<String, Path> chain = new LinkedHashMap<>();
        for (manager.xml.Release.Branch.Layer layer : layers) {
            if (layer.getUri().equals(nextLayer)) {
                Path layerDef = Paths.get(getLocalPath()+File.separator+nextLayer+File.separator+"layer.xml");
                chain.put(layer.getUri(), layerDef);
                if (!layer.getBaseLayerURIs().isEmpty()) {
                    chain.putAll(createLayerChain(layers, (String) layer.getBaseLayerURIs().get(0)));
                }
            }
        }
        return chain;
    }
    
    public Map<String, String> getLayerPaths(List<String> layers) throws SVNException {
        Map<String, String> map = new LinkedHashMap<>();
        ISVNAuthenticationManager authMgr = getRepository().getAuthManager();
        List<SVNDirEntry> entries = SVN.list(getRemotePath(), authMgr);
        entries.stream()
            .filter((dirEntry) -> (dirEntry.getKind() == SVNNodeKind.DIR && layers.contains(dirEntry.getName())))
            .forEachOrdered((dirEntry) -> map.put(
                dirEntry.getName(),
                getRemotePath()+"/"+dirEntry.getName()
            ));
        return map;
    }
    
}
