/**
 * Copyright (C) 2020 Locomizer team and Contributors
 * This project uses New BSD license with do no evil clause. For full text, check the LICENSE file in the root directory.
 */
package io.github.pastorgl.datacooker.storage.jdbc;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import io.github.pastorgl.datacooker.data.DataStream;
import io.github.pastorgl.datacooker.data.Record;
import io.github.pastorgl.datacooker.data.StreamType;
import io.github.pastorgl.datacooker.metadata.AdapterMeta;
import io.github.pastorgl.datacooker.metadata.DefinitionMetaBuilder;
import io.github.pastorgl.datacooker.storage.OutputAdapter;
import org.apache.spark.api.java.JavaRDD;
import org.sparkproject.guava.collect.Iterators;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

@SuppressWarnings("unused")
public class JDBCOutput extends OutputAdapter {
    private String dbDriver;
    private String dbUrl;
    private String dbUser;
    private String dbPassword;

    private int batchSize;

    private char delimiter;
    private String[] columns;

    @Override
    public AdapterMeta meta() {
        return new AdapterMeta("jdbc", "JDBC adapter which performs batch INSERT VALUES of attributes (in order of incidence)" +
                " into a table in the configured database.",
                "Output path is just a table name",

                new StreamType[]{StreamType.Columnar},
                new DefinitionMetaBuilder()
                        .def(JDBCStorage.JDBC_DRIVER, "JDBC driver, fully qualified class name")
                        .def(JDBCStorage.JDBC_URL, "JDBC connection string URL")
                        .def(JDBCStorage.JDBC_USER, "JDBC connection user", null, "By default, user isn't set")
                        .def(JDBCStorage.JDBC_PASSWORD, "JDBC connection password", null, "By default, use no password")
                        .def(JDBCStorage.BATCH_SIZE, "Batch size for SQL INSERTs", Integer.class,
                                500, "By default, use 500 records")
                        .def(JDBCStorage.COLUMNS, "Columns to write",
                                String[].class, null, "By default, select all columns")
                        .build()
        );
    }

    @Override
    protected void configure() {
        dbDriver = resolver.get(JDBCStorage.JDBC_DRIVER);
        dbUrl = resolver.get(JDBCStorage.JDBC_URL);
        dbUser = resolver.get(JDBCStorage.JDBC_USER);
        dbPassword = resolver.get(JDBCStorage.JDBC_PASSWORD);

        batchSize = resolver.get(JDBCStorage.BATCH_SIZE);
        columns = resolver.get(JDBCStorage.COLUMNS);
    }

    @Override
    public void save(String path, DataStream rdd) {
        final String _dbDriver = dbDriver;
        final String _dbUrl = dbUrl;
        final String _dbUser = dbUser;
        final String _dbPassword = dbPassword;

        int _batchSize = batchSize;

        final char _delimiter = delimiter;
        final String[] _cols = columns;
        final String _table = path;

        ((JavaRDD<Record>) rdd.get()).mapPartitions(partition -> {
            Connection conn = null;
            PreparedStatement ps = null;
            try {
                Class.forName(_dbDriver);

                Properties properties = new Properties();
                properties.setProperty("user", _dbUser);
                properties.setProperty("password", _dbPassword);

                conn = DriverManager.getConnection(_dbUrl, properties);

                CSVParser parser = new CSVParserBuilder().withSeparator(_delimiter).build();

                StringBuilder sb = new StringBuilder("INSERT INTO " + _table + " VALUES ");
                sb.append("(");
                for (int i = 0, j = 0; i < _cols.length; i++) {
                    if (!_cols[i].equals("_")) {
                        if (j > 0) {
                            sb.append(",");
                        }
                        sb.append("?");
                        j++;
                    }
                }
                sb.append(")");

                ps = conn.prepareStatement(sb.toString());
                int b = 0;
                while (partition.hasNext()) {
                    Record row = partition.next();

                    for (int i = 0, j = 1; i < _cols.length; i++) {
                        if (!_cols[i].equals("_")) {
                            ps.setObject(j++, row.asIs(_cols[i]));
                        }
                    }
                    ps.addBatch();

                    if (b == _batchSize) {
                        ps.executeBatch();

                        ps.clearBatch();
                        b = 0;
                    }

                    b++;
                }
                if (b != 0) {
                    ps.executeBatch();
                }

                return Iterators.emptyIterator();
            } catch (SQLException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                if (ps != null) {
                    ps.close();
                }
                if (conn != null) {
                    conn.close();
                }
            }
        }).count();
    }
}
