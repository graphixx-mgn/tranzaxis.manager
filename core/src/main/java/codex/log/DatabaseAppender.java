package codex.log;

import codex.service.Service;
import codex.type.DateTime;
import codex.type.IComplexType;
import codex.utils.Language;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.jdbc.JDBCAppender;
import org.sqlite.JDBC;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

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
            setName(getClass().getTypeName());

            init();
        } catch (IOException | SQLException e){
            e.printStackTrace();
        }
    }

    private void init() throws SQLException {
        String createSQL = Language.get("create");
        try (final Statement createStmt = getConnection().createStatement()) {
            createStmt.execute(createSQL);
        }
        String  daysStr = IComplexType.coalesce(
                Service.getProperty(ILogManagementService.class, LoggerServiceOptions.PROP_DB_DAYS),
                String.valueOf(LoggerServiceOptions.STORE_DAYS)
        );
        Integer days;

        if (daysStr != null && !daysStr.isEmpty() && (days = Integer.valueOf(daysStr)) >= 0) {
            Date dropToDate = DateTime.addDays(DateTime.trunc(new Date()), -1*days);
            try (final PreparedStatement deleteStmt = getConnection().prepareStatement(Language.get("delete"))) {
                deleteStmt.setString(1, new Timestamp(dropToDate.getTime()).toString());
                int deletedRows = deleteStmt.executeUpdate();
                if (deletedRows > 0) {
                    connection.createStatement().executeUpdate("VACUUM");
                    SwingUtilities.invokeLater(() -> Logger.getLogger().debug(
                            "Deleted rows (older than {0}): {1}",
                            new SimpleDateFormat("yyyy-MM-dd").format(dropToDate),
                            deletedRows
                    ));
                }
            }
        }
    }
}
