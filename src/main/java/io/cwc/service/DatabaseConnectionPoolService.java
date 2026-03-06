package io.cwc.service;

import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Slf4j
@Service
public class DatabaseConnectionPoolService {

    private static final long IDLE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes
    private static final long EVICTION_INTERVAL_MS = 5 * 60 * 1000L; // 5 minutes

    private record PoolEntry<T>(T pool, long lastUsed) {}

    private final ConcurrentHashMap<String, PoolEntry<HikariDataSource>> jdbcPools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PoolEntry<MongoClient>> mongoPools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PoolEntry<JedisPool>> redisPools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PoolEntry<Driver>> neo4jPools = new ConcurrentHashMap<>();

    public DataSource getJdbcPool(Map<String, Object> credentials, String dbType) {
        String key = poolKey(dbType, credentials);
        PoolEntry<HikariDataSource> entry = jdbcPools.get(key);
        if (entry != null && !entry.pool().isClosed()) {
            jdbcPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
            return entry.pool();
        }
        synchronized (this) {
            entry = jdbcPools.get(key);
            if (entry != null && !entry.pool().isClosed()) {
                jdbcPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
                return entry.pool();
            }
            HikariDataSource ds = createHikariPool(credentials, dbType);
            jdbcPools.put(key, new PoolEntry<>(ds, System.currentTimeMillis()));
            log.info("Created JDBC connection pool for {} (key={})", dbType, key.substring(0, 8));
            return ds;
        }
    }

    public MongoClient getMongoClient(Map<String, Object> credentials) {
        String key = poolKey("mongo", credentials);
        PoolEntry<MongoClient> entry = mongoPools.get(key);
        if (entry != null) {
            mongoPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
            return entry.pool();
        }
        synchronized (this) {
            entry = mongoPools.get(key);
            if (entry != null) {
                mongoPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
                return entry.pool();
            }
            MongoClient client = createMongoClient(credentials);
            mongoPools.put(key, new PoolEntry<>(client, System.currentTimeMillis()));
            log.info("Created MongoDB client pool (key={})", key.substring(0, 8));
            return client;
        }
    }

    public JedisPool getJedisPool(Map<String, Object> credentials) {
        String key = poolKey("redis", credentials);
        PoolEntry<JedisPool> entry = redisPools.get(key);
        if (entry != null && !entry.pool().isClosed()) {
            redisPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
            return entry.pool();
        }
        synchronized (this) {
            entry = redisPools.get(key);
            if (entry != null && !entry.pool().isClosed()) {
                redisPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
                return entry.pool();
            }
            JedisPool pool = createJedisPool(credentials);
            redisPools.put(key, new PoolEntry<>(pool, System.currentTimeMillis()));
            log.info("Created Redis connection pool (key={})", key.substring(0, 8));
            return pool;
        }
    }

    public Driver getNeo4jDriver(Map<String, Object> credentials) {
        String key = poolKey("neo4j", credentials);
        PoolEntry<Driver> entry = neo4jPools.get(key);
        if (entry != null) {
            neo4jPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
            return entry.pool();
        }
        synchronized (this) {
            entry = neo4jPools.get(key);
            if (entry != null) {
                neo4jPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
                return entry.pool();
            }
            Driver driver = createNeo4jDriver(credentials);
            neo4jPools.put(key, new PoolEntry<>(driver, System.currentTimeMillis()));
            log.info("Created Neo4j driver pool (key={})", key.substring(0, 8));
            return driver;
        }
    }

    // --- Pool creation ---

    private HikariDataSource createHikariPool(Map<String, Object> credentials, String dbType) {
        String host = stringVal(credentials, "host", "localhost");
        int port = intVal(credentials, "port", defaultPort(dbType));
        String database = stringVal(credentials, "database", "");
        String username = stringVal(credentials, "username", stringVal(credentials, "user", ""));
        String password = stringVal(credentials, "password", "");

        String jdbcUrl = switch (dbType) {
            case "postgres", "timescaledb" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + database;
            case "oracle" -> {
                String connectAs = stringVal(credentials, "connectAs", "serviceName");
                if ("sid".equals(connectAs)) {
                    yield "jdbc:oracle:thin:@" + host + ":" + port + ":" + database;
                } else {
                    yield "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
                }
            }
            case "mssql" -> {
                StringBuilder url = new StringBuilder("jdbc:sqlserver://");
                url.append(host).append(":").append(port);
                if (!database.isEmpty()) url.append(";databaseName=").append(database);
                url.append(";encrypt=true;trustServerCertificate=true");
                yield url.toString();
            }
            case "cratedb" -> "jdbc:crate://" + host + ":" + port + "/";
            case "questdb" -> "jdbc:postgresql://" + host + ":" + port + "/" + (database.isEmpty() ? "qdb" : database);
            default -> throw new IllegalArgumentException("Unsupported DB type: " + dbType);
        };

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setIdleTimeout(10 * 60 * 1000L); // 10 minutes
        config.setMaxLifetime(30 * 60 * 1000L); // 30 minutes
        config.setConnectionTimeout(30_000L);
        config.setKeepaliveTime(60_000L); // send validation query every 60s on idle connections
        config.setPoolName("cwc-" + dbType + "-" + poolKey(dbType, credentials).substring(0, 8));

        // Validate connections before use — Oracle doesn't support isValid() on all drivers
        if ("oracle".equals(dbType)) {
            config.setConnectionTestQuery("SELECT 1 FROM DUAL");
        }

        // SSL for postgres-compatible drivers
        if (List.of("postgres", "timescaledb", "questdb", "cratedb").contains(dbType)
                && Boolean.TRUE.equals(credentials.get("ssl"))) {
            config.addDataSourceProperty("ssl", "true");
            config.addDataSourceProperty("sslmode", "require");
        }

        return new HikariDataSource(config);
    }

    private MongoClient createMongoClient(Map<String, Object> credentials) {
        String host = stringVal(credentials, "host", "localhost");
        int port = intVal(credentials, "port", 27017);
        String database = stringVal(credentials, "database", "admin");
        String username = stringVal(credentials, "username", "");
        String password = stringVal(credentials, "password", "");
        boolean tls = Boolean.TRUE.equals(credentials.get("tls"));

        StringBuilder connStr = new StringBuilder("mongodb://");
        if (!username.isEmpty()) {
            connStr.append(username);
            if (!password.isEmpty()) {
                connStr.append(":").append(password);
            }
            connStr.append("@");
        }
        connStr.append(host).append(":").append(port).append("/").append(database);
        if (tls) {
            connStr.append("?tls=true");
        }

        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(connStr.toString()))
            .applyToConnectionPoolSettings(pool -> pool
                .maxSize(10)
                .minSize(1)
                .maxConnectionIdleTime(10, java.util.concurrent.TimeUnit.MINUTES))
            .applyToServerSettings(server -> server
                .heartbeatFrequency(30, java.util.concurrent.TimeUnit.SECONDS))
            .build();
        return MongoClients.create(settings);
    }

    private JedisPool createJedisPool(Map<String, Object> credentials) {
        String host = stringVal(credentials, "host", "localhost");
        int port = intVal(credentials, "port", 6379);
        String password = stringVal(credentials, "password", "");
        int database = intVal(credentials, "database", 0);
        boolean ssl = Boolean.TRUE.equals(credentials.get("ssl"));

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);   // PING before returning connection
        poolConfig.setTestWhileIdle(true);  // PING idle connections periodically

        if (!password.isEmpty()) {
            return new JedisPool(poolConfig, host, port, 30_000, password, database, ssl);
        } else {
            return new JedisPool(poolConfig, host, port, 30_000, null, database, ssl);
        }
    }

    private Driver createNeo4jDriver(Map<String, Object> credentials) {
        String uri = stringVal(credentials, "uri", "bolt://localhost:7687");
        String username = stringVal(credentials, "username", "neo4j");
        String password = stringVal(credentials, "password", "");

        Config config = Config.builder()
            .withConnectionLivenessCheckTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .withConnectionTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .withMaxConnectionPoolSize(10)
            .build();
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
    }

    // --- Idle eviction ---

    @Scheduled(fixedRate = EVICTION_INTERVAL_MS)
    public void evictIdlePools() {
        long now = System.currentTimeMillis();

        jdbcPools.forEach((key, entry) -> {
            if (now - entry.lastUsed() > IDLE_TIMEOUT_MS) {
                jdbcPools.remove(key);
                try { entry.pool().close(); } catch (Exception e) { log.warn("Error closing JDBC pool", e); }
                log.info("Evicted idle JDBC pool (key={})", key.substring(0, 8));
            }
        });

        mongoPools.forEach((key, entry) -> {
            if (now - entry.lastUsed() > IDLE_TIMEOUT_MS) {
                mongoPools.remove(key);
                try { entry.pool().close(); } catch (Exception e) { log.warn("Error closing Mongo client", e); }
                log.info("Evicted idle MongoDB pool (key={})", key.substring(0, 8));
            }
        });

        redisPools.forEach((key, entry) -> {
            if (now - entry.lastUsed() > IDLE_TIMEOUT_MS) {
                redisPools.remove(key);
                try { entry.pool().close(); } catch (Exception e) { log.warn("Error closing Redis pool", e); }
                log.info("Evicted idle Redis pool (key={})", key.substring(0, 8));
            }
        });

        neo4jPools.forEach((key, entry) -> {
            if (now - entry.lastUsed() > IDLE_TIMEOUT_MS) {
                neo4jPools.remove(key);
                try { entry.pool().close(); } catch (Exception e) { log.warn("Error closing Neo4j driver", e); }
                log.info("Evicted idle Neo4j pool (key={})", key.substring(0, 8));
            }
        });
    }

    @PreDestroy
    public void closeAllPools() {
        log.info("Shutting down all database connection pools...");
        jdbcPools.values().forEach(e -> { try { e.pool().close(); } catch (Exception ex) { /* ignore */ } });
        mongoPools.values().forEach(e -> { try { e.pool().close(); } catch (Exception ex) { /* ignore */ } });
        redisPools.values().forEach(e -> { try { e.pool().close(); } catch (Exception ex) { /* ignore */ } });
        neo4jPools.values().forEach(e -> { try { e.pool().close(); } catch (Exception ex) { /* ignore */ } });
        jdbcPools.clear();
        mongoPools.clear();
        redisPools.clear();
        neo4jPools.clear();
    }

    // --- Helpers ---

    private String poolKey(String prefix, Map<String, Object> credentials) {
        TreeMap<String, Object> sorted = new TreeMap<>(credentials);
        String raw = prefix + ":" + sorted;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return raw;
        }
    }

    private String stringVal(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    private int intVal(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private int defaultPort(String dbType) {
        return switch (dbType) {
            case "postgres", "timescaledb", "questdb" -> 5432;
            case "mysql" -> 3306;
            case "oracle" -> 1521;
            case "mssql" -> 1433;
            case "cratedb" -> 5432;
            default -> 0;
        };
    }
}
