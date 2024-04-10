package eu.stratosphere.example.record.kmeans;

import eu.stratosphere.api.Job;
import eu.stratosphere.api.Program;
import eu.stratosphere.api.ProgramDescription;
import eu.stratosphere.api.operators.BulkIteration;
import eu.stratosphere.api.operators.FileDataSink;
import eu.stratosphere.api.operators.FileDataSource;
import eu.stratosphere.api.record.operators.CrossOperator;
import eu.stratosphere.api.record.operators.ReduceOperator;
import eu.stratosphere.example.record.kmeans.udfs.ComputeDistance;
import eu.stratosphere.example.record.kmeans.udfs.FindNearestCenter;
import eu.stratosphere.example.record.kmeans.udfs.PointInFormat;
import eu.stratosphere.example.record.kmeans.udfs.PointOutFormat;
import eu.stratosphere.example.record.kmeans.udfs.RecomputeClusterCenter;
import eu.stratosphere.types.PactInteger;

/**
 *
 */
public class KMeansIterative implements Program, ProgramDescription {
	
	@Override
	public Job createJob(String... args) {
		// parse job parameters
		final int numSubTasks = (args.length > 0 ? Integer.parseInt(args[0]) : 1);
		final String dataPointInput = (args.length > 1 ? args[1] : "");
		final String clusterInput = (args.length > 2 ? args[2] : "");
		final String output = (args.length > 3 ? args[3] : "");
		final int numIterations = (args.length > 4 ? Integer.parseInt(args[4]) : 1);

		// create DataSourceContract for cluster center input
		FileDataSource initialClusterPoints = new FileDataSource(new PointInFormat(), clusterInput, "Centers");
		initialClusterPoints.setDegreeOfParallelism(1);
		
		BulkIteration iteration = new BulkIteration("K-Means Loop");
		iteration.setInput(initialClusterPoints);
		iteration.setMaximumNumberOfIterations(numIterations);
		
		// create DataSourceContract for data point input
		FileDataSource dataPoints = new FileDataSource(new PointInFormat(), dataPointInput, "Data Points");

		// create CrossOperator for distance computation
		CrossOperator computeDistance = CrossOperator.builder(new ComputeDistance())
				.input1(dataPoints)
				.input2(iteration.getPartialSolution())
				.name("Compute Distances")
				.build();

		// create ReduceOperator for finding the nearest cluster centers
		ReduceOperator findNearestClusterCenters = ReduceOperator.builder(new FindNearestCenter(), PactInteger.class, 0)
				.input(computeDistance)
				.name("Find Nearest Centers")
				.build();

		// create ReduceOperator for computing new cluster positions
		ReduceOperator recomputeClusterCenter = ReduceOperator.builder(new RecomputeClusterCenter(), PactInteger.class, 0)
				.input(findNearestClusterCenters)
				.name("Recompute Center Positions")
				.build();
		iteration.setNextPartialSolution(recomputeClusterCenter);

		// create DataSinkContract for writing the new cluster positions
		FileDataSink finalResult = new FileDataSink(new PointOutFormat(), output, iteration, "New Center Positions");

		// return the PACT plan
		Job plan = new Job(finalResult, "Iterative KMeans");
		plan.setDefaultParallelism(numSubTasks);
		return plan;
	}

	@Override
	public String getDescription() {
		return "Parameters: <numSubStasks> <dataPoints> <clusterCenters> <output> <numIterations>";
	}
}
