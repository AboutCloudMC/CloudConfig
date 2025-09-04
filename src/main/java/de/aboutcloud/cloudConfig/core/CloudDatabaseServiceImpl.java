package de.aboutcloud.cloudConfig.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.aboutcloud.cloudConfig.api.config.CloudConfigService;
import de.aboutcloud.cloudConfig.api.databasse.CloudDatabaseService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CloudDatabaseServiceImpl implements CloudDatabaseService {
    private final JavaPlugin cloudConfigPlugin;
    private final CloudConfigService cfgService;

    private static final String CFG_FILE = "database.yml";
    private static final String MIGRATIONS_DIR = "config/db/migrations";
    private static final Pattern MIGRATION_NAME = Pattern.compile("^V(\\d+)__([\\w\\-]+)\\.sql$");

    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final Map<String, Map<String,String>> sqlVars = new ConcurrentHashMap<>();

    public CloudDatabaseServiceImpl(JavaPlugin cloudConfig, CloudConfigService cfgService) {
        this.cloudConfigPlugin = cloudConfig;
        this.cfgService = cfgService;
    }

    @Override
    public void ensurePool(JavaPlugin plugin) throws SQLException {
        String key = plugin.getName();
        close(plugin); // recreate if existed

        FileConfiguration cfg = cfgService.getConfig(plugin, CFG_FILE);
        if (cfg == null) throw new IllegalStateException("Missing " + CFG_FILE + " for " + key);

        boolean enabled = cfg.getBoolean("enabled", true);
        if (!enabled) return;

        String host = cfg.getString("host", "127.0.0.1");
        int port = cfg.getInt("port", 3306);
        String database = cfg.getString("database", toSnake(key));
        String user = cfg.getString("user", "root");
        String password = String.valueOf(cfg.get("password"));
        boolean ssl = cfg.getBoolean("useSsl", false);

        // Optionally create database if missing
        if (cfg.getBoolean("schema.createIfMissing", true)) {
            createDatabaseIfMissing(host, port, user, password, ssl, database);
        }

        String jdbc = "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf8"
                + (ssl ? "&useSsl=true" : "&useSsl=false");

        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbc);
        hc.setUsername(user);
        hc.setPassword(password);
        hc.setDriverClassName("org.mariadb.jdbc.Driver");
        hc.setPoolName("CloudDB-" + key);
        hc.setMaximumPoolSize(cfg.getInt("pool.maxPoolSize", 10));
        hc.setMinimumIdle(cfg.getInt("pool.minIdle", 2));
        hc.setConnectionTimeout(cfg.getLong("pool.connectionTimeoutMs", 10_000));
        hc.setIdleTimeout(cfg.getLong("pool.idleTimeoutMs", 600_000));
        hc.setMaxLifetime(cfg.getLong("pool.maxLifetimeMs", 1_800_000));
        if (cfg.getBoolean("pool.allowMysqlCompat", false)) {
            hc.addDataSourceProperty("useMysqlMetadata", "true");
        }

        HikariDataSource ds = new HikariDataSource(hc);
        pools.put(key, ds);

        // Default SQL variables
        Map<String,String> vars = new HashMap<>();
        vars.put("plugin", toSnake(key));       // ${plugin}
        vars.put("database", database);         // ${database}
        setSqlVariables(plugin, vars);

        // Migrations on startup?
        if (cfg.getBoolean("migrations.runOnStartup", true)) {
            migrate(plugin);
        }
    }

    @Override public void close(JavaPlugin plugin) {
        HikariDataSource ds = pools.remove(plugin.getName());
        if (ds != null) ds.close();
    }

    @Override public void reload(JavaPlugin plugin) throws SQLException {
        ensurePool(plugin);
    }

    @Override public DataSource dataSource(JavaPlugin plugin) {
        HikariDataSource ds = pools.get(plugin.getName());
        if (ds == null) throw new IllegalStateException("No DataSource for " + plugin.getName() + " (call ensurePool)");
        return ds;
    }

    @Override public Connection connection(JavaPlugin plugin) throws SQLException {
        return dataSource(plugin).getConnection();
    }

    @Override public <T> T withConnection(JavaPlugin plugin, SQLFunction<Connection,T> fn) throws SQLException {
        try (Connection c = connection(plugin)) { return fn.apply(c); }
    }

    @Override public void withTransaction(JavaPlugin plugin, SQLConsumer<Connection> tx) throws SQLException {
        try (Connection c = connection(plugin)) {
            boolean old = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                tx.accept(c);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(old);
            }
        }
    }

    @Override public void migrate(JavaPlugin plugin) throws SQLException {
        String key = plugin.getName();
        Path dir = plugin.getDataFolder().toPath().resolve(MIGRATIONS_DIR);
        try { Files.createDirectories(dir); } catch (IOException ignored) {}

        // Ensure version table exists
        withTransaction(plugin, c -> {
            try (Statement st = c.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS cloudconfig_schema_version (
                      version INT PRIMARY KEY,
                      description VARCHAR(255),
                      installed_at TIMESTAMP NOT NULL
                    ) ENGINE=InnoDB
                """);
            }
        });

        // Load applied versions
        Set<Integer> applied = withConnection(plugin, c -> {
            Set<Integer> set = new HashSet<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT version FROM cloudconfig_schema_version")) {
                while (rs.next()) set.add(rs.getInt(1));
            }
            return set;
        });

        // Find migration files
        List<Migration> migrations = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                Matcher m = MIGRATION_NAME.matcher(p.getFileName().toString());
                if (!m.matches()) continue;
                int ver = Integer.parseInt(m.group(1));
                String desc = m.group(2).replace('_', ' ');
                migrations.add(new Migration(ver, desc, p));
            }
        } catch (IOException e) {
            throw new SQLException("Failed to list migrations in " + dir, e);
        }
        migrations.sort(Comparator.comparingInt(m -> m.version));

        // Execute pending
        for (Migration m : migrations) {
            if (applied.contains(m.version)) continue;
            applyMigration(plugin, m);
        }
    }

    @Override public void setSqlVariables(JavaPlugin plugin, Map<String, String> vars) {
        sqlVars.computeIfAbsent(plugin.getName(), k -> new HashMap<>()).putAll(vars);
    }

    // ----- helpers -----

    private void applyMigration(JavaPlugin plugin, Migration mig) throws SQLException {
        String key = plugin.getName();
        String sql;
        try {
            sql = Files.readString(mig.path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Failed reading " + mig.path, e);
        }

        // Replace ${var} placeholders
        Map<String,String> vars = sqlVars.getOrDefault(key, Map.of());
        for (var e : vars.entrySet()) {
            sql = sql.replace("${" + e.getKey() + "}", e.getValue());
        }

        String finalSql = sql;
        withTransaction(plugin, c -> {
            // naive splitter: handles ; outside quotes
            for (String stmt : splitSqlStatements(finalSql)) {
                if (stmt.isBlank()) continue;
                try (Statement s = c.createStatement()) {
                    s.execute(stmt);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO cloudconfig_schema_version(version, description, installed_at) VALUES (?,?,?)")) {
                ps.setInt(1, mig.version);
                ps.setString(2, mig.description);
                ps.setTimestamp(3, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        });

        cloudConfigPlugin.getSLF4JLogger().info("[CloudConfig] Applied {} V{} ({})", key, mig.version, mig.description);
    }

    private static List<String> splitSqlStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean sQuote = false, dQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && !dQuote) sQuote = !sQuote;
            else if (ch == '"' && !sQuote) dQuote = !dQuote;

            if (ch == ';' && !sQuote && !dQuote) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        if (!cur.isEmpty()) out.add(cur.toString().trim());
        return out;
    }

    private static void createDatabaseIfMissing(String host, int port, String user, String pass, boolean ssl, String db) throws SQLException {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        String adminUrl = "jdbc:mariadb://" + host + ":" + port + "/?useUnicode=true&characterEncoding=utf8" + (ssl ? "&useSSL=true" : "&useSSL=false");
        try (Connection c = DriverManager.getConnection(adminUrl, user, pass);
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + db + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static String toSnake(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase(Locale.ROOT);
    }

    private record Migration(int version, String description, Path path) {}
}
