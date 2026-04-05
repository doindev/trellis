package io.cwc.service;

import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolProperties;

import io.cwc.credentials.ConnectionPoolParameters;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages pooled database connections.
 *
 * <p>JDBC pools are built-in (no external driver imports — drivers are resolved at runtime
 * from whatever modules are on the classpath).
 *
 * <p>Non-JDBC stores (MongoDB, Redis, Neo4j) register themselves via {@link #registerProvider}
 * from their own modules so that cwc-core has <b>zero</b> driver dependencies.
 */
@Slf4j
@Service
public class DatabaseConnectionPoolService {

    private static final long IDLE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes
    private static final long EVICTION_INTERVAL_MS = 5 * 60 * 1000L; // 5 minutes

    private record PoolEntry<T>(T pool, long lastUsed) {}

    // ── Pluggable pool providers (registered by database modules) ──

    @FunctionalInterface
    public interface PoolFactory<T> {
        T create(Map<String, Object> credentials);
    }

    @FunctionalInterface
    public interface PoolCloser<T> {
        void close(T pool) throws Exception;
    }

    private record ProviderEntry<T>(
        ConcurrentHashMap<String, PoolEntry<T>> pools,
        PoolFactory<T> factory,
        PoolCloser<T> closer
    ) {}

    private final ConcurrentHashMap<String, ProviderEntry<?>> providers = new ConcurrentHashMap<>();

    /**
     * Database modules call this at startup to register their pool factory.
     * Example from cwc-neo4j:
     * <pre>
     *   poolService.registerProvider("neo4j", creds -&gt; createDriver(creds), Driver::close);
     * </pre>
     */
    public <T> void registerProvider(String type, PoolFactory<T> factory, PoolCloser<T> closer) {
        providers.put(type, new ProviderEntry<>(new ConcurrentHashMap<>(), factory, closer));
        log.info("Registered database pool provider: {}", type);
    }

    /**
     * Get or create a pooled connection for the given type.
     * Works for any type registered via {@link #registerProvider}.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPool(String type, Map<String, Object> credentials) {
        ProviderEntry<T> provider = (ProviderEntry<T>) providers.get(type);
        if (provider == null) {
            throw new IllegalStateException(
                "No pool provider registered for '" + type + "'. "
                + "Add the corresponding cwc-" + type + " module to your classpath.");
        }
        String key = poolKey(type, credentials);
        PoolEntry<T> entry = provider.pools().get(key);
        if (entry != null) {
            provider.pools().put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
            return entry.pool();
        }
        synchronized (this) {
            entry = provider.pools().get(key);
            if (entry != null) {
                provider.pools().put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
                return entry.pool();
            }
            T pool = provider.factory().create(credentials);
            provider.pools().put(key, new PoolEntry<>(pool, System.currentTimeMillis()));
            log.info("Created {} pool (key={})", type, key.substring(0, 8));
            return pool;
        }
    }

    // ── JDBC (built-in — no external driver imports) ──

    private final ConcurrentHashMap<String, PoolEntry<org.apache.tomcat.jdbc.pool.DataSource>> jdbcPools = new ConcurrentHashMap<>();

    public DataSource getJdbcPool(Map<String, Object> credentials, String dbType) {
        String key = poolKey(dbType, credentials);
        PoolEntry<org.apache.tomcat.jdbc.pool.DataSource> entry = jdbcPools.get(key);
        if (entry != null && entry.pool().getPool() != null) {
            jdbcPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
            return entry.pool();
        }
        synchronized (this) {
            entry = jdbcPools.get(key);
            if (entry != null && entry.pool().getPool() != null) {
                jdbcPools.put(key, new PoolEntry<>(entry.pool(), System.currentTimeMillis()));
                return entry.pool();
            }
            org.apache.tomcat.jdbc.pool.DataSource ds = createJdbcPool(credentials, dbType);
            jdbcPools.put(key, new PoolEntry<>(ds, System.currentTimeMillis()));
            log.info("Created JDBC connection pool for {} (key={})", dbType, key.substring(0, 8));
            return ds;
        }
    }

    // ── Convenience accessors (delegates to pluggable providers) ──
    // Return types are erased to Object at compile time but callers in their
    // own modules cast to the concrete driver type they depend on.

    @SuppressWarnings("unchecked")
    public <T> T getMongoClient(Map<String, Object> credentials) { return (T) getPool("mongo", credentials); }
    @SuppressWarnings("unchecked")
    public <T> T getJedisPool(Map<String, Object> credentials)   { return (T) getPool("redis", credentials); }
    @SuppressWarnings("unchecked")
    public <T> T getNeo4jDriver(Map<String, Object> credentials) { return (T) getPool("neo4j", credentials); }

    // ── Pool creation (JDBC only) ──

    private org.apache.tomcat.jdbc.pool.DataSource createJdbcPool(Map<String, Object> credentials, String dbType) {
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

        Map<String, Object> poolCfg = ConnectionPoolParameters.getPoolConfig(credentials);

        PoolProperties props = new PoolProperties();
        props.setUrl(jdbcUrl);
        props.setUsername(username);
        props.setPassword(password);
        props.setInitialSize(intVal(poolCfg, "initialSize", 0));
        props.setMaxActive(intVal(poolCfg, "maxActive", 10));
        props.setMaxIdle(intVal(poolCfg, "maxIdle", 10));
        props.setMinIdle(intVal(poolCfg, "minIdle", 1));
        props.setMaxWait(intVal(poolCfg, "maxWaitMillis", 30_000));
        props.setMinEvictableIdleTimeMillis(10 * 60 * 1000);
        props.setMaxAge(30 * 60 * 1000L);
        props.setTimeBetweenEvictionRunsMillis(60_000);
        props.setName("cwc-" + dbType + "-" + poolKey(dbType, credentials).substring(0, 8));

        String defaultValidation = "oracle".equals(dbType) ? "SELECT 1 FROM DUAL" : "SELECT 1";
        props.setValidationQuery(stringVal(poolCfg, "validationQuery", defaultValidation));
        props.setTestOnBorrow(boolVal(poolCfg, "testOnBorrow", false));
        props.setTestWhileIdle(boolVal(poolCfg, "testWhileIdle", false));
        props.setValidationInterval(intVal(poolCfg, "validationInterval", 3000));

        props.setRemoveAbandoned(boolVal(poolCfg, "removeAbandoned", false));
        props.setRemoveAbandonedTimeout(intVal(poolCfg, "removeAbandonedTimeout", 60));
        props.setLogAbandoned(boolVal(poolCfg, "logAbandoned", false));

        if (List.of("postgres", "timescaledb", "questdb", "cratedb").contains(dbType)
                && Boolean.TRUE.equals(credentials.get("ssl"))) {
            props.setConnectionProperties("ssl=true;sslmode=require;");
        }

        return new org.apache.tomcat.jdbc.pool.DataSource(props);
    }

    // ── Idle eviction ──

    @Scheduled(fixedRate = EVICTION_INTERVAL_MS)
    @SuppressWarnings("unchecked")
    public void evictIdlePools() {
        long now = System.currentTimeMillis();

        jdbcPools.forEach((key, entry) -> {
            if (now - entry.lastUsed() > IDLE_TIMEOUT_MS) {
                jdbcPools.remove(key);
                try { entry.pool().close(); } catch (Exception e) { log.warn("Error closing JDBC pool", e); }
                log.info("Evicted idle JDBC pool (key={})", key.substring(0, 8));
            }
        });

        providers.forEach((type, provider) -> {
            var typedProvider = (ProviderEntry<Object>) provider;
            typedProvider.pools().forEach((key, entry) -> {
                if (now - entry.lastUsed() > IDLE_TIMEOUT_MS) {
                    typedProvider.pools().remove(key);
                    try { typedProvider.closer().close(entry.pool()); } catch (Exception e) { log.warn("Error closing {} pool", type, e); }
                    log.info("Evicted idle {} pool (key={})", type, key.substring(0, 8));
                }
            });
        });
    }

    @PreDestroy
    @SuppressWarnings("unchecked")
    public void closeAllPools() {
        log.info("Shutting down all database connection pools...");
        jdbcPools.values().forEach(e -> { try { e.pool().close(); } catch (Exception ex) { /* ignore */ } });
        jdbcPools.clear();

        providers.forEach((type, provider) -> {
            var typedProvider = (ProviderEntry<Object>) provider;
            typedProvider.pools().values().forEach(e -> {
                try { typedProvider.closer().close(e.pool()); } catch (Exception ex) { /* ignore */ }
            });
            typedProvider.pools().clear();
        });
    }

    // ── Helpers ──

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

    private boolean boolVal(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
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
