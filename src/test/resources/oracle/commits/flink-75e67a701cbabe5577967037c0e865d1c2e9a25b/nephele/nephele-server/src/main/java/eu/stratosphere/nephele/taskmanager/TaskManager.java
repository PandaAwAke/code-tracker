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

package eu.stratosphere.nephele.taskmanager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.stratosphere.nephele.configuration.ConfigConstants;
import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.nephele.configuration.GlobalConfiguration;
import eu.stratosphere.nephele.discovery.DiscoveryException;
import eu.stratosphere.nephele.discovery.DiscoveryService;
import eu.stratosphere.nephele.execution.Environment;
import eu.stratosphere.nephele.execution.ExecutionState;
import eu.stratosphere.nephele.execution.librarycache.LibraryCacheManager;
import eu.stratosphere.nephele.execution.librarycache.LibraryCacheProfileRequest;
import eu.stratosphere.nephele.execution.librarycache.LibraryCacheProfileResponse;
import eu.stratosphere.nephele.execution.librarycache.LibraryCacheUpdate;
import eu.stratosphere.nephele.executiongraph.ExecutionVertexID;
import eu.stratosphere.nephele.instance.InstanceConnectionInfo;
import eu.stratosphere.nephele.io.InputGate;
import eu.stratosphere.nephele.io.OutputGate;
import eu.stratosphere.nephele.io.channels.AbstractInputChannel;
import eu.stratosphere.nephele.io.channels.AbstractOutputChannel;
import eu.stratosphere.nephele.io.channels.ChannelSetupException;
import eu.stratosphere.nephele.io.channels.ChannelType;
import eu.stratosphere.nephele.io.channels.bytebuffered.AbstractByteBufferedInputChannel;
import eu.stratosphere.nephele.io.channels.bytebuffered.AbstractByteBufferedOutputChannel;
import eu.stratosphere.nephele.io.channels.bytebuffered.FileInputChannel;
import eu.stratosphere.nephele.io.channels.bytebuffered.FileOutputChannel;
import eu.stratosphere.nephele.io.channels.bytebuffered.NetworkInputChannel;
import eu.stratosphere.nephele.io.channels.bytebuffered.NetworkOutputChannel;
import eu.stratosphere.nephele.io.channels.direct.AbstractDirectInputChannel;
import eu.stratosphere.nephele.io.channels.direct.AbstractDirectOutputChannel;
import eu.stratosphere.nephele.io.channels.direct.InMemoryInputChannel;
import eu.stratosphere.nephele.io.channels.direct.InMemoryOutputChannel;
import eu.stratosphere.nephele.ipc.RPC;
import eu.stratosphere.nephele.ipc.Server;
import eu.stratosphere.nephele.jobgraph.JobID;
import eu.stratosphere.nephele.net.NetUtils;
import eu.stratosphere.nephele.profiling.ProfilingUtils;
import eu.stratosphere.nephele.profiling.TaskManagerProfiler;
import eu.stratosphere.nephele.protocols.ChannelLookupProtocol;
import eu.stratosphere.nephele.protocols.JobManagerProtocol;
import eu.stratosphere.nephele.protocols.TaskOperationProtocol;
import eu.stratosphere.nephele.services.iomanager.IOManager;
import eu.stratosphere.nephele.services.memorymanager.MemoryManager;
import eu.stratosphere.nephele.services.memorymanager.spi.DefaultMemoryManager;
import eu.stratosphere.nephele.taskmanager.bytebuffered.ByteBufferedChannelManager;
import eu.stratosphere.nephele.taskmanager.bytebuffered.ByteBufferedOutputChannelGroup;
import eu.stratosphere.nephele.taskmanager.checkpointing.CheckpointManager;
import eu.stratosphere.nephele.taskmanager.direct.DirectChannelManager;
import eu.stratosphere.nephele.types.Record;
import eu.stratosphere.nephele.util.StringUtils;

/**
 * A task manager receives tasks from the job manager and executes them. After having executed them
 * (or in case of an execution error) it reports the execution result back to the job manager.
 * Task managers are able to automatically discover the job manager and receive its configuration from it
 * as long as the job manager is running on the same local network
 * 
 * @author warneke
 */
public class TaskManager implements TaskOperationProtocol {

	private static final Log LOG = LogFactory.getLog(TaskManager.class);

	private final JobManagerProtocol jobManager;

	private final ChannelLookupProtocol lookupService;

	private static final int handlerCount = 1;

	private final Server taskManagerServer;

	/**
	 * This map contains all the tasks whose threads are in a state other than TERMINATED. If any task
	 * is stored inside this map and its thread status is TERMINATED, this indicates a virtual machine error.
	 * As a result, task status will switch to FAILED and reported to the {@link JobManager}.
	 */
	private final Map<ExecutionVertexID, Environment> runningTasks = new HashMap<ExecutionVertexID, Environment>();

	private final InstanceConnectionInfo localInstanceConnectionInfo;

	private final static int FAILURERETURNCODE = -1;

	private final static int DEFAULTPERIODICTASKSINTERVAL = 1000;

	/**
	 * The instance of the {@link ByteBufferedChannelManager} which is responsible for
	 * setting up and cleaning up the byte buffered channels of the tasks.
	 */
	private final ByteBufferedChannelManager byteBufferedChannelManager;

	/**
	 * The instance of the {@link DirectChannelManager} which is responsible for
	 * setting up and cleaning up the direct channels of the tasks.
	 */
	private final DirectChannelManager directChannelManager;

	/**
	 * The instance of the {@link CheckpointManager} to restore
	 * previously written checkpoints.
	 */
	private final CheckpointManager checkpointManager;

	/**
	 * Instance of the task manager profile if profiling is enabled.
	 */
	private final TaskManagerProfiler profiler;

	private MemoryManager memoryManager;

	private IOManager ioManager;

	/**
	 * Constructs a new task manager, starts its IPC service and attempts to discover the job manager to
	 * receive an initial configuration.
	 * 
	 * @param configDir
	 *        the directory containing the configuration files for the task manager
	 */
	public TaskManager(String configDir) {

		// First, try to load global configuration
		GlobalConfiguration.loadConfiguration(configDir);

		// Use discovery service to find the job manager in the network?
		InetSocketAddress jobManagerAddress = null;
		if (GlobalConfiguration.getBoolean(ConfigConstants.TASK_MANAGER_USE_DISCOVERY_KEY, true)) {
			try {
				jobManagerAddress = DiscoveryService.getJobManagerAddress();
			} catch (DiscoveryException e) {
				e.printStackTrace();
				System.exit(FAILURERETURNCODE);
			}

		} else {
			final String address = GlobalConfiguration.getString(ConfigConstants.JOB_MANAGER_IPC_ADDRESS_KEY,
				ConfigConstants.DEFAULT_JOB_MANAGER_IPC_ADDRESS);
			final int port = GlobalConfiguration.getInteger(ConfigConstants.JOB_MANAGER_IPC_PORT_KEY,
				ConfigConstants.DEFAULT_JOB_MANAGER_IPC_PORT);
			jobManagerAddress = new InetSocketAddress(address, port);
		}

		LOG.info("Determined address of job manager to be " + jobManagerAddress);

		// Determine interface address that is announced to the job manager
		final int ipcPort = GlobalConfiguration.getInteger(ConfigConstants.TASK_MANAGER_IPC_PORT_KEY,
			ConfigConstants.DEFAULT_TASK_MANAGER_IPC_PORT);
		final int dataPort = GlobalConfiguration.getInteger(ConfigConstants.TASK_MANAGER_DATA_PORT_KEY,
			ConfigConstants.DEFAULT_TASK_MANAGER_DATA_PORT);

		final InetAddress taskManagerAnnounceAddress = DiscoveryService.findLocalAddressOnSameNetwork(jobManagerAddress
			.getAddress());
		final InetSocketAddress taskManagerBindAddress = new InetSocketAddress(DiscoveryService.getServiceAddress(),
			ipcPort);

		this.localInstanceConnectionInfo = new InstanceConnectionInfo(taskManagerAnnounceAddress, ipcPort, dataPort);

		LOG.info("Announcing connection information " + this.localInstanceConnectionInfo + " to job manager");

		// Try to create local stub for the job manager
		JobManagerProtocol jobManager = null;
		try {
			jobManager = (JobManagerProtocol) RPC.getProxy(JobManagerProtocol.class, jobManagerAddress, NetUtils
				.getSocketFactory());
		} catch (IOException e) {
			LOG.error(StringUtils.stringifyException(e));
			System.exit(FAILURERETURNCODE);
		}
		this.jobManager = jobManager;

		// Try to create local stub for the lookup service
		ChannelLookupProtocol lookupService = null;
		try {
			lookupService = (ChannelLookupProtocol) RPC.getProxy(ChannelLookupProtocol.class, jobManagerAddress,
				NetUtils.getSocketFactory());
		} catch (IOException e) {
			LOG.error(StringUtils.stringifyException(e));
			System.exit(FAILURERETURNCODE);
		}
		this.lookupService = lookupService;

		// Start local RPC server
		Server taskManagerServer = null;
		try {
			taskManagerServer = RPC.getServer(this, taskManagerBindAddress.getHostName(), taskManagerBindAddress
				.getPort(), handlerCount, false);
			taskManagerServer.start();
		} catch (IOException e) {
			LOG.error(StringUtils.stringifyException(e));
			System.exit(FAILURERETURNCODE);
		}
		this.taskManagerServer = taskManagerServer;

		// Load profiler if it should be used
		if (GlobalConfiguration.getBoolean(ProfilingUtils.ENABLE_PROFILING_KEY, false)) {
			final String profilerClassName = GlobalConfiguration.getString(ProfilingUtils.TASKMANAGER_CLASSNAME_KEY,
				null);
			if (profilerClassName == null) {
				LOG.error("Cannot find class name for the profiler");
				System.exit(FAILURERETURNCODE);
			}
			this.profiler = ProfilingUtils.loadTaskManagerProfiler(profilerClassName, jobManagerAddress.getAddress(),
				this.localInstanceConnectionInfo);
		} else {
			this.profiler = null;
			LOG.debug("Profiler disabled");
		}

		// Get the directory for storing temporary files
		final String tmpDirPath = GlobalConfiguration.getString(ConfigConstants.TASK_MANAGER_TMP_DIR_KEY,
			ConfigConstants.DEFAULT_TASK_MANAGER_TMP_PATH);

		// Initialize the byte buffered channel manager
		ByteBufferedChannelManager byteBufferedChannelManager = null;
		try {
			byteBufferedChannelManager = new ByteBufferedChannelManager(this.lookupService,
				this.localInstanceConnectionInfo.getAddress(), this.localInstanceConnectionInfo.getDataPort(),
				tmpDirPath);
		} catch (IOException ioe) {
			LOG.error(StringUtils.stringifyException(ioe));
			ioe.printStackTrace();
			System.exit(FAILURERETURNCODE);
		}
		this.byteBufferedChannelManager = byteBufferedChannelManager;

		// Initialize the direct channel manager
		this.directChannelManager = new DirectChannelManager();

		// Initialize the checkpoint manager
		this.checkpointManager = new CheckpointManager(this.byteBufferedChannelManager, tmpDirPath);

		// Initialize the memory manager
		long memorySize = GlobalConfiguration.getInteger(ConfigConstants.MEMORY_MANAGER_AVAILABLE_MEMORY_SIZE_KEY,
			ConfigConstants.DEFAULT_MEMORY_MANAGER_AVAILABLE_MEMORY);
		if (memorySize < 1) {
			// get the fraction configuration
			String mss = GlobalConfiguration.getString(ConfigConstants.MEMORY_MANAGER_AVAILABLE_MEMORY_FRACTION_KEY,
				String.valueOf(ConfigConstants.DEFAULT_MEMORY_MANAGER_AVAILABLE_MEMORY_FRACTION));
			float fract = ConfigConstants.DEFAULT_MEMORY_MANAGER_AVAILABLE_MEMORY_FRACTION;
			try {
				fract = Float.parseFloat(mss);
			} catch (NumberFormatException nfex) {
				LOG.warn("Invalid parameter for " + ConfigConstants.MEMORY_MANAGER_AVAILABLE_MEMORY_FRACTION_KEY
					+ " in the configuration. Using default value of "
					+ ConfigConstants.DEFAULT_MEMORY_MANAGER_AVAILABLE_MEMORY_FRACTION
					+ " for the memory fraction dedicated to the MemoryManager.");
			}

			LOG.info("Initializing MemoryManager with a fraction of " + fract + " of the total free memory.");

			this.memoryManager = DefaultMemoryManager.getWithHeapFraction(fract,
				ConfigConstants.DEFAULT_MEMORY_MANAGER_MIN_UNRESERVED_MEMORY);
		} else {
			LOG.info("Initializing memory manager with " + memorySize + " megabytes of memory");
			this.memoryManager = new DefaultMemoryManager(memorySize * 1024L * 1024L);
		}

		// Initialize the io manager
		this.ioManager = new IOManager(tmpDirPath);

		// Add shutdown hook for clean up tasks
		Runtime.getRuntime().addShutdownHook(new TaskManagerCleanUp(this));
	}

	/**
	 * Entry point for the program.
	 * 
	 * @param args
	 *        arguments from the command line
	 */
	@SuppressWarnings("static-access")
	public static void main(String[] args) {

		Option configDirOpt = OptionBuilder.withArgName("config directory").hasArg().withDescription(
			"Specify configuration directory.").create("configDir");

		Options options = new Options();
		options.addOption(configDirOpt);

		CommandLineParser parser = new GnuParser();
		CommandLine line = null;
		try {
			line = parser.parse(options, args);
		} catch (ParseException e) {
			System.err.println("CLI Parsing failed. Reason: " + e.getMessage());
			System.exit(FAILURERETURNCODE);
		}

		String configDir = line.getOptionValue(configDirOpt.getOpt(), null);

		// Create a new task manager object
		TaskManager taskManager = new TaskManager(configDir);

		// Run the main I/O loop
		taskManager.runIOLoop();

		// Clean up
		taskManager.cleanUp();
	}

	/**
	 * Registers the input channels of an incoming task with the task manager.
	 * 
	 * @param eig
	 *        the input gate whose input channels should be registered
	 * @throws ChannelSetupException
	 *         thrown if one of the channels could not be set up correctly
	 */
	// This method is called by the RPC server thread
	private void registerInputChannels(InputGate<? extends Record> eig) throws ChannelSetupException {

		for (int i = 0; i < eig.getNumberOfInputChannels(); i++) {

			AbstractInputChannel<? extends Record> eic = eig.getInputChannel(i);

			if (eic instanceof NetworkInputChannel<?>) {
				this.byteBufferedChannelManager
					.registerByteBufferedInputChannel((AbstractByteBufferedInputChannel<? extends Record>) eic);
			} else if (eic instanceof FileInputChannel<?>) {
				this.byteBufferedChannelManager
					.registerByteBufferedInputChannel((AbstractByteBufferedInputChannel<? extends Record>) eic);
				// Start recovery of the checkpoint
				this.checkpointManager.recoverChannelCheckpoint(eic.getConnectedChannelID());
			} else if (eic instanceof InMemoryInputChannel<?>) {
				this.directChannelManager
					.registerDirectInputChannel((AbstractDirectInputChannel<? extends Record>) eic);
			} else {
				throw new ChannelSetupException("Type of input channel " + eic.getID() + " is not supported");
			}
		}
	}

	/**
	 * Registers the output channels of an incoming task with the task manager.
	 * 
	 * @param eig
	 *        the output gate whose input channels should be registered
	 * @param channelGroup
	 *        the channel group which is requied by byte buffered channel to create checkpoints
	 * @throws ChannelSetupException
	 *         thrown if one of the channels could not be registered
	 */
	// This method is called by the RPC server thread
	private void registerOutputChannels(OutputGate<? extends Record> eog, ByteBufferedOutputChannelGroup channelGroup)
			throws ChannelSetupException {

		for (int i = 0; i < eog.getNumberOfOutputChannels(); i++) {

			AbstractOutputChannel<? extends Record> eoc = eog.getOutputChannel(i);
			if (eoc instanceof NetworkOutputChannel<?>) {
				this.byteBufferedChannelManager.registerByteBufferedOutputChannel(
					(AbstractByteBufferedOutputChannel<? extends Record>) eoc, channelGroup);
			} else if (eoc instanceof FileOutputChannel<?>) {
				this.byteBufferedChannelManager.registerByteBufferedOutputChannel(
					(AbstractByteBufferedOutputChannel<? extends Record>) eoc, channelGroup);
			} else if (eoc instanceof InMemoryOutputChannel<?>) {
				this.directChannelManager
					.registerDirectOutputChannel((AbstractDirectOutputChannel<? extends Record>) eoc);
			} else {
				throw new ChannelSetupException("Type of output channel " + eoc.getID() + " is not supported");
			}
		}
	}

	/**
	 * Unregisters the input channels of a finished or aborted task.
	 * 
	 * @param eig
	 *        the input gate whose input channels should be unregistered
	 */
	// This method is called by the respective task thread
	private void unregisterInputChannels(InputGate<? extends Record> eig) {

		for (int i = 0; i < eig.getNumberOfInputChannels(); i++) {
			AbstractInputChannel<? extends Record> eic = eig.getInputChannel(i);

			if (eic instanceof NetworkInputChannel<?>) {
				this.byteBufferedChannelManager
					.unregisterByteBufferedInputChannel((AbstractByteBufferedInputChannel<? extends Record>) eic);
			} else if (eic instanceof FileInputChannel<?>) {
				this.byteBufferedChannelManager
					.unregisterByteBufferedInputChannel((AbstractByteBufferedInputChannel<? extends Record>) eic);
			} else if (eic instanceof InMemoryInputChannel<?>) {
				this.directChannelManager
					.unregisterDirectInputChannel((AbstractDirectInputChannel<? extends Record>) eic);
			} else {
				LOG.error("Type of input channel " + eic.getID() + " is not supported");
			}
		}
	}

	/**
	 * Unregisters the output channels of a finished or aborted task.
	 * 
	 * @param eig
	 *        the output gate whose input channels should be unregistered
	 */
	// This method is called by the respective task thread
	private void unregisterOutputChannels(OutputGate<? extends Record> eog) {

		for (int i = 0; i < eog.getNumberOfOutputChannels(); i++) {
			AbstractOutputChannel<? extends Record> eoc = eog.getOutputChannel(i);

			if (eoc instanceof NetworkOutputChannel<?>) {
				this.byteBufferedChannelManager
					.unregisterByteBufferedOutputChannel((AbstractByteBufferedOutputChannel<? extends Record>) eoc);
			} else if (eoc instanceof FileOutputChannel<?>) {
				this.byteBufferedChannelManager
					.unregisterByteBufferedOutputChannel((AbstractByteBufferedOutputChannel<? extends Record>) eoc);
			} else if (eoc instanceof InMemoryOutputChannel<?>) {
				this.directChannelManager
					.unregisterDirectOutputChannel((AbstractDirectOutputChannel<? extends Record>) eoc);
			} else {
				LOG.error("Type of output channel " + eoc.getID() + " is not supported");
			}
		}

	}

	// This method is called by the TaskManagers main thread
	public void runIOLoop() {

		long interval = GlobalConfiguration.getInteger("taskmanager.setup.periodictaskinterval",
			DEFAULTPERIODICTASKSINTERVAL);

		while (!Thread.interrupted()) {

			// Sleep
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e1) {
				LOG.debug("Heartbeat thread was interrupted");
				break;
			}

			// Send heartbeat
			try {
				this.jobManager.sendHeartbeat(this.localInstanceConnectionInfo);
			} catch (IOException e) {
				LOG.debug("sending the heart beat caused on IO Exception");
			}

			// Check the status of the task threads to detect unexpected thread terminations
			checkTaskExecution();
		}

		// Shutdown the individual components of the task manager
		cleanUp();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TaskCancelResult cancelTask(ExecutionVertexID id) throws IOException {

		// Check if the task is registered with our task manager
		Environment tmpEnvironment;

		synchronized (this.runningTasks) {

			tmpEnvironment = this.runningTasks.get(id);

			if (tmpEnvironment == null) {
				final TaskCancelResult taskCancelResult = new TaskCancelResult(id, AbstractTaskResult.ReturnCode.ERROR);
				taskCancelResult.setDescription("No task with ID + " + id + " is currently running");
				return taskCancelResult;
			}
		}

		final Environment environment = tmpEnvironment;
		// Execute call in a new thread so IPC thread can return immediately
		Thread tmpThread = new Thread(new Runnable() {

			@Override
			public void run() {
				environment.cancelExecution();
			}
		});
		tmpThread.start();

		return new TaskCancelResult(id, AbstractTaskResult.ReturnCode.SUCCESS);
	}

	@Override
	public TaskSubmissionResult submitTask(ExecutionVertexID id, Configuration jobConfiguration, Environment ee)
			throws IOException {

		// Register task manager components in environment
		ee.setMemoryManager(memoryManager);
		ee.setIOManager(ioManager);

		// Register the task
		TaskSubmissionResult result = registerTask(id, jobConfiguration, ee);
		if (result != null) { // If result is non-null, an error occurred during task registration
			return result;
		}

		// Start execution
		LOG.debug("Starting execution of task with ID " + id);
		ee.startExecution();

		return new TaskSubmissionResult(id, AbstractTaskResult.ReturnCode.SUCCESS);
	}

	/**
	 * Registers an newly incoming task with the task manager.
	 * 
	 * @param id
	 *        the ID of the task to register
	 * @param jobConfiguration
	 *        the job configuration that has been attached to the original job graph
	 * @param ee
	 *        the environment of the task to register
	 * @return <code>null</code> if the registration has been successful or a {@link TaskSubmissionResult} containing
	 *         the error that occurred
	 */
	private TaskSubmissionResult registerTask(ExecutionVertexID id, Configuration jobConfiguration, Environment ee) {

		// Check if incoming task has an ID
		if (id == null) {
			LOG.debug("Incoming task has no ID");
			TaskSubmissionResult result = new TaskSubmissionResult(id, AbstractTaskResult.ReturnCode.ERROR);
			result.setDescription("Incoming task has no ID");
			return result;
		}

		// Check if the received environment is not null
		if (ee == null) {
			LOG.debug("Incoming environment is null");
			TaskSubmissionResult result = new TaskSubmissionResult(id, AbstractTaskResult.ReturnCode.ERROR);
			result.setDescription("Incoming task with ID " + id + " has no environment");
			return result;
		}

		// Check if the task is already running
		synchronized (this.runningTasks) {
			if (this.runningTasks.containsKey(id)) {
				LOG.debug("Task with ID " + id + " is already running");
				TaskSubmissionResult result = new TaskSubmissionResult(id, AbstractTaskResult.ReturnCode.ERROR);
				result.setDescription("Task with ID " + id + " is already running");
				return result;
			}
		}

		// Check if the task has unbound input/output gates
		if (ee.hasUnboundInputGates() || ee.hasUnboundOutputGates()) {
			LOG.debug("Task with ID " + id + " has unbound gates");
			TaskSubmissionResult result = new TaskSubmissionResult(id, AbstractTaskResult.ReturnCode.ERROR);
			result.setDescription("Task with ID " + id + " has unbound gates");
			return result;
		}

		// Create wrapper object and register it as an observer
		final EnvironmentWrapper wrapper = new EnvironmentWrapper(this, id, ee);
		ee.registerExecutionNotifiable(wrapper);

		boolean enableProfiling = false;
		if (this.profiler != null && jobConfiguration.getBoolean(ProfilingUtils.PROFILE_JOB_KEY, true)) {
			enableProfiling = true;
		}

		if (enableProfiling) {
			this.profiler.registerExecutionNotifiable(id, jobConfiguration, ee);
		}

		try {
			// Register input gates
			for (int i = 0; i < ee.getNumberOfInputGates(); i++) {
				registerInputChannels(ee.getInputGate(i));
				if (enableProfiling) {
					this.profiler.registerInputGateListener(id, jobConfiguration, ee.getInputGate(i));
				}
			}

			// Try to find common channel type among the output channels
			final ChannelType commonChannelType = hasCommonOutputChannelType(ee);
			final ByteBufferedOutputChannelGroup channelGroup = new ByteBufferedOutputChannelGroup(
				this.byteBufferedChannelManager, this.checkpointManager, commonChannelType, id);

			// Register output gates
			for (int i = 0; i < ee.getNumberOfOutputGates(); i++) {
				registerOutputChannels(ee.getOutputGate(i), channelGroup);
				if (enableProfiling) {
					this.profiler.registerOutputGateListener(id, jobConfiguration, ee.getOutputGate(i));
				}
			}
		} catch (ChannelSetupException e) {
			TaskSubmissionResult result = new TaskSubmissionResult(id, AbstractTaskResult.ReturnCode.ERROR);
			result.setDescription(e.getMessage());
			return result;
		}

		// The environment itself will put the task into the running task map

		return null;
	}

	/**
	 * Checks if all output channels of the given environment have a common channel type.
	 * 
	 * @param environment
	 *        the environment whose output channels are checked
	 * @return the common {@link ChannelType} or <code>null</code> if no common channel type exists
	 */
	private ChannelType hasCommonOutputChannelType(Environment environment) {

		ChannelType outputChannelType = null;

		for (int i = 0; i < environment.getNumberOfOutputGates(); i++) {

			final OutputGate<? extends Record> outputGate = environment.getOutputGate(i);

			for (int j = 0; j < outputGate.getNumberOfOutputChannels(); j++) {
				final AbstractOutputChannel<? extends Record> outputChannel = outputGate.getOutputChannel(j);

				if (outputChannelType == null) {
					outputChannelType = outputChannel.getType();
				} else {
					if (!outputChannelType.equals(outputChannel.getType())) {
						return null;
					}
				}
			}
		}

		return outputChannelType;
	}

	/**
	 * Unregisters a finished or aborted task.
	 * 
	 * @param id
	 *        the ID of the task to be unregistered
	 * @param environment
	 *        the {@link Environment} of the task to be unregistered
	 */
	private void unregisterTask(ExecutionVertexID id, Environment environment) {

		// Unregister channels
		for (int i = 0; i < environment.getNumberOfOutputGates(); i++) {
			unregisterOutputChannels(environment.getOutputGate(i));
		}
		if (this.profiler != null) {
			this.profiler.unregisterOutputGateListeners(id);
		}

		for (int i = 0; i < environment.getNumberOfInputGates(); i++) {
			unregisterInputChannels(environment.getInputGate(i));
		}
		if (this.profiler != null) {
			this.profiler.unregisterInputGateListeners(id);
		}

		if (this.profiler != null) {
			this.profiler.unregisterExecutionNotifiable(id);
		}

		// Check if there are still vertices running that belong to the same job
		int numberOfVerticesBelongingToThisJob = 0;
		synchronized (this.runningTasks) {
			final Iterator<Environment> iterator = this.runningTasks.values().iterator();
			while (iterator.hasNext()) {
				final Environment candidateEnvironment = iterator.next();
				if (environment.getJobID().equals(candidateEnvironment.getJobID())) {
					numberOfVerticesBelongingToThisJob++;
				}
			}
		}

		// If there are no other vertices belonging to the same job, we can unregister the job's class loader
		if (numberOfVerticesBelongingToThisJob == 0) {
			try {
				LibraryCacheManager.unregister(environment.getJobID());
			} catch (IOException e) {
				LOG.debug("Unregistering the job vertex ID " + id + " caused an IOException");
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public LibraryCacheProfileResponse getLibraryCacheProfile(LibraryCacheProfileRequest request) throws IOException {

		LibraryCacheProfileResponse response = new LibraryCacheProfileResponse(request);
		String[] requiredLibraries = request.getRequiredLibraries();

		for (int i = 0; i < requiredLibraries.length; i++) {
			if (LibraryCacheManager.contains(requiredLibraries[i]) == null)
				response.setCached(i, false);
			else
				response.setCached(i, true);
		}

		return response;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateLibraryCache(LibraryCacheUpdate update) throws IOException {

		// Nothing to to here
	}

	public void executionStateChanged(JobID jobID, ExecutionVertexID id, Environment environment,
			ExecutionState newExecutionState, String optionalDescription) {

		if (newExecutionState == ExecutionState.RUNNING) {
			// Mark task as running by putting it in the corresponding map
			synchronized (this.runningTasks) {
				this.runningTasks.put(id, environment);
			}
		}

		if (newExecutionState == ExecutionState.FINISHED || newExecutionState == ExecutionState.CANCELLED
			|| newExecutionState == ExecutionState.FAILED) {

			// In any of these states the task's thread will be terminated, so we remove the task from the running tasks
			// map
			synchronized (this.runningTasks) {
				this.runningTasks.remove(id);
			}

			// Unregister the task (free all buffers, remove all channels, task-specific class loaders, etc...
			unregisterTask(id, environment);
		}

		// Get lock on the jobManager object and propagate the state change
		synchronized (this.jobManager) {
			try {
				this.jobManager.updateTaskExecutionState(new TaskExecutionState(jobID, id, newExecutionState,
					optionalDescription));
			} catch (IOException e) {
				LOG.error("Transmitting the task execution result for ID " + id + " caused an IO exception");
			}
		}
	}

	/**
	 * Performs clean up operations on task manager exit
	 */
	public void cleanUp() {

		LOG.info("Cleaning up TaskManager");

		// Stop RPC proxy for the task manager
		RPC.stopProxy(this.jobManager);

		// Shut down the own RPC server
		this.taskManagerServer.stop();

		// Stop profiling if enabled
		if (this.profiler != null) {
			this.profiler.shutdown();
		}

		// Shut down the network channel manager
		this.byteBufferedChannelManager.shutdown();

		// Shut down the memory manager
		if (this.ioManager != null) {
			this.ioManager.shutdown();
			this.ioManager = null;
		}

		if (this.memoryManager != null) {
			this.memoryManager.shutdown();
			this.memoryManager = null;
		}
	}

	/**
	 * This method is periodically called by the framework to check
	 * the state of the task threads. If any task thread has unexpectedly
	 * switch to TERMINATED, this indicates that an {@link Error} has occurred
	 * during its execution.
	 */
	private void checkTaskExecution() {

		synchronized (this.runningTasks) {

			final Iterator<ExecutionVertexID> it = this.runningTasks.keySet().iterator();
			while (it.hasNext()) {
				final ExecutionVertexID executionVertexID = it.next();
				final Environment environment = this.runningTasks.get(executionVertexID);

				if (environment.getExecutingThread().getState() == Thread.State.TERMINATED) {
					// Remove entry from the running tasks map
					it.remove();
					environment.changeExecutionState(ExecutionState.FAILED, "Execution thread died unexpectedly");
				}
			}
		}
	}
}
