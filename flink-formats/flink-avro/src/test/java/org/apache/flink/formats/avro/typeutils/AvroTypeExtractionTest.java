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

package org.apache.flink.formats.avro.typeutils;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SerializerConfigImpl;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.avro.AvroInputFormat;
import org.apache.flink.formats.avro.AvroRecordInputFormatTest;
import org.apache.flink.formats.avro.generated.User;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.util.TestStreamEnvironment;
import org.apache.flink.test.junit5.InjectMiniCluster;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.apache.flink.test.util.TestBaseUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Tests for the {@link AvroInputFormat} reading Pojos. */
class AvroTypeExtractionTest {

    private static final int PARALLELISM = 4;

    @RegisterExtension
    private static final MiniClusterExtension MINI_CLUSTER_RESOURCE =
            new MiniClusterExtension(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberTaskManagers(1)
                            .setNumberSlotsPerTaskManager(PARALLELISM)
                            .build());

    private File inFile;
    private String resultPath;
    private String expected;

    @BeforeEach
    public void before(@TempDir java.nio.file.Path tmpDir) throws Exception {
        resultPath = tmpDir.resolve("out").toUri().toString();
        inFile = tmpDir.resolve("in.avro").toFile();
        AvroRecordInputFormatTest.writeTestFile(inFile);
    }

    @AfterEach
    public void after() throws Exception {
        TestBaseUtils.compareResultsByLinesInMemory(expected, resultPath);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSimpleAvroRead(boolean useMiniCluster, @InjectMiniCluster MiniCluster miniCluster)
            throws Exception {
        final StreamExecutionEnvironment env = getExecutionEnvironment(useMiniCluster, miniCluster);
        Path in = new Path(inFile.getAbsoluteFile().toURI());

        AvroInputFormat<User> users = new AvroInputFormat<>(in, User.class);
        DataStream<User> usersDS = env.createInput(users).map((value) -> value);

        usersDS.sinkTo(
                FileSink.forRowFormat(new Path(resultPath), new SimpleStringEncoder<User>())
                        .build());

        env.execute("Simple Avro read job");

        expected =
                "{\"name\": \"Alyssa\", \"favorite_number\": 256, \"favorite_color\": null, "
                        + "\"type_long_test\": null, \"type_double_test\": 123.45, \"type_null_test\": null, "
                        + "\"type_bool_test\": true, \"type_array_string\": [\"ELEMENT 1\", \"ELEMENT 2\"], "
                        + "\"type_array_boolean\": [true, false], \"type_nullable_array\": null, \"type_enum\": \"GREEN\", "
                        + "\"type_map\": {\"KEY 2\": 17554, \"KEY 1\": 8546456}, \"type_fixed\": null, \"type_union\": null, "
                        + "\"type_nested\": {\"num\": 239, \"street\": \"Baker Street\", \"city\": \"London\", "
                        + "\"state\": \"London\", \"zip\": \"NW1 6XE\"}, "
                        + "\"type_bytes\": \"\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\", "
                        + "\"type_date\": \"2014-03-01\", \"type_time_millis\": \"12:12:12\", \"type_time_micros\": \"00:00:00.123456\", "
                        + "\"type_timestamp_millis\": \"2014-03-01T12:12:12.321Z\", "
                        + "\"type_timestamp_micros\": \"1970-01-01T00:00:00.123456Z\", "
                        + "\"type_decimal_bytes\": \"\\u0007Ð\", \"type_decimal_fixed\": [7, -48]}\n"
                        + "{\"name\": \"Charlie\", \"favorite_number\": null, "
                        + "\"favorite_color\": \"blue\", \"type_long_test\": 1337, \"type_double_test\": 1.337, "
                        + "\"type_null_test\": null, \"type_bool_test\": false, \"type_array_string\": [], "
                        + "\"type_array_boolean\": [], \"type_nullable_array\": null, \"type_enum\": \"RED\", \"type_map\": {}, "
                        + "\"type_fixed\": null, \"type_union\": null, "
                        + "\"type_nested\": {\"num\": 239, \"street\": \"Baker Street\", \"city\": \"London\", \"state\": \"London\", "
                        + "\"zip\": \"NW1 6XE\"}, "
                        + "\"type_bytes\": \"\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\", "
                        + "\"type_date\": \"2014-03-01\", \"type_time_millis\": \"12:12:12\", \"type_time_micros\": \"00:00:00.123456\", "
                        + "\"type_timestamp_millis\": \"2014-03-01T12:12:12.321Z\", "
                        + "\"type_timestamp_micros\": \"1970-01-01T00:00:00.123456Z\", "
                        + "\"type_decimal_bytes\": \"\\u0007Ð\", "
                        + "\"type_decimal_fixed\": [7, -48]}\n";
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSerializeWithAvro(boolean useMiniCluster, @InjectMiniCluster MiniCluster miniCluster)
            throws Exception {
        final StreamExecutionEnvironment env = getExecutionEnvironment(useMiniCluster, miniCluster);
        ((SerializerConfigImpl) env.getConfig().getSerializerConfig()).setForceKryoAvro(true);
        Path in = new Path(inFile.getAbsoluteFile().toURI());

        AvroInputFormat<User> users = new AvroInputFormat<>(in, User.class);
        DataStream<User> usersDS =
                env.createInput(users)
                        .map(
                                (MapFunction<User, User>)
                                        value -> {
                                            Map<CharSequence, Long> ab = new HashMap<>(1);
                                            ab.put("hehe", 12L);
                                            value.setTypeMap(ab);
                                            return value;
                                        });

        usersDS.sinkTo(
                FileSink.forRowFormat(new Path(resultPath), new SimpleStringEncoder<User>())
                        .build());

        env.execute("Simple Avro read job");

        expected =
                "{\"name\": \"Alyssa\", \"favorite_number\": 256, \"favorite_color\": null,"
                        + " \"type_long_test\": null, \"type_double_test\": 123.45, \"type_null_test\": null,"
                        + " \"type_bool_test\": true, \"type_array_string\": [\"ELEMENT 1\", \"ELEMENT 2\"],"
                        + " \"type_array_boolean\": [true, false], \"type_nullable_array\": null, \"type_enum\": \"GREEN\","
                        + " \"type_map\": {\"hehe\": 12}, \"type_fixed\": null, \"type_union\": null,"
                        + " \"type_nested\": {\"num\": 239, \"street\": \"Baker Street\", \"city\": \"London\","
                        + " \"state\": \"London\", \"zip\": \"NW1 6XE\"},"
                        + " \"type_bytes\": \"\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\", "
                        + "\"type_date\": \"2014-03-01\", \"type_time_millis\": \"12:12:12\", \"type_time_micros\": \"00:00:00.123456\", "
                        + "\"type_timestamp_millis\": \"2014-03-01T12:12:12.321Z\", "
                        + "\"type_timestamp_micros\": \"1970-01-01T00:00:00.123456Z\", "
                        + "\"type_decimal_bytes\": \"\\u0007Ð\", \"type_decimal_fixed\": [7, -48]}\n"
                        + "{\"name\": \"Charlie\", \"favorite_number\": null, "
                        + "\"favorite_color\": \"blue\", \"type_long_test\": 1337, \"type_double_test\": 1.337, "
                        + "\"type_null_test\": null, \"type_bool_test\": false, \"type_array_string\": [], "
                        + "\"type_array_boolean\": [], \"type_nullable_array\": null, \"type_enum\": \"RED\", "
                        + "\"type_map\": {\"hehe\": 12}, \"type_fixed\": null, \"type_union\": null, "
                        + "\"type_nested\": {\"num\": 239, \"street\": \"Baker Street\", \"city\": \"London\", \"state\": \"London\", "
                        + "\"zip\": \"NW1 6XE\"}, "
                        + "\"type_bytes\": \"\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\\u0000\", "
                        + "\"type_date\": \"2014-03-01\", \"type_time_millis\": \"12:12:12\", \"type_time_micros\": \"00:00:00.123456\", "
                        + "\"type_timestamp_millis\": \"2014-03-01T12:12:12.321Z\", "
                        + "\"type_timestamp_micros\": \"1970-01-01T00:00:00.123456Z\", "
                        + "\"type_decimal_bytes\": \"\\u0007Ð\", \"type_decimal_fixed\": [7, -48]}\n";
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testKeySelection(boolean useMiniCluster, @InjectMiniCluster MiniCluster miniCluster)
            throws Exception {
        final StreamExecutionEnvironment env = getExecutionEnvironment(useMiniCluster, miniCluster);
        env.getConfig().enableObjectReuse();
        Path in = new Path(inFile.getAbsoluteFile().toURI());

        AvroInputFormat<User> users = new AvroInputFormat<>(in, User.class);
        DataStream<User> usersDS = env.createInput(users);

        DataStream<Tuple2<String, Integer>> res =
                usersDS.keyBy(User::getName)
                        .map(
                                (MapFunction<User, Tuple2<String, Integer>>)
                                        value -> new Tuple2<>(value.getName().toString(), 1))
                        .returns(Types.TUPLE(Types.STRING, Types.INT));
        res.sinkTo(
                FileSink.forRowFormat(
                                new Path(resultPath),
                                new SimpleStringEncoder<Tuple2<String, Integer>>())
                        .build());
        env.execute("Avro Key selection");

        expected = "(Alyssa,1)\n(Charlie,1)\n";
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWithAvroGenericSer(boolean useMiniCluster, @InjectMiniCluster MiniCluster miniCluster)
            throws Exception {
        final StreamExecutionEnvironment env = getExecutionEnvironment(useMiniCluster, miniCluster);
        ((SerializerConfigImpl) env.getConfig().getSerializerConfig()).setForceKryoAvro(true);
        Path in = new Path(inFile.getAbsoluteFile().toURI());

        AvroInputFormat<User> users = new AvroInputFormat<>(in, User.class);
        DataStreamSource<User> usersDS = env.createInput(users);

        DataStream<Tuple2<String, Integer>> res =
                usersDS.keyBy(User::getName)
                        .map(
                                (MapFunction<User, Tuple2<String, Integer>>)
                                        value -> new Tuple2<>(value.getName().toString(), 1))
                        .returns(Types.TUPLE(Types.STRING, Types.INT));

        res.sinkTo(
                FileSink.forRowFormat(
                                new Path(resultPath),
                                new SimpleStringEncoder<Tuple2<String, Integer>>())
                        .build());
        env.execute("Avro Key selection");

        expected = "(Charlie,1)\n(Alyssa,1)\n";
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWithKryoGenericSer(boolean useMiniCluster, @InjectMiniCluster MiniCluster miniCluster)
            throws Exception {
        final StreamExecutionEnvironment env = getExecutionEnvironment(useMiniCluster, miniCluster);
        ((SerializerConfigImpl) env.getConfig().getSerializerConfig()).setForceKryoAvro(true);
        Path in = new Path(inFile.getAbsoluteFile().toURI());

        AvroInputFormat<User> users = new AvroInputFormat<>(in, User.class);
        DataStreamSource<User> usersDS = env.createInput(users);

        DataStream<Tuple2<String, Integer>> res =
                usersDS.keyBy(User::getName)
                        .map(
                                (MapFunction<User, Tuple2<String, Integer>>)
                                        value -> new Tuple2<>(value.getName().toString(), 1))
                        .returns(Types.TUPLE(Types.STRING, Types.INT));

        res.sinkTo(
                FileSink.forRowFormat(
                                new Path(resultPath),
                                new SimpleStringEncoder<Tuple2<String, Integer>>())
                        .build());
        env.execute("Avro Key selection");

        expected = "(Charlie,1)\n(Alyssa,1)\n";
    }

    private static Stream<Arguments> testField() {
        return Arrays.stream(new Boolean[] {true, false})
                .flatMap(
                        env ->
                                Stream.of(
                                        Arguments.of(env, "name"),
                                        Arguments.of(env, "type_enum"),
                                        Arguments.of(env, "type_double_test")));
    }

    private static StreamExecutionEnvironment getExecutionEnvironment(
            boolean useMiniCluster, MiniCluster miniCluster) {
        return useMiniCluster
                ? new TestStreamEnvironment(miniCluster, PARALLELISM)
                : StreamExecutionEnvironment.getExecutionEnvironment();
    }
}
