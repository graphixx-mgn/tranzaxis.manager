package codex.config;

import codex.model.Entity;
import codex.xml.ConfigurationDocument;

import java.util.List;
import java.util.Map;

public interface IConfigExporter<T extends Entity> {

    Map<Class<T>, List<T>> getObjects();
    ConfigurationDocument getXml();

}
