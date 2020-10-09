package codex.notification;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.supplier.IDataSupplier;
import codex.type.DateTime;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Str;
import org.sqlite.JDBC;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.MessageFormat;
import java.util.*;

class MessageSupplier implements IDataSupplier<Message> {

    private final static Integer LIMIT = 10;

    private Connection connection;
    private Long       prevOffset = Long.MAX_VALUE;
    private String     filter, query;

    private static File getDatabaseFile() throws IOException {
        Class<?> optionHolderClass = ConfigStoreService.class;
        if (ClassLoader.getSystemClassLoader().getResource("META-INF/options/"+ optionHolderClass.getSimpleName()+".properties") != null) {
            final String fileName = ResourceBundle.getBundle("META-INF/options/"+optionHolderClass.getSimpleName()).getString("file");
            return new File(System.getProperty("user.home") + fileName);
        }
        throw new IOException();
    }

    static {
        try {
            synchronized (MessageInbox.getInstance()) {
                ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).buildClassCatalog(
                        Message.class,
                        new HashMap<String, IComplexType>() {{
                            put(Message.PROP_CREATED, new Str());
                            put(Message.PROP_STATUS, new DateTime());
                        }}
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    MessageSupplier() throws IOException, SQLException {
        DriverManager.registerDriver(new JDBC());
        connection = DriverManager.getConnection("jdbc:sqlite:"+ getDatabaseFile().getPath());
    }

    void setFilter(String filter) {
        this.filter = filter;
        this.query  = MessageFormat.format(
                "SELECT [ID], [time] FROM MESSAGE WHERE [time] < ? AND ({0}) ORDER BY [time] DESC LIMIT ?",
                filter
        );
    }

    @Override
    public boolean ready() {
        return connection != null;
    }

    @Override
    public boolean available(ReadDirection direction) {
        return filter != null && direction == ReadDirection.Backward;
    }

    @Override
    public List<Message> getNext() {
        return Collections.emptyList();
    }

    @Override
    public List<Message> getPrev() {
        List<Message> result = new LinkedList<>();
        synchronized (MessageInbox.getInstance()) {
            try (PreparedStatement select = connection.prepareStatement(query)) {
                select.setFetchSize(LIMIT);
                select.setLong(1, prevOffset);
                select.setInt(2, LIMIT);
                try (ResultSet selectRS = select.executeQuery()) {
                    while (selectRS.next()) {
                        int msgID = selectRS.getInt(1);
                        long msgTime = selectRS.getLong(2);
                        if (msgTime < prevOffset) {
                            prevOffset = msgTime;
                        }
                        Message message = EntityRef.build(Message.class, msgID).getValue();
                        result.add(message);
                    }
                }
            } catch (SQLException e) {
                Logger.getLogger().error("Unable to retrieve messages", e);
            }
        }
        return result;
    }

    @Override
    public void reset() {
        prevOffset = Long.MAX_VALUE;
    }

    Integer getUnreadMessages() {
        final String selectSQL = "SELECT COUNT([ID]) FROM MESSAGE WHERE [status] IS NULL";
        try (ResultSet resultSet = connection.createStatement().executeQuery(selectSQL)) {
            return resultSet.getInt(1);
        } catch (SQLException ignore) {}
        return 0;
    }
}
