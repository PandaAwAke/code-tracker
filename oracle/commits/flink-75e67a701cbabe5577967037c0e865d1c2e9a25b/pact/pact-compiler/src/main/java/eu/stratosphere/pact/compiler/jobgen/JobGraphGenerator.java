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

package eu.stratosphere.pact.compiler.jobgen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.stratosphere.nephele.fs.Path;
import eu.stratosphere.nephele.io.channels.ChannelType;
import eu.stratosphere.nephele.io.compression.CompressionLevel;
import eu.stratosphere.nephele.jobgraph.AbstractJobVertex;
import eu.stratosphere.nephele.jobgraph.JobFileInputVertex;
import eu.stratosphere.nephele.jobgraph.JobFileOutputVertex;
import eu.stratosphere.nephele.jobgraph.JobGraph;
import eu.stratosphere.nephele.jobgraph.JobGraphDefinitionException;
import eu.stratosphere.nephele.jobgraph.JobTaskVertex;
import eu.stratosphere.pact.common.contract.DataSinkContract;
import eu.stratosphere.pact.common.contract.DataSourceContract;
import eu.stratosphere.pact.common.plan.Visitor;
import eu.stratosphere.pact.common.stub.Stub;
import eu.stratosphere.pact.common.type.Key;
import eu.stratosphere.pact.common.type.Value;
import eu.stratosphere.pact.compiler.CompilerException;
import eu.stratosphere.pact.compiler.plan.CombinerNode;
import eu.stratosphere.pact.compiler.plan.DataSourceNode;
import eu.stratosphere.pact.compiler.plan.OptimizedPlan;
import eu.stratosphere.pact.compiler.plan.OptimizerNode;
import eu.stratosphere.pact.compiler.plan.PactConnection;
import eu.stratosphere.pact.compiler.plan.ReduceNode;
import eu.stratosphere.pact.runtime.task.CoGroupTask;
import eu.stratosphere.pact.runtime.task.CombineTask;
import eu.stratosphere.pact.runtime.task.CrossTask;
import eu.stratosphere.pact.runtime.task.DataSinkTask;
import eu.stratosphere.pact.runtime.task.DataSourceTask;
import eu.stratosphere.pact.runtime.task.MapTask;
import eu.stratosphere.pact.runtime.task.MatchTask;
import eu.stratosphere.pact.runtime.task.ReduceTask;
import eu.stratosphere.pact.runtime.task.TempTask;
import eu.stratosphere.pact.runtime.task.util.TaskConfig;
import eu.stratosphere.pact.runtime.task.util.OutputEmitter.ShipStrategy;
import eu.stratosphere.pact.runtime.task.util.TaskConfig.LocalStrategy;

/**
 * This component translates an optimized PACT plan into a nephele schedule. The
 * translation is a one to one mapping, setting and configuring all nephele
 * parameters. The parameters are set with with values that are either indicated
 * in the PACT program directly, are set by the PACT compiler, or with default values.
 * 
 * @author Fabian Hueske (fabian.hueske@tu-berlin.de)
 * @author Stephan Ewen (stephan.ewen@tu-berlin.de)
 */
public class JobGraphGenerator implements Visitor<OptimizerNode> {
	
	public static final Log LOG = LogFactory.getLog(JobGraphGenerator.class);
	
	private static final int DEFAUTL_MERGE_FACTOR = 64; // the number of streams to merge at once

	private static final int MIN_IO_BUFFER_SIZE = 1;

	private static final int MAX_IO_BUFFER_SIZE = 16;

	private static final int MIN_SORT_HEAP = 4;

	private static final int MAX_SORT_HEAP_BUFFER_SIZE = 2047;

	// ------------------------------------------------------------------------

	private JobGraph jobGraph; // the job that is currently built

	private Map<OptimizerNode, AbstractJobVertex> vertices; // a map from optimizer nodes to nephele vertices

	private AbstractJobVertex maxDegreeVertex; // the vertex with the highest degree of parallelism

	// ------------------------------------------------------------------------

	/**
	 * Creates a new job graph generator that uses the default values for its resource configuration.
	 */
	public JobGraphGenerator() {
	}

	/**
	 * Translates a {@link eu.stratosphere.pact.compiler.plan.OptimizedPlan} into a
	 * {@link eu.stratosphere.nephele.jobgraph.JobGraph}.
	 * This is an 1-to-1 mapping. No optimization whatsoever is applied.
	 * 
	 * @param pactPlan
	 *        Optimized PACT plan that is translated into a JobGraph.
	 * @return JobGraph generated from PACT plan.
	 */
	public JobGraph compileJobGraph(OptimizedPlan pactPlan) {
		this.jobGraph = new JobGraph(pactPlan.getJobName());
		this.vertices = new HashMap<OptimizerNode, AbstractJobVertex>();
		this.maxDegreeVertex = null;

		// generate Nephele job graph
		pactPlan.accept(this);

		// now that all have been created, make sure that all share their instances with the one
		// with the highest degree of parallelism
		if (pactPlan.getInstanceTypeName() != null) {
			maxDegreeVertex.setInstanceType(pactPlan.getInstanceTypeName());
		} else {
			LOG.warn("No instance type assigned to Nephele JobVertex.");
		}
		for (AbstractJobVertex vertex : this.vertices.values()) {
			if (vertex == maxDegreeVertex) {
				continue;
			}
			vertex.setVertexToShareInstancesWith(maxDegreeVertex);
		}
		

		JobGraph graph = this.jobGraph;

		// release all references again
		this.maxDegreeVertex = null;
		this.vertices = null;
		this.jobGraph = null;

		// return job graph
		return graph;
	}

	/**
	 * This methods implements the pre-visiting during a depth-first traversal. It create the job vertex and
	 * sets local strategy.
	 * 
	 * @param node
	 *        The node that is currently processed.
	 * @return True, if the visitor should descend to the node's children, false if not.
	 * @see eu.stratosphere.pact.common.plan.Visitor#preVisit(eu.stratosphere.pact.common.plan.Visitable)
	 */
	@Override
	public boolean preVisit(OptimizerNode node) {
		// check if we have visited this node before. in non-tree graphs, this happens
		if (vertices.containsKey(node)) {
			return false;
		}

		// the vertex to be created for the current node
		AbstractJobVertex vertex = null;

		try {
			switch (node.getPactType()) {
			case Map:
				vertex = generateMapVertex(node);
				break;
			case Reduce:
				if (node instanceof ReduceNode) {
					vertex = generateReduceVertex((ReduceNode) node);
				} else if (node instanceof CombinerNode) {
					vertex = generateCombineVertex((CombinerNode) node);
				} else {
					throw new CompilerException("Wrong node type for PACT type 'Reduce': " + node.getClass().getName());
				}
				break;
			case Match:
				vertex = generateMatchVertex(node);
				break;
			case Cross:
				vertex = generateCrossVertex(node);
				break;
			case Cogroup:
				vertex = generateCoGroupVertex(node);
				break;
			case DataSource:
				vertex = generateDataSourceVertex(node);
				break;
			case DataSink:
				vertex = generateDataSinkVertex(node);
				break;
			default:
				throw new Exception("Unknown PACT type: " + node.getPactType());
			}
		} catch (NotEnoughMemoryException nemex) {
			throw new CompilerException("The available memory portion of " + node.getMemoryPerTask()
				+ " megabytes is not sufficient for the task '" + node.toString()
				+ "' Decrease the intra-node parallelism or use instances with more memory.");
		} catch (Exception e) {
			throw new CompilerException(
				"An error occurred while translating the optimized plan to a nephele JobGraph: " + e.getMessage(), e);
		}

		// set degree of parallelism
		int pd = node.getDegreeOfParallelism();
		vertex.setNumberOfSubtasks(pd);

		// check whether this is the vertex with the highest degree of parallelism
		if (maxDegreeVertex == null || maxDegreeVertex.getNumberOfSubtasks() < pd) {
			maxDegreeVertex = vertex;
		}

		// set the number of tasks per instance
		if (node.getInstancesPerMachine() >= 1) {
			vertex.setNumberOfSubtasksPerInstance(node.getInstancesPerMachine());
		}

		// store in the map
		this.vertices.put(node, vertex);

		// returning true causes deeper descend
		return true;
	}

	/**
	 * This method implements the post-visit during the depth-first traversal. When the post visit happens,
	 * all of the descendants have been processed, so this method connects all of the current node's
	 * predecessors to the current node.
	 * 
	 * @param node
	 *        The node currently processed during the post-visit.
	 * @see eu.stratosphere.pact.common.plan.Visitor#postVisit(eu.stratosphere.pact.common.plan.Visitable)
	 */
	@Override
	public void postVisit(OptimizerNode node) {

		try {
			// get pact vertex
			AbstractJobVertex inputVertex = this.vertices.get(node);
			List<PactConnection> incomingConns = node.getIncomingConnections();

			if (incomingConns == null) {
				// data source
				return;
			}

			for (PactConnection connection : node.getIncomingConnections()) {
				// get parent vertex
				AbstractJobVertex outputVertex = this.vertices.get(connection.getSourcePact());
				if (outputVertex == null) {
					throw new Exception("Parent vertex was not initialized");
				}

				switch (connection.getShipStrategy()) {
				case FORWARD:
					connectWithForwardStrategy(connection, outputVertex, inputVertex);
					break;
				case PARTITION_HASH:
					connectWithPartitionStrategy(connection, outputVertex, inputVertex);
					break;
				case BROADCAST:
					connectWithBroadcastStrategy(connection, outputVertex, inputVertex);
					break;
				case SFR:
					connectWithSFRStrategy(connection, outputVertex, inputVertex);
				default:
					throw new Exception("Invalid ship strategy: " + connection.getShipStrategy());
				}
			}
		} catch (Exception e) {
			throw new CompilerException(
				"An error occurred while translating the optimized plan to a nephele JobGraph: " + e.getMessage(), e);
		}
	}

	// ------------------------------------------------------------------------
	// Methods for creating individual vertices
	// ------------------------------------------------------------------------

	/**
	 * @param mapNode
	 * @return
	 * @throws CompilerException
	 */
	private JobTaskVertex generateMapVertex(OptimizerNode mapNode) throws CompilerException {
		// create task vertex
		JobTaskVertex mapVertex = new JobTaskVertex(mapNode.getPactContract().getName(), this.jobGraph);
		// set task class
		mapVertex.setTaskClass(MapTask.class);

		// get task configuration object
		TaskConfig mapConfig = new TaskConfig(mapVertex.getConfiguration());
		// set user code class
		mapConfig.setStubClass(mapNode.getPactContract().getStubClass());

		// set local strategy
		switch (mapNode.getLocalStrategy()) {
		case NONE:
			mapConfig.setLocalStrategy(LocalStrategy.NONE);
			break;
		default:
			throw new CompilerException("Invalid local strategy for 'Map' (" + mapNode.getName() + "): "
				+ mapNode.getLocalStrategy());
		}

		// forward stub parameters to task and stub
		mapConfig.setStubParameters(mapNode.getPactContract().getStubParameters());

		return mapVertex;
	}

	/**
	 * @param combineNode
	 * @return
	 * @throws CompilerException
	 */
	private JobTaskVertex generateCombineVertex(CombinerNode combineNode) throws CompilerException {
		JobTaskVertex combineVertex = new JobTaskVertex("Combiner for " + combineNode.getPactContract().getName(),
			this.jobGraph);
		combineVertex.setTaskClass(CombineTask.class);

		TaskConfig combineConfig = new TaskConfig(combineVertex.getConfiguration());
		combineConfig.setStubClass(combineNode.getPactContract().getStubClass());

		// we have currently only one strategy for combiners
		combineConfig.setLocalStrategy(LocalStrategy.COMBININGSORT);

		// assign the memory
		assignMemory(combineConfig, combineNode.getMemoryPerTask());

		// forward stub parameters to task and stub
		combineConfig.setStubParameters(combineNode.getPactContract().getStubParameters());

		return combineVertex;
	}

	/**
	 * @param reduceNode
	 * @return
	 * @throws CompilerException
	 */
	private JobTaskVertex generateReduceVertex(ReduceNode reduceNode) throws CompilerException {
		// create task vertex
		JobTaskVertex reduceVertex = new JobTaskVertex(reduceNode.getPactContract().getName(), this.jobGraph);
		// set task class
		reduceVertex.setTaskClass(ReduceTask.class);

		// get task configuration object
		TaskConfig reduceConfig = new TaskConfig(reduceVertex.getConfiguration());
		// set user code class
		reduceConfig.setStubClass(reduceNode.getPactContract().getStubClass());

		// set local strategy
		switch (reduceNode.getLocalStrategy()) {
		case SORT:
			reduceConfig.setLocalStrategy(LocalStrategy.SORT);
			break;
		case COMBININGSORT:
			reduceConfig.setLocalStrategy(LocalStrategy.COMBININGSORT);
			break;
		case NONE:
			reduceConfig.setLocalStrategy(LocalStrategy.NONE);
			break;
		default:
			throw new CompilerException("Invalid local strategy for 'Reduce' (" + reduceNode.getName() + "): "
				+ reduceNode.getLocalStrategy());
		}

		// assign the memory
		assignMemory(reduceConfig, reduceNode.getMemoryPerTask());

		// forward stub parameters to task and stub
		reduceConfig.setStubParameters(reduceNode.getPactContract().getStubParameters());

		return reduceVertex;
	}

	/**
	 * @param matchNode
	 * @return
	 * @throws CompilerException
	 */
	private JobTaskVertex generateMatchVertex(OptimizerNode matchNode) throws CompilerException {
		// create task vertex
		JobTaskVertex matchVertex = new JobTaskVertex(matchNode.getPactContract().getName(), this.jobGraph);
		// set task class
		matchVertex.setTaskClass(MatchTask.class);

		// get task configuration object
		TaskConfig matchConfig = new TaskConfig(matchVertex.getConfiguration());
		// set user code class
		matchConfig.setStubClass(matchNode.getPactContract().getStubClass());

		// set local strategy
		switch (matchNode.getLocalStrategy()) {
		case SORTMERGE:
			matchConfig.setLocalStrategy(LocalStrategy.SORTMERGE);
			break;
		case HYBRIDHASH_FIRST:
			matchConfig.setLocalStrategy(LocalStrategy.HYBRIDHASH_FIRST);
			break;
		case HYBRIDHASH_SECOND:
			matchConfig.setLocalStrategy(LocalStrategy.HYBRIDHASH_SECOND);
			break;
		case MMHASH_FIRST:
			matchConfig.setLocalStrategy(LocalStrategy.MMHASH_FIRST);
			break;
		case MMHASH_SECOND:
			matchConfig.setLocalStrategy(LocalStrategy.MMHASH_SECOND);
			break;
		default:
			throw new CompilerException("Invalid local strategy for 'Match' (" + matchNode.getName() + "): "
				+ matchNode.getLocalStrategy());
		}

		// assign the memory
		assignMemory(matchConfig, matchNode.getMemoryPerTask());

		// forward stub parameters to task and stub
		matchConfig.setStubParameters(matchNode.getPactContract().getStubParameters());

		return matchVertex;
	}

	/**
	 * @param crossNode
	 * @return
	 * @throws CompilerException
	 */
	private JobTaskVertex generateCrossVertex(OptimizerNode crossNode) throws CompilerException {
		// create task vertex
		JobTaskVertex crossVertex = new JobTaskVertex(crossNode.getPactContract().getName(), this.jobGraph);
		// set task class
		crossVertex.setTaskClass(CrossTask.class);

		// get task configuration object
		TaskConfig crossConfig = new TaskConfig(crossVertex.getConfiguration());
		// set user code class
		crossConfig.setStubClass(crossNode.getPactContract().getStubClass());

		// set local strategy
		switch (crossNode.getLocalStrategy()) {
		case NESTEDLOOP_BLOCKED_OUTER_FIRST:
			crossConfig.setLocalStrategy(LocalStrategy.NESTEDLOOP_BLOCKED_OUTER_FIRST);
			break;
		case NESTEDLOOP_BLOCKED_OUTER_SECOND:
			crossConfig.setLocalStrategy(LocalStrategy.NESTEDLOOP_BLOCKED_OUTER_SECOND);
			break;
		case NESTEDLOOP_STREAMED_OUTER_FIRST:
			crossConfig.setLocalStrategy(LocalStrategy.NESTEDLOOP_STREAMED_OUTER_FIRST);
			break;
		case NESTEDLOOP_STREAMED_OUTER_SECOND:
			crossConfig.setLocalStrategy(LocalStrategy.NESTEDLOOP_STREAMED_OUTER_SECOND);
			break;
		default:
			throw new CompilerException("Invalid local strategy for 'Cross' (" + crossNode.getName() + "): "
				+ crossNode.getLocalStrategy());
		}

		crossConfig.setIOBufferSize(crossNode.getMemoryPerTask());

		// forward stub parameters to task and stub
		crossConfig.setStubParameters(crossNode.getPactContract().getStubParameters());

		return crossVertex;
	}

	/**
	 * @param coGroupNode
	 * @return
	 * @throws CompilerException
	 */
	private JobTaskVertex generateCoGroupVertex(OptimizerNode coGroupNode) throws CompilerException {
		// create task vertex
		JobTaskVertex coGroupVertex = new JobTaskVertex(coGroupNode.getPactContract().getName(), this.jobGraph);
		// set task class
		coGroupVertex.setTaskClass(CoGroupTask.class);

		// get task configuration object
		TaskConfig coGroupConfig = new TaskConfig(coGroupVertex.getConfiguration());
		// set user code class
		coGroupConfig.setStubClass(coGroupNode.getPactContract().getStubClass());

		// set local strategy
		switch (coGroupNode.getLocalStrategy()) {
		case SORTMERGE:
			coGroupConfig.setLocalStrategy(LocalStrategy.SORTMERGE);
			break;
		default:
			throw new CompilerException("Invalid local strategy for 'CoGroup' (" + coGroupNode.getName() + "): "
				+ coGroupNode.getLocalStrategy());
		}

		// assign the memory
		assignMemory(coGroupConfig, coGroupNode.getMemoryPerTask());

		// forward stub parameters to task and stub
		coGroupConfig.setStubParameters(coGroupNode.getPactContract().getStubParameters());

		return coGroupVertex;
	}

	/**
	 * @param sourceNode
	 * @return
	 * @throws CompilerException
	 */
	private JobFileInputVertex generateDataSourceVertex(OptimizerNode sourceNode) throws CompilerException {
		DataSourceNode dsn = (DataSourceNode) sourceNode;
		DataSourceContract<?, ?> contract = (DataSourceContract<?, ?>) (dsn.getPactContract());

		// create task vertex
		JobFileInputVertex sourceVertex = new JobFileInputVertex(contract.getName(), this.jobGraph);
		// set task class
		sourceVertex.setFileInputClass(DataSourceTask.class);
		// set file path
		sourceVertex.setFilePath(new Path(contract.getFilePath()));

		// get task configuration object
		DataSourceTask.Config sourceConfig = new DataSourceTask.Config(sourceVertex.getConfiguration());
		// set user code class
		sourceConfig.setStubClass(contract.getStubClass());
		// set format parameter
		sourceConfig.setFormatParameters(contract.getFormatParameters());

		// set local strategy
		switch (sourceNode.getLocalStrategy()) {
		case NONE:
			sourceConfig.setLocalStrategy(LocalStrategy.NONE);
			break;
		default:
			throw new CompilerException("Invalid local strategy for 'DataSource'(" + sourceNode.getName() + "): "
				+ sourceNode.getLocalStrategy());
		}

		// forward stub parameters to task and data format
		sourceConfig.setStubParameters(sourceNode.getPactContract().getStubParameters());

		return sourceVertex;
	}

	/**
	 * @param sinkNode
	 * @return
	 * @throws CompilerException
	 */
	private JobFileOutputVertex generateDataSinkVertex(OptimizerNode sinkNode) throws CompilerException {
		// create task vertex
		JobFileOutputVertex sinkVertex = new JobFileOutputVertex(sinkNode.getPactContract().getName(), this.jobGraph);
		// set task class
		sinkVertex.setFileOutputClass(DataSinkTask.class);
		// set output path
		sinkVertex.setFilePath(new Path(((DataSinkContract<?, ?>) sinkNode.getPactContract()).getFilePath()));

		// get task configuration object
		DataSinkTask.Config sinkConfig = new DataSinkTask.Config(sinkVertex.getConfiguration());
		// set user code class
		sinkConfig.setStubClass(((DataSinkContract<?, ?>) sinkNode.getPactContract()).getStubClass());
		// set format parameter
		sinkConfig.setStubParameters((((DataSinkContract<?, ?>) sinkNode.getPactContract()).getFormatParameters()));

		// set local strategy
		switch (sinkNode.getLocalStrategy()) {
		case NONE:
			sinkConfig.setLocalStrategy(LocalStrategy.NONE);
			break;
		default:
			throw new CompilerException("Invalid local strategy for 'DataSink' (" + sinkNode.getName() + "): "
				+ sinkNode.getLocalStrategy());
		}

		// forward stub parameters to task and data format
		sinkConfig.setStubParameters(sinkNode.getPactContract().getStubParameters());

		return sinkVertex;
	}

	/**
	 * @param stubClass
	 * @param dop
	 * @return
	 */
	private JobTaskVertex generateTempVertex(Class<? extends Stub<? extends Key, ? extends Value>> stubClass, int dop) {
		// create task vertex
		JobTaskVertex tempVertex = new JobTaskVertex("TempVertex", this.jobGraph);
		// set task class
		tempVertex.setTaskClass(TempTask.class);

		// get task configuration object
		TaskConfig tempConfig = new TaskConfig(tempVertex.getConfiguration());
		// set key and value classes
		tempConfig.setStubClass(stubClass);

		tempConfig.setIOBufferSize(2);

		// set degree of parallelism
		tempVertex.setNumberOfSubtasks(dop);

		return tempVertex;
	}

	// ------------------------------------------------------------------------
	// Connecting Vertices
	// ------------------------------------------------------------------------

	/**
	 * @param connection
	 * @param outputVertex
	 * @param inputVertex
	 * @throws CompilerException
	 */
	private void connectWithForwardStrategy(PactConnection connection, AbstractJobVertex outputVertex,
			AbstractJobVertex inputVertex) throws CompilerException, JobGraphDefinitionException {
		// TODO: currently we do a 1-to-1 mapping one the same instance. Hence, we use INMEMORY channels
		// We should add the possibility to distribute the load to multiple machines (one local, x remote)

		// check if shipStrategy suits child
		switch (connection.getTargetPact().getPactType()) {
		case Map:
			// ok (Default)
			break;
		case Reduce:
			// ok (Partitioning exists already)
			break;
		case Match:
			// ok (Partitioning exist already or forward for broadcast)
			break;
		case Cross:
			// ok (Forward for broadcast)
			break;
		case Cogroup:
			// ok (Partitioning exist already)
			break;
		case DataSink:
			// ok
			break;
		default:
			throw new CompilerException("ShipStrategy " + connection.getShipStrategy().name() + " does not suit PACT "
				+ connection.getTargetPact().getPactType().name());
		}

		connectJobVertices(connection, outputVertex, inputVertex);

	}

	/**
	 * @param connection
	 * @param outputVertex
	 * @param inputVertex
	 * @throws CompilerException
	 * @throws JobGraphDefinitionException
	 */
	private void connectWithPartitionStrategy(PactConnection connection, AbstractJobVertex outputVertex,
			AbstractJobVertex inputVertex) throws CompilerException, JobGraphDefinitionException {
		// check if shipStrategy suits child
		switch (connection.getTargetPact().getPactType()) {
		case Map:
			// ok (Partitioning before map increases data volume)
			break;
		case Reduce:
			// ok (Default)
			break;
		case Match:
			// ok (Partitioning exist already or forward for broadcast)
			break;
		case Cross:
			// ok (Partitioning with broadcast before cross increases data volume)
			break;
		case Cogroup:
			// ok (Default)
			break;
		case DataSink:
			// ok
			break;
		default:
			throw new CompilerException("ShipStrategy " + connection.getShipStrategy().name() + " does not suit PACT "
				+ connection.getTargetPact().getPactType().name());
		}

		connectJobVertices(connection, outputVertex, inputVertex);
	}

	/**
	 * @param connection
	 * @param outputVertex
	 * @param inputVertex
	 * @throws CompilerException
	 * @throws JobGraphDefinitionException
	 */
	private void connectWithBroadcastStrategy(PactConnection connection, AbstractJobVertex outputVertex,
			AbstractJobVertex inputVertex) throws CompilerException, JobGraphDefinitionException {
		// check if shipStrategy suits child
		switch (connection.getTargetPact().getPactType()) {
		case Match:
			// ok (Broadcast)
			break;
		case Cross:
			// ok (Broadcast)
			break;
		default:
			throw new CompilerException("ShipStrategy " + connection.getShipStrategy().name() + " does not suit PACT "
				+ connection.getTargetPact().getPactType().name());
		}

		connectJobVertices(connection, outputVertex, inputVertex);
	}

	/**
	 * @param connection
	 * @param outputVertex
	 * @param inputVertex
	 * @throws CompilerException
	 * @throws JobGraphDefinitionException
	 */
	private void connectWithSFRStrategy(PactConnection connection, AbstractJobVertex outputVertex,
			AbstractJobVertex inputVertex) throws CompilerException, JobGraphDefinitionException {
		// check if shipStrategy suits child
		switch (connection.getTargetPact().getPactType()) {
		case Cross:
			// ok
			break;
		default:
			throw new CompilerException("ShipStrategy " + connection.getShipStrategy().name() + " does not suit PACT "
				+ connection.getTargetPact().getPactType().name());
		}

		// TODO: implement SFR
		throw new UnsupportedOperationException("SFR shipping strategy not supported yet");
	}

	/**
	 * @param connection
	 * @param outputVertex
	 * @param inputVertex
	 * @throws JobGraphDefinitionException
	 * @throws CompilerException
	 */
	private void connectJobVertices(PactConnection connection, AbstractJobVertex outputVertex,
			AbstractJobVertex inputVertex) throws JobGraphDefinitionException, CompilerException {
		ChannelType channelType = null;

		switch (connection.getShipStrategy()) {
		case FORWARD:
			channelType = ChannelType.INMEMORY;
			break;
		case PARTITION_HASH:
			channelType = ChannelType.NETWORK;
			break;
		case BROADCAST:
			channelType = ChannelType.NETWORK;
			break;
		case SFR:
			channelType = ChannelType.NETWORK;
			break;
		}

		TaskConfig outputConfig = new TaskConfig(outputVertex.getConfiguration());
		TaskConfig inputConfig = new TaskConfig(inputVertex.getConfiguration());
		TaskConfig tempConfig = null;

		switch (connection.getTempMode()) {
		case NONE:
			// connect child with inmemory channel
			outputVertex.connectTo(inputVertex, channelType, CompressionLevel.NO_COMPRESSION);
			// set ship strategy in vertex and child

			// set strategies in task configs
			outputConfig.addOutputShipStrategy(connection.getShipStrategy());
			inputConfig.addInputShipStrategy(connection.getShipStrategy());
			break;
		case TEMP_SENDER_SIDE:
			// create tempTask
			int pd = connection.getSourcePact().getDegreeOfParallelism();

			JobTaskVertex tempVertex = generateTempVertex(
			// source pact stub contains out key and value
				(Class<? extends Stub<?, ?>>) connection.getSourcePact().getPactContract().getStubClass(),
				// keep parallelization of source pact
				pd);

			// insert tempVertex between outputVertex and inputVertex and connect them
			outputVertex.connectTo(tempVertex, ChannelType.INMEMORY, CompressionLevel.NO_COMPRESSION);
			tempVertex.connectTo(inputVertex, channelType, CompressionLevel.NO_COMPRESSION);

			// get tempVertex config
			tempConfig = new TaskConfig(tempVertex.getConfiguration());

			// set strategies in task configs
			outputConfig.addOutputShipStrategy(ShipStrategy.FORWARD);
			tempConfig.addInputShipStrategy(ShipStrategy.FORWARD);
			tempConfig.addOutputShipStrategy(connection.getShipStrategy());
			inputConfig.addInputShipStrategy(connection.getShipStrategy());

			break;
		case TEMP_RECEIVER_SIDE:
			int pdr = connection.getTargetPact().getDegreeOfParallelism();

			// create tempVertex
			tempVertex = generateTempVertex(
			// source pact stub contains out key and value
				(Class<? extends Stub<?, ?>>) connection.getSourcePact().getPactContract().getStubClass(),
				// keep parallelization of target pact
				pdr);

			// insert tempVertex between outputVertex and inputVertex and connect them
			outputVertex.connectTo(tempVertex, channelType, CompressionLevel.NO_COMPRESSION);
			tempVertex.connectTo(inputVertex, ChannelType.INMEMORY, CompressionLevel.NO_COMPRESSION);

			// get tempVertex config
			tempConfig = new TaskConfig(tempVertex.getConfiguration());

			// set strategies in task configs
			outputConfig.addOutputShipStrategy(connection.getShipStrategy());
			tempConfig.addInputShipStrategy(connection.getShipStrategy());
			tempConfig.addOutputShipStrategy(ShipStrategy.FORWARD);
			inputConfig.addInputShipStrategy(ShipStrategy.FORWARD);

			break;
		default:
			throw new CompilerException("Invalid connection temp mode: " + connection.getTempMode());
		}
	}

	// ------------------------------------------------------------------------
	// Assigning Memory
	// ------------------------------------------------------------------------

	private void assignMemory(TaskConfig config, int memSize) {
		// this code currently has a very simple way of assigning space to the I/O and sort buffers
		// in future releases, we plan to design the sort-component in a more adaptive fashion,
		// making it distribute its memory among sort and merge space by itself

		int ioMem, sortMem, numSortBuffers, sortBufferSize;

		// decide how to divide the memory between sort and I/O space
		if (memSize > 512) {
			ioMem = MAX_IO_BUFFER_SIZE;
		} else if (memSize > 64) {
			ioMem = memSize / 32;
		} else if (memSize > 32) {
			ioMem = 2;
		} else if (memSize > MIN_SORT_HEAP + MIN_IO_BUFFER_SIZE) {
			ioMem = MIN_IO_BUFFER_SIZE;
		} else {
			throw new NotEnoughMemoryException();
		}
		sortMem = memSize - ioMem;

		// decide how to divide the sort memory among different buffers
		if (sortMem > 3 * MAX_SORT_HEAP_BUFFER_SIZE) {
			numSortBuffers = sortMem / MAX_SORT_HEAP_BUFFER_SIZE + 1;
			// correct rounding loss
			numSortBuffers = sortMem / (sortMem / numSortBuffers);
		} else if (sortMem > 3 * 64) {
			numSortBuffers = 3;
		} else if (sortMem >= 2 * MIN_SORT_HEAP) {
			numSortBuffers = 2;
		} else {
			numSortBuffers = 1;
		}
		sortBufferSize = sortMem / numSortBuffers;

		// set the config
		config.setIOBufferSize(ioMem);
		config.setMergeFactor(DEFAUTL_MERGE_FACTOR);
		config.setNumSortBuffer(numSortBuffers);
		config.setSortBufferSize(sortBufferSize);
	}

	// ------------------------------------------------------------------------

	private static final class NotEnoughMemoryException extends RuntimeException {
		private static final long serialVersionUID = -1996018032841078865L;
	}
}
