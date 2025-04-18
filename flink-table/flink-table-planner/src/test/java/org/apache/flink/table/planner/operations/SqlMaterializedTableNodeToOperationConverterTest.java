/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.operations;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.ValidationException;
import org.apache.flink.table.catalog.CatalogMaterializedTable;
import org.apache.flink.table.catalog.CatalogTable;
import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.IntervalFreshness;
import org.apache.flink.table.catalog.ObjectIdentifier;
import org.apache.flink.table.catalog.ObjectPath;
import org.apache.flink.table.catalog.ResolvedCatalogMaterializedTable;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.catalog.TableChange;
import org.apache.flink.table.catalog.UnresolvedIdentifier;
import org.apache.flink.table.catalog.exceptions.DatabaseNotExistException;
import org.apache.flink.table.catalog.exceptions.TableAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.TableNotExistException;
import org.apache.flink.table.operations.Operation;
import org.apache.flink.table.operations.materializedtable.AlterMaterializedTableAsQueryOperation;
import org.apache.flink.table.operations.materializedtable.AlterMaterializedTableRefreshOperation;
import org.apache.flink.table.operations.materializedtable.AlterMaterializedTableResumeOperation;
import org.apache.flink.table.operations.materializedtable.AlterMaterializedTableSuspendOperation;
import org.apache.flink.table.operations.materializedtable.CreateMaterializedTableOperation;
import org.apache.flink.table.operations.materializedtable.DropMaterializedTableOperation;
import org.apache.flink.table.planner.utils.TableFunc0;

import org.apache.flink.shaded.guava33.com.google.common.collect.ImmutableMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test for the materialized table statements for {@link SqlNodeToOperationConversion}. */
public class SqlMaterializedTableNodeToOperationConverterTest
        extends SqlNodeToOperationConversionTestBase {

    @BeforeEach
    public void before() throws TableAlreadyExistException, DatabaseNotExistException {
        super.before();
        final ObjectPath path3 = new ObjectPath(catalogManager.getCurrentDatabase(), "t3");
        final Schema tableSchema =
                Schema.newBuilder()
                        .fromResolvedSchema(
                                ResolvedSchema.of(
                                        Column.physical("a", DataTypes.BIGINT().notNull()),
                                        Column.physical("b", DataTypes.VARCHAR(Integer.MAX_VALUE)),
                                        Column.physical("c", DataTypes.INT()),
                                        Column.physical("d", DataTypes.VARCHAR(Integer.MAX_VALUE))))
                        .build();
        Map<String, String> options = new HashMap<>();
        options.put("connector", "COLLECTION");
        final CatalogTable catalogTable =
                CatalogTable.newBuilder()
                        .schema(tableSchema)
                        .comment("")
                        .partitionKeys(Arrays.asList("b", "c"))
                        .options(options)
                        .build();
        catalog.createTable(path3, catalogTable, true);

        // create materialized table
        final String sql =
                "CREATE MATERIALIZED TABLE base_mtbl (\n"
                        + "   CONSTRAINT ct1 PRIMARY KEY(a) NOT ENFORCED"
                        + ")\n"
                        + "COMMENT 'materialized table comment'\n"
                        + "PARTITIONED BY (a, d)\n"
                        + "WITH (\n"
                        + "  'connector' = 'filesystem', \n"
                        + "  'format' = 'json'\n"
                        + ")\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "REFRESH_MODE = FULL\n"
                        + "AS SELECT * FROM t1";
        final ObjectPath path4 = new ObjectPath(catalogManager.getCurrentDatabase(), "base_mtbl");

        CreateMaterializedTableOperation operation = (CreateMaterializedTableOperation) parse(sql);
        catalog.createTable(path4, operation.getCatalogMaterializedTable(), true);
    }

    @Test
    void testCreateMaterializedTable() {
        final String sql =
                "CREATE MATERIALIZED TABLE mtbl1 (\n"
                        + "   CONSTRAINT ct1 PRIMARY KEY(a) NOT ENFORCED"
                        + ")\n"
                        + "COMMENT 'materialized table comment'\n"
                        + "PARTITIONED BY (a, d)\n"
                        + "WITH (\n"
                        + "  'connector' = 'filesystem', \n"
                        + "  'format' = 'json'\n"
                        + ")\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "REFRESH_MODE = FULL\n"
                        + "AS SELECT * FROM t1";
        Operation operation = parse(sql);
        assertThat(operation).isInstanceOf(CreateMaterializedTableOperation.class);

        CreateMaterializedTableOperation op = (CreateMaterializedTableOperation) operation;
        CatalogMaterializedTable materializedTable = op.getCatalogMaterializedTable();
        assertThat(materializedTable).isInstanceOf(ResolvedCatalogMaterializedTable.class);

        Map<String, String> options = new HashMap<>();
        options.put("connector", "filesystem");
        options.put("format", "json");
        CatalogMaterializedTable expected =
                CatalogMaterializedTable.newBuilder()
                        .schema(
                                Schema.newBuilder()
                                        .column("a", DataTypes.BIGINT().notNull())
                                        .column("b", DataTypes.VARCHAR(Integer.MAX_VALUE))
                                        .column("c", DataTypes.INT())
                                        .column("d", DataTypes.VARCHAR(Integer.MAX_VALUE))
                                        .primaryKeyNamed("ct1", Collections.singletonList("a"))
                                        .build())
                        .comment("materialized table comment")
                        .options(options)
                        .partitionKeys(Arrays.asList("a", "d"))
                        .freshness(IntervalFreshness.ofSecond("30"))
                        .logicalRefreshMode(CatalogMaterializedTable.LogicalRefreshMode.FULL)
                        .refreshMode(CatalogMaterializedTable.RefreshMode.FULL)
                        .refreshStatus(CatalogMaterializedTable.RefreshStatus.INITIALIZING)
                        .definitionQuery("SELECT *\n" + "FROM `builtin`.`default`.`t1`")
                        .build();

        assertThat(((ResolvedCatalogMaterializedTable) materializedTable).getOrigin())
                .isEqualTo(expected);
    }

    @Test
    void testCreateMaterializedTableWithUDTFQuery() {
        functionCatalog.registerCatalogFunction(
                UnresolvedIdentifier.of(
                        ObjectIdentifier.of(
                                catalogManager.getCurrentCatalog(), "default", "myFunc")),
                TableFunc0.class,
                true);

        final String sql =
                "CREATE MATERIALIZED TABLE mtbl1 (\n"
                        + "   CONSTRAINT ct1 PRIMARY KEY(a) NOT ENFORCED"
                        + ")\n"
                        + "COMMENT 'materialized table comment'\n"
                        + "PARTITIONED BY (a)\n"
                        + "WITH (\n"
                        + "  'connector' = 'filesystem', \n"
                        + "  'format' = 'json'\n"
                        + ")\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "REFRESH_MODE = FULL\n"
                        + "AS SELECT a, f1, f2 FROM t1,LATERAL TABLE(myFunc(b)) as T(f1, f2)";
        Operation operation = parse(sql);
        assertThat(operation).isInstanceOf(CreateMaterializedTableOperation.class);

        CreateMaterializedTableOperation createOperation =
                (CreateMaterializedTableOperation) operation;

        assertThat(createOperation.getCatalogMaterializedTable().getDefinitionQuery())
                .isEqualTo(
                        "SELECT `t1`.`a`, `T`.`f1`, `T`.`f2`\n"
                                + "FROM `builtin`.`default`.`t1`,\n"
                                + "LATERAL TABLE(`builtin`.`default`.`myFunc`(`b`)) AS `T` (`f1`, `f2`)");
    }

    @Test
    void testContinuousRefreshMode() {
        // test continuous mode derived by specify freshness automatically
        final String sql =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "AS SELECT * FROM t1";
        Operation operation = parse(sql);
        assertThat(operation).isInstanceOf(CreateMaterializedTableOperation.class);

        CreateMaterializedTableOperation op = (CreateMaterializedTableOperation) operation;
        CatalogMaterializedTable materializedTable = op.getCatalogMaterializedTable();
        assertThat(materializedTable).isInstanceOf(ResolvedCatalogMaterializedTable.class);

        assertThat(materializedTable.getLogicalRefreshMode())
                .isEqualTo(CatalogMaterializedTable.LogicalRefreshMode.AUTOMATIC);
        assertThat(materializedTable.getRefreshMode())
                .isEqualTo(CatalogMaterializedTable.RefreshMode.CONTINUOUS);

        // test continuous mode by manual specify
        final String sql2 =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "FRESHNESS = INTERVAL '30' DAY\n"
                        + "REFRESH_MODE = CONTINUOUS\n"
                        + "AS SELECT * FROM t1";
        Operation operation2 = parse(sql2);
        assertThat(operation2).isInstanceOf(CreateMaterializedTableOperation.class);

        CreateMaterializedTableOperation op2 = (CreateMaterializedTableOperation) operation2;
        CatalogMaterializedTable materializedTable2 = op2.getCatalogMaterializedTable();
        assertThat(materializedTable2).isInstanceOf(ResolvedCatalogMaterializedTable.class);

        assertThat(materializedTable2.getLogicalRefreshMode())
                .isEqualTo(CatalogMaterializedTable.LogicalRefreshMode.CONTINUOUS);
        assertThat(materializedTable2.getRefreshMode())
                .isEqualTo(CatalogMaterializedTable.RefreshMode.CONTINUOUS);
    }

    @Test
    void testFullRefreshMode() {
        // test full mode derived by specify freshness automatically
        final String sql =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "FRESHNESS = INTERVAL '1' DAY\n"
                        + "AS SELECT * FROM t1";
        Operation operation = parse(sql);
        assertThat(operation).isInstanceOf(CreateMaterializedTableOperation.class);

        CreateMaterializedTableOperation op = (CreateMaterializedTableOperation) operation;
        CatalogMaterializedTable materializedTable = op.getCatalogMaterializedTable();
        assertThat(materializedTable).isInstanceOf(ResolvedCatalogMaterializedTable.class);

        assertThat(materializedTable.getLogicalRefreshMode())
                .isEqualTo(CatalogMaterializedTable.LogicalRefreshMode.AUTOMATIC);
        assertThat(materializedTable.getRefreshMode())
                .isEqualTo(CatalogMaterializedTable.RefreshMode.FULL);

        // test full mode by manual specify
        final String sql2 =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "REFRESH_MODE = FULL\n"
                        + "AS SELECT * FROM t1";
        Operation operation2 = parse(sql2);
        assertThat(operation2).isInstanceOf(CreateMaterializedTableOperation.class);

        CreateMaterializedTableOperation op2 = (CreateMaterializedTableOperation) operation2;
        CatalogMaterializedTable materializedTable2 = op2.getCatalogMaterializedTable();
        assertThat(materializedTable2).isInstanceOf(ResolvedCatalogMaterializedTable.class);

        assertThat(materializedTable2.getLogicalRefreshMode())
                .isEqualTo(CatalogMaterializedTable.LogicalRefreshMode.FULL);
        assertThat(materializedTable2.getRefreshMode())
                .isEqualTo(CatalogMaterializedTable.RefreshMode.FULL);

        final String sql3 =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "FRESHNESS = INTERVAL '40' MINUTE\n"
                        + "AS SELECT * FROM t1";
        assertThatThrownBy(() -> parse(sql3))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "In full refresh mode, only freshness that are factors of 60 are currently supported when the time unit is MINUTE.");
    }

    @Test
    void testCreateMaterializedTableWithInvalidPrimaryKey() {
        // test unsupported constraint
        final String sql =
                "CREATE MATERIALIZED TABLE mtbl1 (\n"
                        + "   CONSTRAINT ct1 UNIQUE(a) NOT ENFORCED"
                        + ")\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "AS SELECT * FROM t1";

        assertThatThrownBy(() -> parse(sql))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Primary key validation failed: UNIQUE constraint is not supported yet.");

        // test primary key not defined in source table
        final String sql2 =
                "CREATE MATERIALIZED TABLE mtbl1 (\n"
                        + "   CONSTRAINT ct1 PRIMARY KEY(e) NOT ENFORCED"
                        + ")\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "AS SELECT * FROM t1";

        assertThatThrownBy(() -> parse(sql2))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Primary key column 'e' not defined in the query schema. Available columns: ['a', 'b', 'c', 'd'].");

        // test primary key with nullable source column
        final String sql3 =
                "CREATE MATERIALIZED TABLE mtbl1 (\n"
                        + "   CONSTRAINT ct1 PRIMARY KEY(d) NOT ENFORCED"
                        + ")\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "AS SELECT * FROM t1";

        assertThatThrownBy(() -> parse(sql3))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Could not create a PRIMARY KEY with nullable column 'd'.");
    }

    @Test
    void testCreateMaterializedTableWithInvalidPartitionKey() {
        final String sql =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "PARTITIONED BY (a, e)\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "REFRESH_MODE = FULL\n"
                        + "AS SELECT * FROM t1";
        assertThatThrownBy(() -> parse(sql))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Partition column 'e' not defined in the query schema. Available columns: ['a', 'b', 'c', 'd'].");

        final String sql2 =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "PARTITIONED BY (b, c)\n"
                        + "WITH (\n"
                        + " 'partition.fields.ds.date-formatter' = 'yyyy-MM-dd'\n"
                        + ")\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "REFRESH_MODE = FULL\n"
                        + "AS SELECT * FROM t3";
        assertThatThrownBy(() -> parse(sql2))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Column 'ds' referenced by materialized table option 'partition.fields.ds.date-formatter' isn't a partition column. Available partition columns: ['b', 'c'].");

        final String sql3 =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "WITH (\n"
                        + " 'partition.fields.c.date-formatter' = 'yyyy-MM-dd'\n"
                        + ")\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "REFRESH_MODE = FULL\n"
                        + "AS SELECT * FROM t3";
        assertThatThrownBy(() -> parse(sql3))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Column 'c' referenced by materialized table option 'partition.fields.c.date-formatter' isn't a partition column. Available partition columns: [''].");

        final String sql4 =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "PARTITIONED BY (b, c)\n"
                        + "WITH (\n"
                        + " 'partition.fields.c.date-formatter' = 'yyyy-MM-dd'\n"
                        + ")\n"
                        + "FRESHNESS = INTERVAL '30' SECOND\n"
                        + "REFRESH_MODE = FULL\n"
                        + "AS SELECT * FROM t3";
        assertThatThrownBy(() -> parse(sql4))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Materialized table option 'partition.fields.c.date-formatter' only supports referring to char, varchar and string type partition column. Column c type is INT.");
    }

    @Test
    void testCreateMaterializedTableWithInvalidFreshnessType() {
        // test negative freshness value
        final String sql =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "FRESHNESS = INTERVAL -'30' SECOND\n"
                        + "REFRESH_MODE = FULL\n"
                        + "AS SELECT * FROM t1";
        assertThatThrownBy(() -> parse(sql))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Materialized table freshness doesn't support negative value.");

        // test unsupported freshness type
        final String sql2 =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "FRESHNESS = INTERVAL '30' YEAR\n"
                        + "AS SELECT * FROM t1";
        assertThatThrownBy(() -> parse(sql2))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Materialized table freshness only support SECOND, MINUTE, HOUR, DAY as the time unit.");

        // test unsupported freshness type
        final String sql3 =
                "CREATE MATERIALIZED TABLE mtbl1\n"
                        + "FRESHNESS = INTERVAL '30' DAY TO HOUR\n"
                        + "AS SELECT * FROM t1";
        assertThatThrownBy(() -> parse(sql3))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Materialized table freshness only support SECOND, MINUTE, HOUR, DAY as the time unit.");
    }

    @Test
    void testAlterMaterializedTableRefreshOperationWithPartitionSpec() {
        final String sql =
                "ALTER MATERIALIZED TABLE mtbl1 REFRESH PARTITION (ds1 = '1', ds2 = '2')";

        Operation operation = parse(sql);
        assertThat(operation).isInstanceOf(AlterMaterializedTableRefreshOperation.class);

        AlterMaterializedTableRefreshOperation op =
                (AlterMaterializedTableRefreshOperation) operation;
        assertThat(op.getTableIdentifier().toString()).isEqualTo("`builtin`.`default`.`mtbl1`");
        assertThat(op.getPartitionSpec()).isEqualTo(ImmutableMap.of("ds1", "1", "ds2", "2"));
    }

    @Test
    public void testAlterMaterializedTableRefreshOperationWithoutPartitionSpec() {
        final String sql = "ALTER MATERIALIZED TABLE mtbl1 REFRESH";

        Operation operation = parse(sql);
        assertThat(operation).isInstanceOf(AlterMaterializedTableRefreshOperation.class);

        AlterMaterializedTableRefreshOperation op =
                (AlterMaterializedTableRefreshOperation) operation;
        assertThat(op.getTableIdentifier().toString()).isEqualTo("`builtin`.`default`.`mtbl1`");
        assertThat(op.getPartitionSpec()).isEmpty();
    }

    @Test
    void testAlterMaterializedTableSuspend() {
        final String sql = "ALTER MATERIALIZED TABLE mtbl1 SUSPEND";
        Operation operation = parse(sql);
        assertThat(operation).isInstanceOf(AlterMaterializedTableSuspendOperation.class);
    }

    @Test
    void testAlterMaterializedTableResume() {
        final String sql1 = "ALTER MATERIALIZED TABLE mtbl1 RESUME";
        Operation operation = parse(sql1);
        assertThat(operation).isInstanceOf(AlterMaterializedTableResumeOperation.class);
        assertThat(operation.asSummaryString())
                .isEqualTo("ALTER MATERIALIZED TABLE builtin.default.mtbl1 RESUME");

        final String sql2 = "ALTER MATERIALIZED TABLE mtbl1 RESUME WITH ('k1' = 'v1')";
        Operation operation2 = parse(sql2);
        assertThat(operation2).isInstanceOf(AlterMaterializedTableResumeOperation.class);
        assertThat(((AlterMaterializedTableResumeOperation) operation2).getDynamicOptions())
                .containsEntry("k1", "v1");
        assertThat(operation2.asSummaryString())
                .isEqualTo("ALTER MATERIALIZED TABLE builtin.default.mtbl1 RESUME WITH (k1: [v1])");
    }

    @Test
    void testAlterMaterializedTableAsQuery() throws TableNotExistException {
        String sql =
                "ALTER MATERIALIZED TABLE base_mtbl AS SELECT a, b, c, d, d as e, cast('123' as string) as f FROM t3";
        Operation operation = parse(sql);

        assertThat(operation).isInstanceOf(AlterMaterializedTableAsQueryOperation.class);

        AlterMaterializedTableAsQueryOperation op =
                (AlterMaterializedTableAsQueryOperation) operation;
        assertThat(op.getTableChanges())
                .isEqualTo(
                        Arrays.asList(
                                TableChange.add(
                                        Column.physical("e", DataTypes.VARCHAR(Integer.MAX_VALUE))),
                                TableChange.add(
                                        Column.physical("f", DataTypes.VARCHAR(Integer.MAX_VALUE))),
                                TableChange.modifyDefinitionQuery(
                                        "SELECT `t3`.`a`, `t3`.`b`, `t3`.`c`, `t3`.`d`, `t3`.`d` AS `e`, CAST('123' AS STRING) AS `f`\n"
                                                + "FROM `builtin`.`default`.`t3`")));
        assertThat(operation.asSummaryString())
                .isEqualTo(
                        "ALTER MATERIALIZED TABLE builtin.default.base_mtbl AS SELECT `t3`.`a`, `t3`.`b`, `t3`.`c`, `t3`.`d`, `t3`.`d` AS `e`, CAST('123' AS STRING) AS `f`\n"
                                + "FROM `builtin`.`default`.`t3`");

        // new table only difference schema & definition query with old table.
        CatalogMaterializedTable oldTable =
                (CatalogMaterializedTable)
                        catalog.getTable(
                                new ObjectPath(catalogManager.getCurrentDatabase(), "base_mtbl"));
        CatalogMaterializedTable newTable = op.getNewMaterializedTable();

        assertThat(oldTable.getUnresolvedSchema()).isNotEqualTo(newTable.getUnresolvedSchema());
        assertThat(oldTable.getUnresolvedSchema().getPrimaryKey())
                .isEqualTo(newTable.getUnresolvedSchema().getPrimaryKey());
        assertThat(oldTable.getUnresolvedSchema().getWatermarkSpecs())
                .isEqualTo(newTable.getUnresolvedSchema().getWatermarkSpecs());
        assertThat(oldTable.getDefinitionQuery()).isNotEqualTo(newTable.getDefinitionQuery());
        assertThat(oldTable.getDefinitionFreshness()).isEqualTo(newTable.getDefinitionFreshness());
        assertThat(oldTable.getRefreshMode()).isEqualTo(newTable.getRefreshMode());
        assertThat(oldTable.getRefreshStatus()).isEqualTo(newTable.getRefreshStatus());
        assertThat(oldTable.getSerializedRefreshHandler())
                .isEqualTo(newTable.getSerializedRefreshHandler());

        List<Schema.UnresolvedColumn> addedColumn =
                newTable.getUnresolvedSchema().getColumns().stream()
                        .filter(
                                column ->
                                        !oldTable.getUnresolvedSchema()
                                                .getColumns()
                                                .contains(column))
                        .collect(Collectors.toList());
        // added column should be a nullable column.
        assertThat(addedColumn)
                .isEqualTo(
                        Arrays.asList(
                                new Schema.UnresolvedPhysicalColumn(
                                        "e", DataTypes.VARCHAR(Integer.MAX_VALUE)),
                                new Schema.UnresolvedPhysicalColumn(
                                        "f", DataTypes.VARCHAR(Integer.MAX_VALUE))));
    }

    @Test
    void testAlterMaterializedTableAsQueryWithConflictColumnName() {
        String sql5 = "ALTER MATERIALIZED TABLE base_mtbl AS SELECT a, b, c, d, c as a FROM t3";
        AlterMaterializedTableAsQueryOperation sqlAlterMaterializedTableAsQuery =
                (AlterMaterializedTableAsQueryOperation) parse(sql5);

        assertThat(sqlAlterMaterializedTableAsQuery.getTableChanges())
                .isEqualTo(
                        Arrays.asList(
                                TableChange.add(Column.physical("a0", DataTypes.INT())),
                                TableChange.modifyDefinitionQuery(
                                        "SELECT `t3`.`a`, `t3`.`b`, `t3`.`c`, `t3`.`d`, `t3`.`c` AS `a`\n"
                                                + "FROM `builtin`.`default`.`t3`")));
    }

    @Test
    void testAlterMaterializedTableAsQueryWithUnsupportedColumnChange() {
        // 1. delete existing column
        String sql1 = "ALTER MATERIALIZED TABLE base_mtbl AS SELECT a, b FROM t3";
        assertThatThrownBy(() -> parse(sql1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "Failed to modify query because drop column is unsupported. When modifying a query, you can only append new columns at the end of original schema. The original schema has 4 columns, but the newly derived schema from the query has 2 columns.");
        // 2. swap column position
        String sql2 = "ALTER MATERIALIZED TABLE base_mtbl AS SELECT a, b, d, c FROM t3";
        assertThatThrownBy(() -> parse(sql2))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "When modifying the query of a materialized table, currently only support appending columns at the end of original schema, dropping, renaming, and reordering columns are not supported.\n"
                                + "Column mismatch at position 2: Original column is [`c` INT], but new column is [`d` STRING].");
        // 3. change existing column type
        String sql3 =
                "ALTER MATERIALIZED TABLE base_mtbl AS SELECT a, b, c, cast(d as int) as d FROM t3";
        assertThatThrownBy(() -> parse(sql3))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "When modifying the query of a materialized table, currently only support appending columns at the end of original schema, dropping, renaming, and reordering columns are not supported.\n"
                                + "Column mismatch at position 3: Original column is [`d` STRING], but new column is [`d` INT].");
        // 4. change existing column nullability
        String sql4 =
                "ALTER MATERIALIZED TABLE base_mtbl AS SELECT a, b, c, cast('d' as string) as d FROM t3";
        assertThatThrownBy(() -> parse(sql4))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                        "When modifying the query of a materialized table, currently only support appending columns at the end of original schema, dropping, renaming, and reordering columns are not supported.\n"
                                + "Column mismatch at position 3: Original column is [`d` STRING], but new column is [`d` STRING NOT NULL].");
    }

    @Test
    void testAlterAlterMaterializedTableAsQueryWithCatalogTable() {
        // t1 is a CatalogTable not a Materialized Table
        final String sql = "ALTER MATERIALIZED TABLE t1 AS SELECT * FROM t1";
        assertThatThrownBy(() -> parse(sql))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only materialized table support modify definition query.");
    }

    @Test
    void testDropMaterializedTable() {
        final String sql = "DROP MATERIALIZED TABLE mtbl1";
        Operation operation = parse(sql);
        assertThat(operation).isInstanceOf(DropMaterializedTableOperation.class);
        assertThat(((DropMaterializedTableOperation) operation).isIfExists()).isFalse();
        assertThat(operation.asSummaryString())
                .isEqualTo(
                        "DROP MATERIALIZED TABLE: (identifier: [`builtin`.`default`.`mtbl1`], IfExists: [false])");

        final String sql2 = "DROP MATERIALIZED TABLE IF EXISTS mtbl1";
        Operation operation2 = parse(sql2);
        assertThat(operation2).isInstanceOf(DropMaterializedTableOperation.class);
        assertThat(((DropMaterializedTableOperation) operation2).isIfExists()).isTrue();

        assertThat(operation2.asSummaryString())
                .isEqualTo(
                        "DROP MATERIALIZED TABLE: (identifier: [`builtin`.`default`.`mtbl1`], IfExists: [true])");
    }
}
