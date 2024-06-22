/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.pact.test.contracts;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

import junit.framework.Assert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.nephele.jobgraph.JobGraph;
import eu.stratosphere.pact.common.contract.DataSinkContract;
import eu.stratosphere.pact.common.contract.DataSourceContract;
import eu.stratosphere.pact.common.contract.MatchContract;
import eu.stratosphere.pact.common.io.TextInputFormat;
import eu.stratosphere.pact.common.io.TextOutputFormat;
import eu.stratosphere.pact.common.plan.Plan;
import eu.stratosphere.pact.common.stub.Collector;
import eu.stratosphere.pact.common.stub.MatchStub;
import eu.stratosphere.pact.common.type.KeyValuePair;
import eu.stratosphere.pact.common.type.base.PactInteger;
import eu.stratosphere.pact.common.type.base.PactString;
import eu.stratosphere.pact.compiler.PactCompiler;
import eu.stratosphere.pact.compiler.jobgen.JobGraphGenerator;
import eu.stratosphere.pact.compiler.plan.OptimizedPlan;
import eu.stratosphere.pact.test.util.TestBase;

/**
 * @author Fabian Hueske
 */
@RunWith(Parameterized.class)
public class MatchTest extends TestBase
/*
 * TODO: - Allow multiple data sinks - Rename Merge to Match
 */

{
	public MatchTest(String clusterConfig, Configuration testConfig) {
		super(testConfig, clusterConfig);
	}

	private static final String MATCH_LEFT_IN_1 = "1 1\n2 2\n3 3\n4 4\n";

	private static final String MATCH_LEFT_IN_2 = "1 2\n2 3\n3 4\n4 5\n";

	private static final String MATCH_LEFT_IN_3 = "1 3\n2 4\n3 5\n4 6\n";

	private static final String MATCH_LEFT_IN_4 = "1 4\n2 5\n3 6\n4 7\n";

	private static final String MATCH_RIGHT_IN_1 = "1 1\n2 2\n3 3\n5 1\n";

	private static final String MATCH_RIGHT_IN_2 = "1 1\n2 2\n3 3\n6 1\n";

	private static final String MATCH_RIGHT_IN_3 = "1 1\n2 2\n2 2\n7 1\n";

	private static final String MATCH_RIGHT_IN_4 = "1 1\n2 2\n2 2\n8 1\n";

	private static final String MATCH_RESULT = "1 0\n1 0\n1 0\n1 0\n1 1\n1 1\n1 1\n1 1\n1 2\n1 2\n1 2\n1 2\n1 3\n1 3\n1 3\n1 3\n"
		+ "3 0\n3 0\n3 1\n3 1\n3 2\n3 2\n3 3\n3 3\n"
		+ "2 0\n2 1\n2 2\n2 3\n2 0\n2 1\n2 2\n2 3\n2 0\n2 1\n2 2\n2 3\n2 0\n2 1\n2 2\n2 3\n2 0\n2 1\n2 2\n2 3\n2 0\n2 1\n2 2\n2 3\n";

	@Override
	protected void preSubmit() throws Exception {
		getHDFSProvider().createDir(getHDFSProvider().getHdfsHome() + "/match_left");

		getHDFSProvider().writeFileToHDFS(getHDFSProvider().getHdfsHome() + "/match_left/matchTest_1.txt",
			MATCH_LEFT_IN_1);
		getHDFSProvider().writeFileToHDFS(getHDFSProvider().getHdfsHome() + "/match_left/matchTest_2.txt",
			MATCH_LEFT_IN_2);
		getHDFSProvider().writeFileToHDFS(getHDFSProvider().getHdfsHome() + "/match_left/matchTest_3.txt",
			MATCH_LEFT_IN_3);
		getHDFSProvider().writeFileToHDFS(getHDFSProvider().getHdfsHome() + "/match_left/matchTest_4.txt",
			MATCH_LEFT_IN_4);

		getHDFSProvider().createDir(getHDFSProvider().getHdfsHome() + "/match_right");

		getHDFSProvider().writeFileToHDFS(getHDFSProvider().getHdfsHome() + "/match_right/matchTest_1.txt",
			MATCH_RIGHT_IN_1);
		getHDFSProvider().writeFileToHDFS(getHDFSProvider().getHdfsHome() + "/match_right/matchTest_2.txt",
			MATCH_RIGHT_IN_2);
		getHDFSProvider().writeFileToHDFS(getHDFSProvider().getHdfsHome() + "/match_right/matchTest_3.txt",
			MATCH_RIGHT_IN_3);
		getHDFSProvider().writeFileToHDFS(getHDFSProvider().getHdfsHome() + "/match_right/matchTest_4.txt",
			MATCH_RIGHT_IN_4);

	}

	public static class MatchTestInFormat extends TextInputFormat<PactString, PactString> {

		private static final Log LOG = LogFactory.getLog(MatchTestInFormat.class);

		@Override
		public boolean readLine(KeyValuePair<PactString, PactString> pair, byte[] line) {

			pair.setKey(new PactString(new String((char) line[0] + "")));
			pair.setValue(new PactString(new String((char) line[2] + "")));

			LOG.info("Read in: [" + pair.getKey() + "," + pair.getValue() + "]");
			return true;
		}
	}

	public static class MatchTestOutFormat extends TextOutputFormat<PactString, PactInteger> {

		private static final Log LOG = LogFactory.getLog(MatchTestOutFormat.class);

		@Override
		public byte[] writeLine(KeyValuePair<PactString, PactInteger> pair) {

			LOG.info("Writing out: [" + pair.getKey() + "," + pair.getValue() + "]");

			return (pair.getKey().toString() + " " + pair.getValue().toString() + "\n").getBytes();
		}
	}

	public static class TestMatcher extends MatchStub<PactString, PactString, PactString, PactString, PactInteger> {

		private static final Log LOG = LogFactory.getLog(TestMatcher.class);

		@Override
		public void match(PactString key, PactString value1, PactString value2, Collector<PactString, PactInteger> out) {
			out.collect(key, new PactInteger(Integer.parseInt(value1.toString()) - Integer.parseInt(value2.toString())));

			LOG.info("Processed: [" + key + "," + value1 + "] + [" + key + "," + value2 + "]");
		}

	}

	@Override
	protected JobGraph getJobGraph() throws Exception {

		DataSourceContract<PactString, PactString> input_left = new DataSourceContract<PactString, PactString>(
			MatchTestInFormat.class, getHDFSProvider().getHdfsHome() + "/match_left");
		input_left.setFormatParameter("delimiter", "\n");
		input_left.setDegreeOfParallelism(config.getInteger("MatchTest#NoSubtasks", 1));

		DataSourceContract<PactString, PactString> input_right = new DataSourceContract<PactString, PactString>(
			MatchTestInFormat.class, getHDFSProvider().getHdfsHome() + "/match_right");
		input_right.setFormatParameter("delimiter", "\n");
		input_right.setDegreeOfParallelism(config.getInteger("MatchTest#NoSubtasks", 1));

		MatchContract<PactString, PactString, PactString, PactString, PactInteger> testMatcher = new MatchContract<PactString, PactString, PactString, PactString, PactInteger>(
			TestMatcher.class);
		testMatcher.setDegreeOfParallelism(config.getInteger("MatchTest#NoSubtasks", 1));
		testMatcher.getStubParameters().setString(PactCompiler.HINT_LOCAL_STRATEGY,
			config.getString("MatchTest#LocalStrategy", ""));
		if (config.getString("MatchTest#ShipStrategy", "").equals("BROADCAST_FIRST")) {
			testMatcher.getStubParameters().setString(PactCompiler.HINT_SHIP_STRATEGY_FIRST_INPUT,
				PactCompiler.HINT_SHIP_STRATEGY_BROADCAST);
			testMatcher.getStubParameters().setString(PactCompiler.HINT_SHIP_STRATEGY_SECOND_INPUT,
				PactCompiler.HINT_SHIP_STRATEGY_FORWARD);
		} else if (config.getString("MatchTest#ShipStrategy", "").equals(
			"BROADCAST_SECOND")) {
			testMatcher.getStubParameters().setString(PactCompiler.HINT_SHIP_STRATEGY_FIRST_INPUT,
				PactCompiler.HINT_SHIP_STRATEGY_FORWARD);
			testMatcher.getStubParameters().setString(PactCompiler.HINT_SHIP_STRATEGY_SECOND_INPUT,
				PactCompiler.HINT_SHIP_STRATEGY_BROADCAST);
		} else {
			testMatcher.getStubParameters().setString(PactCompiler.HINT_SHIP_STRATEGY,
				config.getString("MatchTest#ShipStrategy", ""));
		}

		DataSinkContract<PactString, PactInteger> output = new DataSinkContract<PactString, PactInteger>(
			MatchTestOutFormat.class, getHDFSProvider().getHdfsHome() + "/result.txt");
		output.setDegreeOfParallelism(1);

		output.setInput(testMatcher);
		testMatcher.setFirstInput(input_left);
		testMatcher.setSecondInput(input_right);

		Plan plan = new Plan(output);

		PactCompiler pc = new PactCompiler();
		OptimizedPlan op = pc.compile(plan);

		JobGraphGenerator jgg = new JobGraphGenerator();
		return jgg.compileJobGraph(op);

	}

	@Override
	protected void postSubmit() throws Exception {
		// read result
		InputStream is = getHDFSProvider().getHdfsInputStream(getHDFSProvider().getHdfsHome() + "/result.txt");
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line = reader.readLine();
		Assert.assertNotNull("No output computed", line);

		// collect out lines
		PriorityQueue<String> computedResult = new PriorityQueue<String>();
		while (line != null) {
			computedResult.add(line);
			line = reader.readLine();
		}
		reader.close();

		PriorityQueue<String> expectedResult = new PriorityQueue<String>();
		StringTokenizer st = new StringTokenizer(MATCH_RESULT, "\n");
		while (st.hasMoreElements()) {
			expectedResult.add(st.nextToken());
		}

		// print expected and computed results
		System.out.println("Expected: " + expectedResult);
		System.out.println("Computed: " + computedResult);

		Assert.assertEquals("Computed and expected results have different size", expectedResult.size(), computedResult
			.size());

		while (!expectedResult.isEmpty()) {
			String expectedLine = expectedResult.poll();
			String computedLine = computedResult.poll();
			System.out.println("expLine: <" + expectedLine + ">\t\t: compLine: <" + computedLine + ">");
			Assert.assertEquals("Computed and expected lines differ", expectedLine, computedLine);
		}

		getHDFSProvider().delete(getHDFSProvider().getHdfsHome() + "/result.txt", false);
		getHDFSProvider().delete(getHDFSProvider().getHdfsHome() + "/match_left", true);
		getHDFSProvider().delete(getHDFSProvider().getHdfsHome() + "/match_right", true);

	}

	@Parameters
	public static Collection<Object[]> getConfigurations() throws FileNotFoundException, IOException {

		LinkedList<Configuration> tConfigs = new LinkedList<Configuration>();

		String[] localStrategies = { PactCompiler.HINT_LOCAL_STRATEGY_SORT,
			PactCompiler.HINT_LOCAL_STRATEGY_HASH_BUILD_FIRST, PactCompiler.HINT_LOCAL_STRATEGY_HASH_BUILD_SECOND };

		String[] shipStrategies = { PactCompiler.HINT_SHIP_STRATEGY_REPARTITION, "BROADCAST_FIRST", "BROADCAST_SECOND" };

		for (String localStrategy : localStrategies) {
			for (String shipStrategy : shipStrategies) {

				Configuration config = new Configuration();
				config.setString("MatchTest#LocalStrategy", localStrategy);
				config.setString("MatchTest#ShipStrategy", shipStrategy);
				config.setInteger("MatchTest#NoSubtasks", 4);

				tConfigs.add(config);
			}
		}

		return toParameterList(MatchTest.class, tConfigs);
	}

	@Override
	protected String getJarFilePath() {
		return "/home/fhueske/distTests.jar";
	}

}
