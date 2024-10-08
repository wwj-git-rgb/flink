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

package org.apache.flink.streaming.graph;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.legacy.DiscardingSink;
import org.apache.flink.streaming.api.functions.source.legacy.ParallelSourceFunction;
import org.apache.flink.streaming.api.graph.StreamGraph;
import org.apache.flink.streaming.api.graph.StreamNode;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the {@link StreamNode} hash assignment during translation from {@link StreamGraph} to
 * {@link JobGraph} instances.
 */
@SuppressWarnings("serial")
class StreamingJobGraphGeneratorNodeHashTest {

    // ------------------------------------------------------------------------
    // Deterministic hash assignment
    // ------------------------------------------------------------------------

    /**
     * Creates the same flow twice and checks that all IDs are the same.
     *
     * <pre>
     * [ (src) -> (map) -> (filter) -> (reduce) -> (map) -> (sink) ]
     *                                                       //
     * [ (src) -> (filter) ] -------------------------------//
     *                                                      /
     * [ (src) -> (filter) ] ------------------------------/
     * </pre>
     */
    @Test
    void testNodeHashIsDeterministic() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);

        DataStream<String> src0 =
                env.addSource(new NoOpSourceFunction(), "src0")
                        .map(new NoOpMapFunction())
                        .filter(new NoOpFilterFunction())
                        .keyBy(new NoOpKeySelector())
                        .reduce(new NoOpReduceFunction())
                        .name("reduce");

        DataStream<String> src1 =
                env.addSource(new NoOpSourceFunction(), "src1").filter(new NoOpFilterFunction());

        DataStream<String> src2 =
                env.addSource(new NoOpSourceFunction(), "src2").filter(new NoOpFilterFunction());

        src0.map(new NoOpMapFunction())
                .union(src1, src2)
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>())
                .name("sink");

        JobGraph jobGraph = env.getStreamGraph().getJobGraph();

        final Map<JobVertexID, String> ids = rememberIds(jobGraph);

        // Do it again and verify
        env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);

        src0 =
                env.addSource(new NoOpSourceFunction(), "src0")
                        .map(new NoOpMapFunction())
                        .filter(new NoOpFilterFunction())
                        .keyBy(new NoOpKeySelector())
                        .reduce(new NoOpReduceFunction())
                        .name("reduce");

        src1 = env.addSource(new NoOpSourceFunction(), "src1").filter(new NoOpFilterFunction());

        src2 = env.addSource(new NoOpSourceFunction(), "src2").filter(new NoOpFilterFunction());

        src0.map(new NoOpMapFunction())
                .union(src1, src2)
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>())
                .name("sink");

        jobGraph = env.getStreamGraph().getJobGraph();

        verifyIdsEqual(jobGraph, ids);
    }

    /**
     * Tests that there are no collisions with two identical sources.
     *
     * <pre>
     * [ (src0) ] --\
     *               +--> [ (sink) ]
     * [ (src1) ] --/
     * </pre>
     */
    @Test
    void testNodeHashIdenticalSources() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);
        env.disableOperatorChaining();

        DataStream<String> src0 = env.addSource(new NoOpSourceFunction());
        DataStream<String> src1 = env.addSource(new NoOpSourceFunction());

        src0.union(src1)
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        JobGraph jobGraph = env.getStreamGraph().getJobGraph();

        List<JobVertex> vertices = jobGraph.getVerticesSortedTopologicallyFromSources();
        assertThat(vertices.get(0).isInputVertex()).isTrue();
        assertThat(vertices.get(1).isInputVertex()).isTrue();

        assertThat(vertices.get(0).getID()).isNotNull();
        assertThat(vertices.get(1).getID()).isNotNull();

        assertThat(vertices.get(0).getID()).isNotEqualTo(vertices.get(1).getID());
    }

    /**
     * Tests that (un)chaining affects the node hash (for sources).
     *
     * <pre>
     * A (chained): [ (src0) -> (map) -> (filter) -> (sink) ]
     * B (unchained): [ (src0) ] -> [ (map) -> (filter) -> (sink) ]
     * </pre>
     *
     * <p>The hashes for the single vertex in A and the source vertex in B need to be different.
     */
    @Test
    void testNodeHashAfterSourceUnchaining() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);

        env.addSource(new NoOpSourceFunction())
                .map(new NoOpMapFunction())
                .filter(new NoOpFilterFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        JobGraph jobGraph = env.getStreamGraph().getJobGraph();

        JobVertexID sourceId = jobGraph.getVerticesSortedTopologicallyFromSources().get(0).getID();

        env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);

        env.addSource(new NoOpSourceFunction())
                .map(new NoOpMapFunction())
                .startNewChain()
                .filter(new NoOpFilterFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        jobGraph = env.getStreamGraph().getJobGraph();

        JobVertexID unchainedSourceId =
                jobGraph.getVerticesSortedTopologicallyFromSources().get(0).getID();

        assertThat(unchainedSourceId).isNotEqualTo(sourceId);
    }

    /**
     * Tests that (un)chaining affects the node hash (for intermediate nodes).
     *
     * <pre>
     * A (chained): [ (src0) -> (map) -> (filter) -> (sink) ]
     * B (unchained): [ (src0) ] -> [ (map) -> (filter) -> (sink) ]
     * </pre>
     *
     * <p>The hashes for the single vertex in A and the source vertex in B need to be different.
     */
    @Test
    void testNodeHashAfterIntermediateUnchaining() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);

        env.addSource(new NoOpSourceFunction())
                .map(new NoOpMapFunction())
                .name("map")
                .startNewChain()
                .filter(new NoOpFilterFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        JobGraph jobGraph = env.getStreamGraph().getJobGraph();

        JobVertex chainedMap = jobGraph.getVerticesSortedTopologicallyFromSources().get(1);
        assertThat(chainedMap.getName()).startsWith("map");
        JobVertexID chainedMapId = chainedMap.getID();

        env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);

        env.addSource(new NoOpSourceFunction())
                .map(new NoOpMapFunction())
                .name("map")
                .startNewChain()
                .filter(new NoOpFilterFunction())
                .startNewChain()
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        jobGraph = env.getStreamGraph().getJobGraph();

        JobVertex unchainedMap = jobGraph.getVerticesSortedTopologicallyFromSources().get(1);
        assertThat(unchainedMap.getName()).isEqualTo("map");
        JobVertexID unchainedMapId = unchainedMap.getID();

        assertThat(unchainedMapId).isNotEqualTo(chainedMapId);
    }

    /**
     * Tests that there are no collisions with two identical intermediate nodes connected to the
     * same predecessor.
     *
     * <pre>
     *             /-> [ (map) ] -> [ (sink) ]
     * [ (src) ] -+
     *             \-> [ (map) ] -> [ (sink) ]
     * </pre>
     */
    @Test
    void testNodeHashIdenticalNodes() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);
        env.disableOperatorChaining();

        DataStream<String> src = env.addSource(new NoOpSourceFunction());

        src.map(new NoOpMapFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        src.map(new NoOpMapFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        JobGraph jobGraph = env.getStreamGraph().getJobGraph();
        Set<JobVertexID> vertexIds = new HashSet<>();
        for (JobVertex vertex : jobGraph.getVertices()) {
            assertThat(vertexIds.add(vertex.getID())).isTrue();
        }
    }

    /** Tests that a changed operator name does not affect the hash. */
    @Test
    void testChangedOperatorName() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.addSource(new NoOpSourceFunction(), "A").map(new NoOpMapFunction());
        JobGraph jobGraph = env.getStreamGraph().getJobGraph();

        JobVertexID expected = jobGraph.getVerticesAsArray()[0].getID();

        env = StreamExecutionEnvironment.createLocalEnvironment();
        env.addSource(new NoOpSourceFunction(), "B").map(new NoOpMapFunction());
        jobGraph = env.getStreamGraph().getJobGraph();

        JobVertexID actual = jobGraph.getVerticesAsArray()[0].getID();

        assertThat(actual).isEqualTo(expected);
    }

    // ------------------------------------------------------------------------
    // Manual hash assignment
    // ------------------------------------------------------------------------

    /**
     * Tests that manual hash assignments are mapped to the same operator ID.
     *
     * <pre>
     *                     /-> [ (map) ] -> [ (sink)@sink0 ]
     * [ (src@source ) ] -+
     *                     \-> [ (map) ] -> [ (sink)@sink1 ]
     * </pre>
     *
     * <pre>
     *                    /-> [ (map) ] -> [ (reduce) ] -> [ (sink)@sink0 ]
     * [ (src)@source ] -+
     *                   \-> [ (map) ] -> [ (reduce) ] -> [ (sink)@sink1 ]
     * </pre>
     */
    @Test
    void testManualHashAssignment() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);
        env.disableOperatorChaining();

        DataStream<String> src =
                env.addSource(new NoOpSourceFunction()).name("source").uid("source");

        src.map(new NoOpMapFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>())
                .name("sink0")
                .uid("sink0");

        src.map(new NoOpMapFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>())
                .name("sink1")
                .uid("sink1");

        JobGraph jobGraph = env.getStreamGraph().getJobGraph();
        Set<JobVertexID> ids = new HashSet<>();
        for (JobVertex vertex : jobGraph.getVertices()) {
            assertThat(ids.add(vertex.getID())).isTrue();
        }

        // Resubmit a slightly different program
        env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);
        env.disableOperatorChaining();

        src =
                env.addSource(new NoOpSourceFunction())
                        // New map function, should be mapped to the source state
                        .map(new NoOpMapFunction())
                        .name("source")
                        .uid("source");

        src.map(new NoOpMapFunction())
                .keyBy(new NoOpKeySelector())
                .reduce(new NoOpReduceFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>())
                .name("sink0")
                .uid("sink0");

        src.map(new NoOpMapFunction())
                .keyBy(new NoOpKeySelector())
                .reduce(new NoOpReduceFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>())
                .name("sink1")
                .uid("sink1");

        JobGraph newJobGraph = env.getStreamGraph().getJobGraph();
        assertThat(newJobGraph.getJobID()).isNotEqualTo(jobGraph.getJobID());

        for (JobVertex vertex : newJobGraph.getVertices()) {
            // Verify that the expected IDs are the same
            if (vertex.getName().endsWith("source")
                    || vertex.getName().endsWith("sink0")
                    || vertex.getName().endsWith("sink1")) {

                assertThat(vertex.getID()).isIn(ids);
            }
        }
    }

    /** Tests that a collision on the manual hash throws an Exception. */
    @Test
    void testManualHashAssignmentCollisionThrowsException() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);
        env.disableOperatorChaining();

        env.addSource(new NoOpSourceFunction())
                .uid("source")
                .map(new NoOpMapFunction())
                .uid("source") // Collision
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        // This call is necessary to generate the job graph
        assertThatThrownBy(() -> env.getStreamGraph().getJobGraph())
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Tests that a manual hash for an intermediate chain node is accepted. */
    @Test
    void testManualHashAssignmentForIntermediateNodeInChain() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);

        env.addSource(new NoOpSourceFunction())
                // Intermediate chained node
                .map(new NoOpMapFunction())
                .uid("map")
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        env.getStreamGraph().getJobGraph();
    }

    /** Tests that a manual hash at the beginning of a chain is accepted. */
    @Test
    void testManualHashAssignmentForStartNodeInInChain() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(4);

        env.addSource(new NoOpSourceFunction())
                .uid("source")
                .map(new NoOpMapFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        env.getStreamGraph().getJobGraph();
    }

    @Test
    void testUserProvidedHashingOnChainSupported() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        env.addSource(new NoOpSourceFunction(), "src")
                .setUidHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .map(new NoOpMapFunction())
                .setUidHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .filter(new NoOpFilterFunction())
                .setUidHash("cccccccccccccccccccccccccccccccc")
                .keyBy(new NoOpKeySelector())
                .reduce(new NoOpReduceFunction())
                .name("reduce")
                .setUidHash("dddddddddddddddddddddddddddddddd");

        env.getStreamGraph().getJobGraph();
    }

    @Test
    void testDisablingAutoUidsFailsStreamGraphCreation() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.getConfig().disableAutoGeneratedUIDs();

        env.addSource(new NoOpSourceFunction())
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>());

        assertThatThrownBy(env::getStreamGraph).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testDisablingAutoUidsAcceptsManuallySetId() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.getConfig().disableAutoGeneratedUIDs();

        env.addSource(new NoOpSourceFunction())
                .uid("uid1")
                .sinkTo(new org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink<>())
                .uid("uid2");

        env.getStreamGraph();
    }

    @Test
    void testDisablingAutoUidsAcceptsManuallySetHash() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.getConfig().disableAutoGeneratedUIDs();

        env.addSource(new NoOpSourceFunction())
                .setUidHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .addSink(new DiscardingSink<>())
                // TODO remove this after sinkFunction is not supported.
                .setUidHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        env.getStreamGraph();
    }

    @Test
    void testDisablingAutoUidsWorksWithKeyBy() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.getConfig().disableAutoGeneratedUIDs();

        env.addSource(new NoOpSourceFunction())
                .setUidHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .keyBy(o -> o)
                .addSink(new DiscardingSink<>())
                // TODO remove this after sinkFunction is not supported.
                .setUidHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        env.getStreamGraph();
    }

    // ------------------------------------------------------------------------

    /** Returns a {@link JobVertexID} to vertex name mapping for the given graph. */
    private Map<JobVertexID, String> rememberIds(JobGraph jobGraph) {
        final Map<JobVertexID, String> ids = new HashMap<>();
        for (JobVertex vertex : jobGraph.getVertices()) {
            ids.put(vertex.getID(), vertex.getName());
        }
        return ids;
    }

    /**
     * Verifies that each {@link JobVertexID} of the {@link JobGraph} is contained in the given map
     * and mapped to the same vertex name.
     */
    private void verifyIdsEqual(JobGraph jobGraph, Map<JobVertexID, String> ids) {
        // Verify same number of vertices
        assertThat(ids).hasSize(jobGraph.getNumberOfVertices());

        // Verify that all IDs->name mappings are identical
        for (JobVertex vertex : jobGraph.getVertices()) {
            String expectedName = ids.get(vertex.getID());
            assertThat(vertex.getName()).isNotNull().isEqualTo(expectedName);
        }
    }

    /**
     * Verifies that no {@link JobVertexID} of the {@link JobGraph} is contained in the given map.
     */
    private void verifyIdsNotEqual(JobGraph jobGraph, Map<JobVertexID, String> ids) {
        // Verify same number of vertices
        assertThat(ids).hasSize(jobGraph.getNumberOfVertices());

        // Verify that all IDs->name mappings are identical
        for (JobVertex vertex : jobGraph.getVertices()) {
            assertThat(ids).doesNotContainKey(vertex.getID());
        }
    }

    // ------------------------------------------------------------------------

    private static class NoOpSourceFunction implements ParallelSourceFunction<String> {

        private static final long serialVersionUID = -5459224792698512636L;

        @Override
        public void run(SourceContext<String> ctx) throws Exception {}

        @Override
        public void cancel() {}
    }

    private static class NoOpMapFunction implements MapFunction<String, String> {

        private static final long serialVersionUID = 6584823409744624276L;

        @Override
        public String map(String value) throws Exception {
            return value;
        }
    }

    private static class NoOpFilterFunction implements FilterFunction<String> {

        private static final long serialVersionUID = 500005424900187476L;

        @Override
        public boolean filter(String value) throws Exception {
            return true;
        }
    }

    private static class NoOpKeySelector implements KeySelector<String, String> {

        private static final long serialVersionUID = -96127515593422991L;

        @Override
        public String getKey(String value) throws Exception {
            return value;
        }
    }

    private static class NoOpReduceFunction implements ReduceFunction<String> {
        private static final long serialVersionUID = -8775747640749256372L;

        @Override
        public String reduce(String value1, String value2) throws Exception {
            return value1;
        }
    }
}
