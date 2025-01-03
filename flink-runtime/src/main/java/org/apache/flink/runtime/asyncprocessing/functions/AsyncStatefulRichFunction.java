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

package org.apache.flink.runtime.asyncprocessing.functions;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.runtime.asyncprocessing.declare.DeclarationContext;

/** The basic interface of async state function. */
@Internal
public interface AsyncStatefulRichFunction extends Function {

    /**
     * Initialization method for the function. It is called before the actual working methods (like
     * <i>map</i> or <i>join</i>) and thus suitable for one time setup work. For functions that are
     * part of an iteration, this method will be invoked at the beginning of each iteration
     * superstep.
     *
     * <p>The openContext object passed to the function can be used for configuration and
     * initialization. The openContext contains some necessary information that were configured on
     * the function in the program composition.
     *
     * <pre>{@code
     * public class MyFilter extends RichFilterFunction<String> {
     *
     *     private String searchString;
     *
     *     public void open(OpenContext openContext) {
     *         // initialize the value of searchString
     *     }
     *
     *     public boolean filter(String value) {
     *         return value.equals(searchString);
     *     }
     * }
     * }</pre>
     *
     * @param openContext The context containing information about the context in which the function
     *     is opened.
     * @throws Exception Implementations may forward exceptions, which are caught by the runtime.
     *     When the runtime catches an exception, it aborts the task and lets the fail-over logic
     *     decide whether to retry the task execution.
     */
    void open(DeclarationContext openContext) throws Exception;

    /**
     * Tear-down method for the user code. It is called after the last call to the main working
     * methods (e.g. <i>map</i> or <i>join</i>). For functions that are part of an iteration, this
     * method will be invoked after each iteration superstep.
     *
     * <p>This method can be used for clean up work.
     *
     * @throws Exception Implementations may forward exceptions, which are caught by the runtime.
     *     When the runtime catches an exception, it aborts the task and lets the fail-over logic
     *     decide whether to retry the task execution.
     */
    void close() throws Exception;

    // ------------------------------------------------------------------------
    //  Runtime context
    // ------------------------------------------------------------------------

    /**
     * Gets the context that contains information about the UDF's runtime, such as the parallelism
     * of the function, the subtask index of the function, or the name of the task that executes the
     * function.
     *
     * <p>The RuntimeContext also gives access to the {@link
     * org.apache.flink.api.common.accumulators.Accumulator}s and the {@link
     * org.apache.flink.api.common.cache.DistributedCache}.
     *
     * @return The UDF's runtime context.
     */
    RuntimeContext getRuntimeContext();

    /**
     * Sets the function's runtime context. Called by the framework when creating a parallel
     * instance of the function.
     *
     * @param t The runtime context.
     */
    @Internal
    void setRuntimeContext(RuntimeContext t);
}
