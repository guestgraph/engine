package io.guestgraph.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * Spec 006: every engine object lives in one schema, and a role that owns another schema in the
 * same database cannot read a single engine table. The catalog query is SC-001 verbatim, the second
 * role is SC-003 verbatim.
 */
class SchemaIsolationTest extends PostgresIntegrationTest {

  /** The name the suite ran with, so the test holds for any DATABASE_SCHEMA (FR-001). */
  @Value("${spring.datasource.hikari.schema}")
  String schema;

  static final List<String> ENGINE_FUNCTIONS =
      List.of("guard_append_only", "source_record_immutable");

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
  @DisplayName("every engine table and function sits in the engine schema and none in public")
  void everyTableLivesInTheEngineSchema() {
    List<String> inSchema =
        jdbc.sql(
                "select table_name from information_schema.tables"
                    + " where table_schema = :schema order by table_name")
            .param("schema", schema)
            .query(String.class)
            .list();
    List<String> inPublic =
        jdbc.sql(
                "select table_name from information_schema.tables"
                    + " where table_schema = 'public' and table_type = 'BASE TABLE'")
            .query(String.class)
            .list();
    List<String> functionsInSchema =
        jdbc.sql(
                "select p.proname from pg_proc p join pg_namespace n on n.oid = p.pronamespace"
                    + " where n.nspname = :schema")
            .param("schema", schema)
            .query(String.class)
            .list();

    assertThat(inSchema).containsAll(ENGINE_TABLES);
    assertThat(inPublic).doesNotContainAnyElementsOf(ENGINE_TABLES);
    assertThat(functionsInSchema).containsAll(ENGINE_FUNCTIONS);
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
        String read = "select count(*) from " + schema + "." + table;
        // SQLState 42501, insufficient_privilege, says the same in every server locale.
        assertThatThrownBy(() -> statement.executeQuery(read))
            .as("probe reads %s.%s", schema, table)
            .isInstanceOfSatisfying(
                SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
      }
    } finally {
      // Leave the shared container as the harness found it.
      jdbc.sql("drop schema if exists probe cascade").update();
      jdbc.sql("drop role if exists probe").update();
    }
  }
}
