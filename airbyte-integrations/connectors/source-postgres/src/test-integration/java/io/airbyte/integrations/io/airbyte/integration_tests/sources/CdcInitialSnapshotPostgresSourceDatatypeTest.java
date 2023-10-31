/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.io.airbyte.integration_tests.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import io.airbyte.cdk.db.Database;
import io.airbyte.cdk.db.factory.DSLContextFactory;
import io.airbyte.cdk.db.factory.DatabaseDriver;
import io.airbyte.cdk.db.jdbc.JdbcUtils;
import io.airbyte.cdk.integrations.standardtest.source.TestDataHolder;
import io.airbyte.cdk.integrations.standardtest.source.TestDestinationEnv;
import io.airbyte.cdk.integrations.util.HostPortResolver;
import io.airbyte.commons.features.EnvVariableFeatureFlags;
import io.airbyte.commons.json.Jsons;
import io.airbyte.protocol.models.JsonSchemaType;
import java.util.List;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
public class CdcInitialSnapshotPostgresSourceDatatypeTest extends AbstractPostgresSourceDatatypeTest {

  private static final String SCHEMA_NAME = "test";
  private static final String SLOT_NAME_BASE = "debezium_slot";
  private static final String PUBLICATION = "publication";
  private static final int INITIAL_WAITING_SECONDS = 30;

  @SystemStub
  private EnvironmentVariables environmentVariables;

  @Override
  protected Database setupDatabase() throws Exception {
    environmentVariables.set(EnvVariableFeatureFlags.USE_STREAM_CAPABLE_STATE, "true");
    container = new PostgreSQLContainer<>("postgres:14-alpine")
        .withCopyFileToContainer(MountableFile.forClasspathResource("postgresql.conf"),
            "/etc/postgresql/postgresql.conf")
        .withCommand("postgres -c config_file=/etc/postgresql/postgresql.conf");
    container.start();

    /**
     * The publication is not being set as part of the config and because of it
     * {@link io.airbyte.integrations.source.postgres.PostgresSource#isCdc(JsonNode)} returns false, as
     * a result no test in this class runs through the cdc path.
     */
    final JsonNode replicationMethod = Jsons.jsonNode(ImmutableMap.builder()
        .put("method", "CDC")
        .put("replication_slot", SLOT_NAME_BASE)
        .put("publication", PUBLICATION)
        .put("initial_waiting_seconds", INITIAL_WAITING_SECONDS)
        .build());
    config = Jsons.jsonNode(ImmutableMap.builder()
        .put(JdbcUtils.HOST_KEY, HostPortResolver.resolveHost(container))
        .put(JdbcUtils.PORT_KEY, HostPortResolver.resolvePort(container))
        .put(JdbcUtils.DATABASE_KEY, container.getDatabaseName())
        .put(JdbcUtils.SCHEMAS_KEY, List.of(SCHEMA_NAME))
        .put(JdbcUtils.USERNAME_KEY, container.getUsername())
        .put(JdbcUtils.PASSWORD_KEY, container.getPassword())
        .put("replication_method", replicationMethod)
        .put("is_test", true)
        .put(JdbcUtils.SSL_KEY, false)
        .build());

    dslContext = DSLContextFactory.create(
        config.get(JdbcUtils.USERNAME_KEY).asText(),
        config.get(JdbcUtils.PASSWORD_KEY).asText(),
        DatabaseDriver.POSTGRESQL.getDriverClassName(),
        String.format(DatabaseDriver.POSTGRESQL.getUrlFormatString(),
            container.getHost(),
            container.getFirstMappedPort(),
            config.get(JdbcUtils.DATABASE_KEY).asText()),
        SQLDialect.POSTGRES);
    final Database database = new Database(dslContext);

    database.query(ctx -> {
      ctx.execute(
          "SELECT pg_create_logical_replication_slot('" + SLOT_NAME_BASE + "', 'pgoutput');");
      ctx.execute("CREATE PUBLICATION " + PUBLICATION + " FOR ALL TABLES;");
      ctx.execute("CREATE EXTENSION hstore;");
      return null;
    });

    database.query(ctx -> ctx.fetch("CREATE SCHEMA TEST;"));
    database.query(ctx -> ctx.fetch("CREATE TYPE mood AS ENUM ('sad', 'ok', 'happy');"));
    database.query(ctx -> ctx.fetch("CREATE TYPE inventory_item AS (\n"
        + "    name            text,\n"
        + "    supplier_id     integer,\n"
        + "    price           numeric\n"
        + ");"));

    database.query(ctx -> ctx.fetch("SET TIMEZONE TO 'MST'"));
    return database;
  }

  @Override
  protected void tearDown(final TestDestinationEnv testEnv) {
    dslContext.close();
    container.close();
  }

  public boolean testCatalog() {
    return true;
  }

  @Override
  protected void addHstoreTest() {
    addDataTypeTestData(
        TestDataHolder.builder()
            .sourceType("hstore")
            .airbyteType(JsonSchemaType.STRING)
            .addInsertValues("""
                             '"paperback" => "243","publisher" => "postgresqltutorial.com",
                             "language"  => "English","ISBN-13" => "978-1449370000",
                             "weight"    => "11.2 ounces"'
                             """, null)
            .addExpectedValues(
                //
                "\"weight\"=>\"11.2 ounces\", \"ISBN-13\"=>\"978-1449370000\", \"language\"=>\"English\", \"paperback\"=>\"243\", \"publisher\"=>\"postgresqltutorial.com\"",
                null)
            .build());
  }

}
