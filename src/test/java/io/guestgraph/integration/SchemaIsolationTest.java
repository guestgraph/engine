package io.guestgraph.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spec 006: every engine object lives in one schema, and a role that owns another schema in the
 * same database cannot read a single engine table. The catalog query is SC-001 verbatim, the second
 * role is SC-003 verbatim.
 */
class SchemaIsolationTest extends PostgresIntegrationTest {

  static final List<String> ENGINE_TABLES =
      List.of(
          "tenant",
          "api_key",
          "source_system",
          "guest",
          "source_record",
          "record_identifier",
          "record_block_key",
          "record_object",
          "identifier",
          "merge_event",
          "resolution_link",
          "match_review",
          "negative_match_rule",
          "identifier_quality_rule",
          "flyway_schema_history");

  @Test
  @DisplayName("every engine table sits in the engine schema and none in public")
  void everyTableLivesInTheEngineSchema() {
    List<String> inEngine =
        jdbc.sql(
                "select table_name from information_schema.tables"
                    + " where table_schema = 'engine' order by table_name")
            .query(String.class)
            .list();
    List<String> inPublic =
        jdbc.sql(
                "select table_name from information_schema.tables"
                    + " where table_schema = 'public' and table_type = 'BASE TABLE'")
            .query(String.class)
            .list();

    assertThat(inEngine).containsAll(ENGINE_TABLES);
    assertThat(inPublic).doesNotContainAnyElementsOf(ENGINE_TABLES);
  }

  @Test
  @DisplayName("a role owning another schema in the same database is refused on every engine table")
  void anotherRoleCannotReadEngineTables() throws SQLException {
    jdbc.sql("drop schema if exists probe cascade").update();
    jdbc.sql("drop role if exists probe").update();
    jdbc.sql("create role probe login password 'probe'").update();
    jdbc.sql("create schema probe authorization probe").update();

    try (Connection probe = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "probe", "probe");
        Statement statement = probe.createStatement()) {
      for (String table : ENGINE_TABLES) {
        assertThatThrownBy(() -> statement.executeQuery("select count(*) from engine." + table))
            .as("probe reads engine.%s", table)
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("permission denied");
      }
    }
  }
}
