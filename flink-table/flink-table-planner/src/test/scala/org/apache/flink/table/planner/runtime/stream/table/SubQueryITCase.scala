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
package org.apache.flink.table.planner.runtime.stream.table

import org.apache.flink.table.api._
import org.apache.flink.table.api.bridge.scala.{dataStreamConversions, tableConversions}
import org.apache.flink.table.planner.runtime.utils.{StreamingEnvUtil, StreamingWithStateTestBase, TestingRetractSink}
import org.apache.flink.table.planner.runtime.utils.StreamingWithStateTestBase.StateBackendMode
import org.apache.flink.testutils.junit.extensions.parameterized.ParameterizedTestExtension
import org.apache.flink.types.Row

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(Array(classOf[ParameterizedTestExtension]))
class SubQueryITCase(mode: StateBackendMode) extends StreamingWithStateTestBase(mode) {

  @TestTemplate
  def testInUncorrelated(): Unit = {
    val dataA = Seq(
      (1, 1L, "Hello"),
      (2, 2L, "Hello"),
      (3, 3L, "Hello World"),
      (4, 4L, "Hello")
    )

    val dataB = Seq(
      (1, "hello"),
      (2, "co-hello"),
      (4, "hello")
    )

    val tableA = StreamingEnvUtil.fromCollection(env, dataA).toTable(tEnv, 'a, 'b, 'c)

    val tableB = StreamingEnvUtil.fromCollection(env, dataB).toTable(tEnv, 'x, 'y)

    val result = tableA.where('a.in(tableB.select('x)))

    val sink = new TestingRetractSink
    val results = result.toRetractStream[Row]
    results.addSink(sink).setParallelism(1)
    env.execute()

    val expected = Seq(
      "1,1,Hello",
      "2,2,Hello",
      "4,4,Hello"
    )

    assertThat(sink.getRetractResults.sorted).isEqualTo(expected.sorted)
  }

  @TestTemplate
  def testInUncorrelatedWithConditionAndAgg(): Unit = {
    val dataA = Seq(
      (1, 1L, "Hello"),
      (2, 2L, "Hello"),
      (3, 3L, "Hello World"),
      (4, 4L, "Hello")
    )

    val dataB = Seq(
      (1, "hello"),
      (1, "Hanoi"),
      (1, "Hanoi"),
      (2, "Hanoi-1"),
      (2, "Hanoi-1"),
      (-1, "Hanoi-1")
    )

    val tableA = StreamingEnvUtil.fromCollection(env, dataA).toTable(tEnv, 'a, 'b, 'c)

    val tableB = StreamingEnvUtil.fromCollection(env, dataB).toTable(tEnv, 'x, 'y)

    val result = tableA
      .where('a.in(tableB.where('y.like("%Hanoi%")).groupBy('y).select('x.sum)))

    val sink = new TestingRetractSink
    val results = result.toRetractStream[Row]
    results.addSink(sink).setParallelism(1)
    env.execute()

    val expected = Seq(
      "2,2,Hello",
      "3,3,Hello World"
    )

    assertThat(sink.getRetractResults.sorted).isEqualTo(expected.sorted)
  }

  @TestTemplate
  def testInWithMultiUncorrelatedCondition(): Unit = {
    val dataA = Seq(
      (1, 1L, "Hello"),
      (2, 2L, "Hello"),
      (3, 3L, "Hello World"),
      (4, 4L, "Hello")
    )

    val dataB = Seq(
      (1, "hello"),
      (2, "co-hello"),
      (4, "hello")
    )

    val dataC = Seq(
      (1L, "Joker"),
      (1L, "Sanity"),
      (2L, "Cool")
    )

    val tableA = StreamingEnvUtil.fromCollection(env, dataA).toTable(tEnv, 'a, 'b, 'c)

    val tableB = StreamingEnvUtil.fromCollection(env, dataB).toTable(tEnv, 'x, 'y)

    val tableC = StreamingEnvUtil.fromCollection(env, dataC).toTable(tEnv, 'w, 'z)

    val result = tableA
      .where('a.in(tableB.select('x)) && 'b.in(tableC.select('w)))

    val sink = new TestingRetractSink
    val results = result.toRetractStream[Row]
    results.addSink(sink).setParallelism(1)
    env.execute()

    val expected = Seq(
      "1,1,Hello",
      "2,2,Hello"
    )

    assertThat(sink.getRetractResults.sorted).isEqualTo(expected.sorted)
  }

}
