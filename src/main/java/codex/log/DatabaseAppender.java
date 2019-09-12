package codex.log;

import codex.utils.Language;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.jdbc.JDBCAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.sqlite.JDBC;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.stream.Collectors;

public class DatabaseAppender extends JDBCAppender {

    private static final String PROP_FILE = "META-INF/options/LogManagementService.properties";

    public DatabaseAppender() {
        try (
            InputStream configPropStream  = Logger.class.getClassLoader().getResourceAsStream(PROP_FILE)
        ) {
            EnhancedPatternLayout layout = new EnhancedPatternLayout();
            layout.setConversionPattern(Language.get("insert"));

            Properties properties = new Properties();
            properties.load(configPropStream);
            Path dbFilePath = Paths.get(
                    System.getProperty("user.home"),
                    properties.getProperty("file")
            );
            setDriver(JDBC.class.getTypeName());
            setLayout(layout);
            setURL("jdbc:sqlite:"+ dbFilePath);

            init();
        } catch (IOException | SQLException e){
            e.printStackTrace();
        }
    }

    private void init() throws SQLException {
        String createSQL = Language.get("create");
        try (final Statement statement = getConnection().createStatement()) {
            statement.execute(createSQL);
        }
    }

    @Override
    public synchronized void doAppend(LoggingEvent event) {
            String contexts = Logger.getMessageContexts().stream()
                    .map(Logger::getContextId)
                    .collect(Collectors.joining(","));
            super.doAppend(new LoggingEvent(
                    event.getFQNOfLoggerClass(),
                    event.getLogger(),
                    event.getTimeStamp(),
                    event.getLevel(),
                    event.getMessage().toString(),
                    event.getThreadName(),
                    event.getThrowableInformation(),
                    contexts,
                    event.getLocationInformation(),
                    null
            ));
    }
}
