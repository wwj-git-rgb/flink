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

package org.apache.flink.runtime.state.v2.ttl;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.runtime.state.ttl.AbstractTtlDecorator;
import org.apache.flink.runtime.state.ttl.TtlTimeProvider;
import org.apache.flink.runtime.state.ttl.TtlValue;
import org.apache.flink.util.FlinkRuntimeException;

/**
 * This class wraps aggregating function with TTL logic.
 *
 * @param <IN> The type of the values that are aggregated (input values)
 * @param <ACC> The type of the accumulator (intermediate aggregate state).
 * @param <OUT> The type of the aggregated result
 */
public class TtlAggregateFunction<IN, ACC, OUT>
        extends AbstractTtlDecorator<AggregateFunction<IN, ACC, OUT>>
        implements AggregateFunction<IN, TtlValue<ACC>, OUT> {

    public TtlAggregateFunction(
            AggregateFunction<IN, ACC, OUT> aggFunction,
            StateTtlConfig config,
            TtlTimeProvider timeProvider) {
        super(aggFunction, config, timeProvider);
    }

    @Override
    public TtlValue<ACC> createAccumulator() {
        return wrapWithTs(original.createAccumulator());
    }

    @Override
    public TtlValue<ACC> add(IN value, TtlValue<ACC> accumulator) {
        ACC userAcc = getUnexpired(accumulator);
        userAcc = userAcc == null ? original.createAccumulator() : userAcc;
        return wrapWithTs(original.add(value, userAcc));
    }

    @Override
    public OUT getResult(TtlValue<ACC> accumulator) {
        ACC userAcc;
        try {
            userAcc = getElementWithTtlCheck(accumulator);
        } catch (Exception e) {
            throw new FlinkRuntimeException(
                    "Failed to retrieve original internal aggregating state", e);
        }
        return userAcc == null ? null : original.getResult(userAcc);
    }

    @Override
    public TtlValue<ACC> merge(TtlValue<ACC> a, TtlValue<ACC> b) {
        ACC userA = getUnexpired(a);
        ACC userB = getUnexpired(b);
        if (userA != null && userB != null) {
            return wrapWithTs(original.merge(userA, userB));
        } else if (userA != null) {
            return rewrapWithNewTs(a);
        } else if (userB != null) {
            return rewrapWithNewTs(b);
        } else {
            return null;
        }
    }
}
