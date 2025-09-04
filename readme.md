<p align="center">
  <img src="logo.png" alt="CloudConfig Logo" width="200"/>
</p>

<h1 align="center">CloudConfig</h1>

CloudConfig is a Paper/Spigot plugin library that provides a **centralized configuration** and **database management service** for Minecraft plugins.
It allows developers to easily manage plugin configs, localized message files, and SQL database connections with minimal boilerplate.

---

## ✨ Features

### Config Service (`CloudConfigService`)

* Register and load multiple configuration files per plugin.
* Automatic copying of default configs and locale files if missing.
* Support for **localization** with `.yml` files under `plugins/<PluginName>/locale/`.
* Locale fallback chain (player locale → default locale → English → root).
* Built-in MiniMessage parsing with placeholder support.
* Simple API to send messages to `Audience` or `Player`, using their preferred locale when available.

### Database Service (`CloudDatabaseService`)

* Provides pooled database connections via **HikariCP**.
* Configuration through `database.yml` (host, port, user, password, SSL, pool settings, etc.).
* Automatic database creation if missing.
* Built-in **migration system** with versioned SQL scripts (`V1__init.sql`, `V2__add_table.sql`, etc.).
* Utility methods for:

    * `withConnection` and `withTransaction`
    * Running migrations
    * Replacing variables (e.g., `${plugin}`, `${database}`) in SQL scripts

### Admin Command (`/cloudconfig`)

* `/cloudconfig reload <PluginName>` – Reload configs and locales for a plugin.
* `/cloudconfig listlocales <PluginName>` – Show available locales for a plugin.
* `/cloudconfig set <PluginName> <file> <path> <value>` – Update config or locale values directly in-game.

---

## 🚀 Getting Started

### Installation

1. Place the compiled **CloudConfig** plugin JAR in your server’s `plugins/` directory.
2. Start the server once to generate default files:

    * `config/` directory for configs
    * `locale/` directory for messages
    * `database.yml` for DB connection settings

### Usage in Your Plugin

#### Registering with CloudConfig

```java
@Override
public void onEnable() {
    CloudConfigService configService = CloudConfigAPI.get(this);
    configService.register(this, new CloudConfigRegistration(
        List.of("config.yml", "database.yml"),
        "en-EN",
        "locale/en-EN.yml",
        true
    ));

    CloudDatabaseService dbService = CloudDatabaseAPI.get(this);
    dbService.ensurePool(this);
}
```

#### Getting Configs

```java
FileConfiguration cfg = configService.getConfig(this, "config.yml");
String value = cfg.getString("some.path");
```

#### Sending Localized Messages

```java
configService.send(player, this, "welcome.message", Map.of("player", player.getName()));
```

#### Running SQL Queries

```java
dbService.withConnection(this, conn -> {
    try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users")) {
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            // handle results
        }
    }
    return null;
});
```

---

## 🗂️ Project Structure

* `api/` – Public API for configs and database
* `core/` – Implementations of services
* `command/` – `/cloudconfig` command handling
* `util/` – Utility classes (e.g., `LocaleUtil`, `YamlUtil`)
* `CloudConfig.java` – Main plugin class

---

## ⚙️ Configuration

### `database.yml`

Example:

```yaml
enabled: true
host: 127.0.0.1
port: 3306
database: myplugin
user: root
password: secret
useSsl: false

schema:
  createIfMissing: true

pool:
  maxPoolSize: 10
  minIdle: 2
  connectionTimeoutMs: 10000
  idleTimeoutMs: 600000
  maxLifetimeMs: 1800000

migrations:
  runOnStartup: true
```

### `V1__init.sql`

Example:

```sql
CREATE TABLE IF NOT EXISTS users (
    id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);
```

---

## 🛠️ Development

* Java 17+
* PaperMC API
* HikariCP (for database pooling)
* Adventure (for MiniMessage + Components)

Build with:

```bash
mvn clean package
```

---

## 📜 License

MIT License – free to use, modify, and distribute.
