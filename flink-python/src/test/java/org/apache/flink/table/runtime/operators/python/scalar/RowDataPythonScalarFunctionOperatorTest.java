/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.runtime.operators.python.scalar;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.python.PythonFunctionRunner;
import org.apache.flink.python.env.PythonEnvironmentManager;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.functions.python.PythonFunctionInfo;
import org.apache.flink.table.runtime.typeutils.PythonTypeUtils;
import org.apache.flink.table.runtime.typeutils.RowDataSerializer;
import org.apache.flink.table.runtime.util.RowDataHarnessAssertor;
import org.apache.flink.table.runtime.utils.PassThroughPythonScalarFunctionRunner;
import org.apache.flink.table.runtime.utils.PythonTestUtils;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.RowKind;

import org.apache.beam.sdk.fn.data.FnDataReceiver;

import java.util.Collection;
import java.util.Map;

import static org.apache.flink.table.runtime.util.StreamRecordUtils.row;

/**
 * Tests for {@link RowDataPythonScalarFunctionOperator}.
 */
public class RowDataPythonScalarFunctionOperatorTest
		extends PythonScalarFunctionOperatorTestBase<RowData, RowData, RowData> {

	private final RowDataHarnessAssertor assertor = new RowDataHarnessAssertor(new TypeInformation[]{
		Types.STRING,
		Types.STRING,
		Types.LONG
	});

	@Override
	public AbstractPythonScalarFunctionOperator<RowData, RowData, RowData> getTestOperator(
		Configuration config,
		PythonFunctionInfo[] scalarFunctions,
		RowType inputType,
		RowType outputType,
		int[] udfInputOffsets,
		int[] forwardedFields) {
		return new PassThroughPythonScalarFunctionOperator(
			config,
			scalarFunctions,
			inputType,
			outputType,
			udfInputOffsets,
			forwardedFields
		);
	}

	@Override
	public RowData newRow(boolean accumulateMsg, Object... fields) {
		if (accumulateMsg) {
			return row(fields);
		} else {
			RowData row = row(fields);
			row.setRowKind(RowKind.DELETE);
			return row;
		}
	}

	@Override
	public void assertOutputEquals(String message, Collection<Object> expected, Collection<Object> actual) {
		assertor.assertOutputEquals(message, expected, actual);
	}

	@Override
	public StreamTableEnvironment createTableEnvironment(StreamExecutionEnvironment env) {
		return StreamTableEnvironment.create(
			env,
			EnvironmentSettings.newInstance().inStreamingMode().useBlinkPlanner().build());
	}

	@Override
	public TypeSerializer<RowData> getOutputTypeSerializer(RowType rowType) {
		// If not specified, PojoSerializer will be used which doesn't work well with the Arrow data structure.
		return new RowDataSerializer(new ExecutionConfig(), rowType);
	}

	private static class PassThroughPythonScalarFunctionOperator extends RowDataPythonScalarFunctionOperator {

		PassThroughPythonScalarFunctionOperator(
			Configuration config,
			PythonFunctionInfo[] scalarFunctions,
			RowType inputType,
			RowType outputType,
			int[] udfInputOffsets,
			int[] forwardedFields) {
			super(config, scalarFunctions, inputType, outputType, udfInputOffsets, forwardedFields);
		}

		@Override
		public PythonFunctionRunner<RowData> createPythonFunctionRunner(
				FnDataReceiver<byte[]> resultReceiver,
				PythonEnvironmentManager pythonEnvironmentManager,
				Map<String, String> jobOptions) {
			return new PassThroughPythonScalarFunctionRunner<RowData>(
				getRuntimeContext().getTaskName(),
				resultReceiver,
				scalarFunctions,
				pythonEnvironmentManager,
				userDefinedFunctionInputType,
				userDefinedFunctionOutputType,
				jobOptions,
				PythonTestUtils.createMockFlinkMetricContainer()) {
				@Override
				public TypeSerializer<RowData> getInputTypeSerializer() {
					return (RowDataSerializer) PythonTypeUtils.toBlinkTypeSerializer(getInputType());
				}
			};
		}
	}
}
