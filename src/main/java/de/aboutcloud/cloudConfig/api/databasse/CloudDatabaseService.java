package de.aboutcloud.cloudConfig.api.databasse;

import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public interface CloudDatabaseService {
    /** Ensure a pool exists for this plugin (creates/updates from database.yml). */
    void ensurePool(JavaPlugin plugin) throws SQLException;

    /** Close and remove this plugin’s pool. */
    void close(JavaPlugin plugin);

    /** Reload config and recreate the pool. */
    void reload(JavaPlugin plugin) throws SQLException;

    /** Access the pooled DataSource (ensurePool must have run). */
    DataSource dataSource(JavaPlugin plugin);

    /** Get a connection (remember to close!). */
    Connection connection(JavaPlugin plugin) throws SQLException;

    /** Run a unit of work with a connection. */
    <T> T withConnection(JavaPlugin plugin, SQLFunction<Connection, T> fn) throws SQLException;

    /** Run a transaction (commit on success, rollback on error). */
    void withTransaction(JavaPlugin plugin, SQLConsumer<Connection> tx) throws SQLException;

    /** Run versioned SQL migrations from disk; creates schema version table. */
    void migrate(JavaPlugin plugin) throws SQLException;

    /** Replace vars like ${plugin} in SQL before execution. */
    void setSqlVariables(JavaPlugin plugin, Map<String, String> vars);

    @FunctionalInterface interface SQLFunction<C, R> { R apply(C c) throws SQLException; }
    @FunctionalInterface interface SQLConsumer<C> { void accept(C c) throws SQLException; }
}
