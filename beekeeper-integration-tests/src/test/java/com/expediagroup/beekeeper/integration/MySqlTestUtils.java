/**
 * Copyright (C) 2019-2020 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expediagroup.beekeeper.integration;

import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;

import static com.expediagroup.beekeeper.core.model.LifecycleEventType.UNREFERENCED;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.expediagroup.beekeeper.core.model.EntityHousekeepingPath;
import com.expediagroup.beekeeper.core.model.PathStatus;

class MySqlTestUtils {

  private static final String DROP_TABLE = "DROP TABLE IF EXISTS beekeeper.%s;";
  private static final String SELECT_TABLE = "SELECT * FROM beekeeper.%s where lifecycle_type = '%s' order by path;";
  private static final String INSERT_PATH = "INSERT INTO beekeeper.path (%s) VALUES (%s);";

  private static final String CLEANUP_ATTEMPTS = "cleanup_attempts";
  private static final String CLEANUP_DELAY = "cleanup_delay";
  private static final String LIFECYCLE_TYPE = "lifecycle_type";
  private static final String CLEANUP_TIMESTAMP = "cleanup_timestamp";
  private static final String CLIENT_ID = "client_id";
  private static final String CREATION_TIMESTAMP = "creation_timestamp";
  private static final String DATABASE_NAME = "database_name";
  private static final String ID = "id";
  private static final String MODIFIED_TIMESTAMP = "modified_timestamp";
  private static final String PATH = "path";
  private static final String PATH_STATUS = "path_status";
  private static final String TABLE_NAME = "table_name";

  private final Connection connection;

  MySqlTestUtils(String jdbcUrl, String username, String password) throws SQLException {
    connection = DriverManager.getConnection(jdbcUrl, username, password);
  }

  void insertPath(String path, String table) throws SQLException {
    String lifecycleType = UNREFERENCED.toString().toLowerCase();

    String fields = String.join(", ", PATH, PATH_STATUS, CLEANUP_DELAY, CLEANUP_TIMESTAMP, TABLE_NAME, LIFECYCLE_TYPE);
    String values = Stream.of(path, PathStatus.SCHEDULED.toString(), "PT1S", Timestamp.valueOf(LocalDateTime.now(UTC)
        .minus(1L, ChronoUnit.DAYS))
        .toString(), table, lifecycleType)
        .map(s -> "\"" + s + "\"")
        .collect(Collectors.joining(", "));

    connection.createStatement()
        .executeUpdate(format(INSERT_PATH, fields, values));
  }

  int unreferencedRowsInTable(String table) throws SQLException {
    return rowsInTable(table, UNREFERENCED.toString());
  }

  private int rowsInTable(String table, String cleanupType) throws SQLException {
    ResultSet resultSet = connection.createStatement()
        .executeQuery(format(SELECT_TABLE, table, cleanupType));
    resultSet.last();
    int rowsInTable = resultSet.getRow();
    return rowsInTable;
  }

  void dropTable(String tableName) throws SQLException {
    connection.createStatement()
        .executeUpdate(format(DROP_TABLE, tableName));
  }

  void close() throws SQLException {
    connection.close();
  }

  List<EntityHousekeepingPath> getUnreferencedPaths() throws SQLException {
    return getPaths(UNREFERENCED.toString());
  }

  List<EntityHousekeepingPath> getPaths(String cleanupType) throws SQLException {
    ResultSet resultSet = connection.createStatement().executeQuery(format(SELECT_TABLE, PATH, cleanupType));
    List<EntityHousekeepingPath> paths = new ArrayList<>();
    while (resultSet.next()) {
      paths.add(map(resultSet));
    }
    return paths;
  }

  private EntityHousekeepingPath map(ResultSet resultSet) throws SQLException {
    EntityHousekeepingPath path = new EntityHousekeepingPath.Builder()
        .id(resultSet.getLong(ID))
        .path(resultSet.getString(PATH))
        .pathStatus(PathStatus.valueOf(resultSet.getString(PATH_STATUS)))
        .cleanupAttempts(resultSet.getInt(CLEANUP_ATTEMPTS))
        .tableName(resultSet.getString(TABLE_NAME))
        .databaseName(resultSet.getString(DATABASE_NAME))
        .creationTimestamp(Timestamp.valueOf(resultSet.getString(CREATION_TIMESTAMP))
            .toLocalDateTime())
        .modifiedTimestamp(Timestamp.valueOf(resultSet.getString(MODIFIED_TIMESTAMP))
            .toLocalDateTime())
        .cleanupDelay(Duration.parse(resultSet.getString(CLEANUP_DELAY)))
        .clientId(resultSet.getString(CLIENT_ID))
        .lifecycleType(resultSet.getString(LIFECYCLE_TYPE))
        .build();
    path.setCleanupTimestamp(Timestamp.valueOf(resultSet.getString(CLEANUP_TIMESTAMP))
        .toLocalDateTime());
    return path;
  }
}
