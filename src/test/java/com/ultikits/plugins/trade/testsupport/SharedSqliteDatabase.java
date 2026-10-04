package com.ultikits.plugins.trade.testsupport;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.data.sqlite.SQLiteDataOperator;
import com.ultikits.ultitools.manager.DataSourceTransactionManager;

import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test support: one SQLite database file that several "servers" open, each through its own instance of
 * the framework's real {@link SQLiteDataOperator}.
 * <p>
 * Two operators on one file stand in for two servers sharing one MySQL database: each statement is
 * its own auto-committed transaction, the table has the framework's {@code PRIMARY KEY (id)}, and
 * {@code updateCounted} / {@code updateIf} are the framework's own single-statement writes whose
 * affected-row count decides the result, and {@code transaction} is a real database transaction through
 * the framework's {@code DataSourceTransactionManager} -- the properties the module's cross-server code
 * relies on.
 */
public final class SharedSqliteDatabase {

    private final Path file;

    private SharedSqliteDatabase(Path file) {
        this.file = file;
    }

    /** A fresh, empty database file in {@code dir}. */
    public static SharedSqliteDatabase in(Path dir) throws IOException {
        return new SharedSqliteDatabase(Files.createTempFile(dir, "shared", ".db"));
    }

    /** What one server opens: a new operator on the shared file. */
    public <T extends BaseDataEntity<String>> DataOperator<T> openAs(Class<T> type) {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
        SQLiteDataOperator<T> operator = new SQLiteDataOperator<>(source, type);
        // As SQLiteDataStore wires it, so DataOperator#transaction is a real database transaction.
        operator.setTransactionManager(new DataSourceTransactionManager(source));
        return operator;
    }
}
