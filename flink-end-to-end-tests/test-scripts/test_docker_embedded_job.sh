#!/usr/bin/env bash
################################################################################
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################

source "$(dirname "$0")"/common.sh
source "$(dirname "$0")"/common_docker.sh

DOCKER_SCRIPTS=${END_TO_END_DIR}/test-scripts/container-scripts
DOCKER_IMAGE_BUILD_RETRIES=3
BUILD_BACKOFF_TIME=5

export FLINK_JOB=org.apache.flink.streaming.examples.wordcount.WordCount
export FLINK_DOCKER_IMAGE_NAME=test_docker_embedded_job
export INPUT_VOLUME=${END_TO_END_DIR}/test-scripts/test-data
export OUTPUT_VOLUME=${TEST_DATA_DIR}/out
export INPUT_PATH=/data/test/input
export OUTPUT_PATH=/data/test/output

INPUT_TYPE=${1:-file}
RESULT_HASH="5a9945c9ab08890b2a0f6b31a4437d57"
case $INPUT_TYPE in
    (file)
        INPUT_ARGS="--input ${INPUT_PATH}/words"
    ;;
    (dummy-fs)
        source "$(dirname "$0")"/common_dummy_fs.sh
        dummy_fs_setup
        INPUT_ARGS="--input dummy://localhost/words --input anotherDummy://localhost/words"
        RESULT_HASH="41d097718a0b00f67fe13d21048d1757"
    ;;
    (*)
        echo "Unknown input type $INPUT_TYPE"
        exit 1
    ;;
esac

export FLINK_JOB_ARGUMENTS="${INPUT_ARGS} --output ${OUTPUT_PATH}/docker_wc_out --execution-mode BATCH"

# user inside the container must be able to create files, this is a workaround in-container permissions
mkdir -p $OUTPUT_VOLUME
chmod 777 $OUTPUT_VOLUME

if ! retry_times $DOCKER_IMAGE_BUILD_RETRIES ${BUILD_BACKOFF_TIME} "build_image ${FLINK_DOCKER_IMAGE_NAME}"; then
    echo "Failed to build docker image. Aborting..."
    exit 1
fi

export USER_LIB=${FLINK_DIR}/examples/streaming
docker compose -f ${DOCKER_SCRIPTS}/docker-compose.test.yml up --force-recreate --abort-on-container-exit --exit-code-from job-cluster &> /dev/null
docker compose -f ${DOCKER_SCRIPTS}/docker-compose.test.yml logs job-cluster > $FLINK_LOG_DIR/jobmanager.log
docker compose -f ${DOCKER_SCRIPTS}/docker-compose.test.yml logs taskmanager > $FLINK_LOG_DIR/taskmanager.log
docker compose -f ${DOCKER_SCRIPTS}/docker-compose.test.yml rm -f

OUTPUT_FILES=$(find "$OUTPUT_VOLUME/docker_wc_out" -type f)
check_result_hash "WordCount" "${OUTPUT_FILES}" "${RESULT_HASH}"
