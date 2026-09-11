package dev.anchorlight.StoneLib.storage.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A HikariCP-backed MySQL connection pool.
 *
 * <p>Every method here blocks on the database, so none of them may be called from the server
 * thread. Hand the work to {@code SchedulerService.supplyAsync} and take the result back on the
 * main thread.</p>
 */
public final class ConnectionPool implements AutoCloseable {

    private final HikariDataSource dataSource;

    public ConnectionPool(DatabaseConfig config, String poolName) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName(poolName);
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setConnectionTimeout(config.connectionTimeoutMillis());
        // Keep pooled connections comfortably below MySQL's default 8h wait_timeout so the pool
        // never hands out a connection the server has already dropped.
        hikari.setMaxLifetime(30 * 60 * 1000L);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(hikari);
    }

    /** Borrows a connection. The caller closes it, which returns it to the pool. */
    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Runs a statement that returns an update count. */
    public int update(String sql, Object... params) throws SQLException {
        try (Connection conn = connection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        }
    }

    /** Runs a query, mapping every row through {@code mapper}. */
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (Connection conn = connection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        }
    }

    /** Runs a query expected to match at most one row, returning null when it matches none. */
    public <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> results = query(sql, mapper, params);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Runs {@code work} inside a transaction, committing on return and rolling back on any
     * exception.
     */
    public void transaction(SqlConsumer work) throws SQLException {
        try (Connection conn = connection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                work.accept(conn);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    /** Maps one {@link ResultSet} row to a value. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** A consumer of a {@link Connection} that is allowed to throw {@link SQLException}. */
    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
