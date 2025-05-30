/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.state;

import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.runtime.checkpoint.channel.InputChannelInfo;

import java.util.List;

/**
 * {@link StateObject Handle} to an {@link
 * org.apache.flink.runtime.io.network.partition.consumer.InputChannel InputChannel} state.
 */
@Internal
public class InputChannelStateHandle extends AbstractChannelStateHandle<InputChannelInfo>
        implements InputStateHandle {

    private static final long serialVersionUID = 1L;

    public InputChannelStateHandle(
            int subtaskIndex,
            InputChannelInfo info,
            StreamStateHandle delegate,
            StateContentMetaInfo contentMetaInfo) {
        this(subtaskIndex, info, delegate, contentMetaInfo.getOffsets(), contentMetaInfo.getSize());
    }

    @VisibleForTesting
    public InputChannelStateHandle(
            InputChannelInfo info, StreamStateHandle delegate, List<Long> offset) {
        this(0, info, delegate, offset, delegate.getStateSize());
    }

    public InputChannelStateHandle(
            int subtaskIndex,
            InputChannelInfo info,
            StreamStateHandle delegate,
            List<Long> offset,
            long size) {
        super(delegate, offset, subtaskIndex, info, size);
    }
}
