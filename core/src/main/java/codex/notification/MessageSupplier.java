package codex.notification;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.supplier.IDataSupplier;
import codex.type.EntityRef;
import org.sqlite.JDBC;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.MessageFormat;
import java.util.*;


class MessageSupplier implements IDataSupplier<Message> {

    private final static String  PAGINATION = "SELECT T.* FROM ({0}) T LIMIT {1} OFFSET {2}";
    private final static Integer LIMIT = 10;

    private Connection connection;
    private Integer    baseID, maxID;
    private Long       prevOffset = 0L;

    private static File getDatabaseFile() throws IOException {
        Class<?> optionHolderClass = ConfigStoreService.class;
        if (ClassLoader.getSystemClassLoader().getResource("META-INF/options/"+ optionHolderClass.getSimpleName()+".properties") != null) {
            final String fileName = ResourceBundle.getBundle("META-INF/options/"+optionHolderClass.getSimpleName()).getString("file");
            return new File(System.getProperty("user.home") + fileName);
        }
        throw new IOException();
    }

    MessageSupplier() {
        try {
            DriverManager.registerDriver(new JDBC());
            connection = DriverManager.getConnection("jdbc:sqlite:"+ getDatabaseFile().getPath());
            ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).buildClassCatalog(Message.class, Collections.emptyMap());
            baseID = getMaxID();
            maxID  = baseID;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean ready() {
        return connection != null;
    }

    @Override
    public boolean available(ReadDirection direction) {
        return true;
    }

    @Override
    public List<Message> getNext() {
        List<Message> result = new LinkedList<>();
        final String selectSQL = "SELECT [ID] FROM MESSAGE WHERE ID > ? ORDER BY [SEQ] ASC";
        try (PreparedStatement select = connection.prepareStatement(selectSQL)) {
            select.setFetchSize(10);
            select.setInt(1, maxID);
            try (ResultSet selectRS = select.executeQuery()) {
                while (selectRS.next()) {
                    Message message = EntityRef.build(Message.class, selectRS.getInt(1)).getValue();
                    message.model.read();
                    result.add(message);
                    if (message.getID() > maxID) {
                        maxID = message.getID();
                    }
                }
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to retrieve messages", e);
        }
        return result;
    }

    @Override
    public List<Message> getPrev() {
        List<Message> result = new LinkedList<>();
        final String selectSQL = "SELECT [ID] FROM MESSAGE WHERE ID <= ? ORDER BY [SEQ] DESC";
        try (PreparedStatement select = connection.prepareStatement(MessageFormat.format(
                PAGINATION, selectSQL, LIMIT, prevOffset
        ))) {
            select.setFetchSize(10);
            select.setInt(1, baseID);
            try (ResultSet selectRS = select.executeQuery()) {
                while (selectRS.next()) {
                    Message message = EntityRef.build(Message.class, selectRS.getInt(1)).getValue();
                    message.model.read();
                    result.add(message);
                }
            }
        } catch (SQLException e) {
            Logger.getLogger().error("Unable to retrieve messages", e);
        }
        prevOffset += result.size();
        return result;
    }

    @Override
    public void reset() {
        prevOffset = 0L;
    }

    private Integer getMaxID() {
        final String selectSQL = "SELECT MAX([ID]) FROM MESSAGE";
        try (ResultSet resultSet = connection.createStatement().executeQuery(selectSQL)) {
            return resultSet.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
