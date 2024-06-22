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

package eu.stratosphere.nephele.jobmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import eu.stratosphere.nephele.event.job.AbstractEvent;
import eu.stratosphere.nephele.event.job.EventList;
import eu.stratosphere.nephele.event.job.ExecutionStateChangeEvent;
import eu.stratosphere.nephele.event.job.JobEvent;
import eu.stratosphere.nephele.event.job.ManagementEvent;
import eu.stratosphere.nephele.event.job.NewJobEvent;
import eu.stratosphere.nephele.event.job.VertexEvent;
import eu.stratosphere.nephele.execution.Environment;
import eu.stratosphere.nephele.execution.ExecutionNotifiable;
import eu.stratosphere.nephele.execution.ExecutionState;
import eu.stratosphere.nephele.executiongraph.ExecutionGraph;
import eu.stratosphere.nephele.executiongraph.ExecutionGraphIterator;
import eu.stratosphere.nephele.executiongraph.ExecutionVertex;
import eu.stratosphere.nephele.executiongraph.ExecutionVertexID;
import eu.stratosphere.nephele.executiongraph.JobStatusListener;
import eu.stratosphere.nephele.jobgraph.JobID;
import eu.stratosphere.nephele.jobgraph.JobStatus;
import eu.stratosphere.nephele.jobgraph.JobVertexID;
import eu.stratosphere.nephele.managementgraph.ManagementVertexID;
import eu.stratosphere.nephele.profiling.ProfilingNotifiable;
import eu.stratosphere.nephele.profiling.types.ProfilingEvent;

/**
 * The event collector collects events which occurred during the execution of a job and prepares them
 * for being fetched by a client. The collected events have an expiration time. In a configurable interval
 * the event collector removes all intervals which are older than the interval.
 * 
 * @author warneke
 */
public class EventCollector extends TimerTask implements ProfilingNotifiable {

	/**
	 * The execution notitifiable wrapper is an auxiliary class. It is required
	 * because the job vertex ID and the management vertex ID cannot be accessed from
	 * the data provided by the <code>executionStateChanged</code> callback method.
	 * However, these IDs are needed to create the construct the {@link VertexEvent} and the
	 * {@link ExecutionStateChangeEvent}.
	 * 
	 * @author warneke
	 */
	private class ExecutionNotifiableWrapper implements ExecutionNotifiable {

		/**
		 * The event collector to forward the created event to.
		 */
		private final EventCollector eventCollector;

		/**
		 * The ID of the job vertex this wrapper object belongs to.
		 */
		private final JobVertexID jobVertexID;

		/**
		 * The ID of the management vertex this wrapper object belongs to.
		 */
		private final ManagementVertexID managementVertexID;

		/**
		 * Constructs a new execution notifiable object.
		 * 
		 * @param eventCollector
		 *        the event collector to forward the created event to
		 * @param jobVertexID
		 *        the ID of the job vertex this wrapper object belongs to
		 * @param executionVertexID
		 *        the ID of the execution vertex this wrapper object belongs to
		 */
		public ExecutionNotifiableWrapper(EventCollector eventCollector, JobVertexID jobVertexID,
				ExecutionVertexID executionVertexID) {
			this.eventCollector = eventCollector;
			this.jobVertexID = jobVertexID;
			this.managementVertexID = executionVertexID.toManagementVertexID();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void executionStateChanged(Environment ee, ExecutionState newExecutionState, String optionalMessage) {

			final long timestamp = System.currentTimeMillis();

			// Create a new vertex event
			final VertexEvent vertexEvent = new VertexEvent(timestamp, this.jobVertexID, ee.getTaskName(), ee
				.getCurrentNumberOfSubtasks(), ee.getIndexInSubtaskGroup(), newExecutionState, optionalMessage);

			this.eventCollector.addEvent(ee.getJobID(), vertexEvent);

			final ExecutionStateChangeEvent executionStateChangeEvent = new ExecutionStateChangeEvent(timestamp,
				this.managementVertexID, newExecutionState);

			this.eventCollector.addEvent(ee.getJobID(), executionStateChangeEvent);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void userThreadFinished(Environment ee, Thread userThread) {
			// Nothing to do here
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void userThreadStarted(Environment ee, Thread userThread) {
			// Nothing to do here
		}
	}

	/**
	 * The job status listener wrapper is an auxiliary class. It is required
	 * because the job name cannot be accessed from the data provided by the <code>jobStatusHasChanged</code> callback
	 * method. However, this job name
	 * is needed to create the construct the {@link NewJobEvent}.
	 * 
	 * @author warneke
	 */
	private class JobStatusListenerWrapper implements JobStatusListener {

		/**
		 * The event collector to forward the created event to.
		 */
		private final EventCollector eventCollector;

		/**
		 * The name of the job this wrapper has been created for.
		 */
		private final String jobName;

		/**
		 * <code>true</code> if profiling events are collected for the job, <code>false</code> otherwise.
		 */
		private final boolean isProfilingAvailable;

		/**
		 * Constructs a new job status listener wrapper.
		 * 
		 * @param eventCollector
		 *        the event collector to forward the events to
		 * @param jobName
		 *        the name of the job
		 * @param isProfilingAvailable
		 *        <code>true</code> if profiling events are collected for the job, <code>false</code> otherwise
		 */
		public JobStatusListenerWrapper(EventCollector eventCollector, String jobName, boolean isProfilingAvailable) {
			this.eventCollector = eventCollector;
			this.jobName = jobName;
			this.isProfilingAvailable = isProfilingAvailable;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void jobStatusHasChanged(JobID jobID, JobStatus newJobStatus) {

			if (newJobStatus == JobStatus.SCHEDULED) {
				this.eventCollector.createRunningJobEvent(jobID, this.jobName, this.isProfilingAvailable);
			}

			if (newJobStatus == JobStatus.FAILED || newJobStatus == JobStatus.CANCELLED
				|| newJobStatus == JobStatus.FINISHED) {
				this.eventCollector.removeRunningJobEvent(jobID);
			}

			this.eventCollector.addEvent(jobID, new JobEvent(System.currentTimeMillis(), newJobStatus));
		}

	}

	private final long TIMERTASKINTERVAL;

	/**
	 * The map which stores all collected events until they are either
	 * fetched by the client or discarded.
	 */
	private final Map<JobID, List<AbstractEvent>> collectedEvents = new HashMap<JobID, List<AbstractEvent>>();

	/**
	 * A list of events which point to currently running jobs.
	 */
	private final Map<JobID, NewJobEvent> runningJobs = new HashMap<JobID, NewJobEvent>();

	/**
	 * The timer used to trigger the cleanup routine.
	 */
	private final Timer timer;

	/**
	 * Constructs a new event collector and starts
	 * its background cleanup routine.
	 * 
	 * @param clientQueryInterval
	 *        the interval with which clients query for events
	 */
	public EventCollector(int clientQueryInterval) {

		this.TIMERTASKINTERVAL = clientQueryInterval * 1000 * 2; // Double the interval, clients will take care of
		// duplicate notifications

		timer = new Timer();
		timer.schedule(this, TIMERTASKINTERVAL, TIMERTASKINTERVAL);
	}

	public void getEventsForJob(JobID jobID, EventList<AbstractEvent> eventList, boolean includeManagementEvents) {

		synchronized (this.collectedEvents) {

			List<AbstractEvent> eventsForJob = this.collectedEvents.get(jobID);
			if (eventsForJob != null) {

				final Iterator<AbstractEvent> it = eventsForJob.iterator();
				while (it.hasNext()) {

					final AbstractEvent event = it.next();
					final boolean isManagementEvent = (event instanceof ManagementEvent);
					if (!isManagementEvent || includeManagementEvents) {
						eventList.addEvent(event);
					}
				}
			}
		}
	}

	public void getNewJobs(EventList<NewJobEvent> eventList) {

		synchronized (this.runningJobs) {

			final Iterator<NewJobEvent> it = this.runningJobs.values().iterator();
			while (it.hasNext()) {
				eventList.addEvent(it.next());
			}
		}
	}

	/**
	 * Stops the timer thread and cleans up the
	 * data structure which stores the collected events.
	 */
	public void shutdown() {

		// Clear event map
		synchronized (this.collectedEvents) {
			this.collectedEvents.clear();
		}

		// Cancel the timer for the cleanup routine
		this.timer.cancel();
	}

	/**
	 * Adds an event to the job's event list.
	 * 
	 * @param jobID
	 *        the ID of the job the event belongs to
	 * @param event
	 *        the event to be added to the job's event list
	 */
	private void addEvent(JobID jobID, AbstractEvent event) {

		synchronized (this.collectedEvents) {

			List<AbstractEvent> eventList = this.collectedEvents.get(jobID);
			if (eventList == null) {
				eventList = new ArrayList<AbstractEvent>();
				this.collectedEvents.put(jobID, eventList);
			}

			eventList.add(event);
		}
	}

	/**
	 * Creates a {@link NewJobEvent} and adds it to the list
	 * of currently running jobs.
	 * 
	 * @param jobID
	 *        the ID of the new job
	 * @param jobName
	 *        the name of the new job
	 * @param isProfilingEnabled
	 *        <code>true</code> if profiling events are collected for the job, <code>false</code> otherwise
	 */
	private void createRunningJobEvent(JobID jobID, String jobName, boolean isProfilingEnabled) {

		final NewJobEvent newJobEvent = new NewJobEvent(jobID, jobName, isProfilingEnabled, System.currentTimeMillis());

		synchronized (this.runningJobs) {
			this.runningJobs.put(jobID, newJobEvent);
		}
	}

	/**
	 * Removes the {@link NewJobEvent} from the list of
	 * currently running jobs.
	 * 
	 * @param jobID
	 *        the ID of the job to be removed
	 */
	private void removeRunningJobEvent(JobID jobID) {

		synchronized (this.runningJobs) {
			this.runningJobs.remove(jobID);
		}
	}

	/**
	 * Registers a job in form of its execution graph representation
	 * with the job progress collector. The collector will subscribe
	 * to state changes of the individual subtasks. A separate
	 * deregistration is not necessary since the job progress collector
	 * periodically discards outdated progress information.
	 * 
	 * @param executionGraph
	 *        the execution graph representing the job
	 * @param profilingAvailable
	 *        indicates if profiling data is available for this job
	 */
	public void registerJob(ExecutionGraph executionGraph, boolean profilingAvailable) {

		final Iterator<ExecutionVertex> it = new ExecutionGraphIterator(executionGraph, true);

		while (it.hasNext()) {

			final ExecutionVertex vertex = it.next();

			// Register the notifiable object which will pass state changes on to the collector
			vertex.getEnvironment().registerExecutionNotifiable(
				new ExecutionNotifiableWrapper(this, vertex.getGroupVertex().getJobVertexID(), vertex.getID()));
		}

		// Register one job status listener wrapper for the entire job
		executionGraph.registerJobStatusListener(new JobStatusListenerWrapper(this, executionGraph.getJobName(),
			profilingAvailable));
	}

	/**
	 * This method will periodically be called to clean up expired
	 * collected events.
	 */
	@Override
	public void run() {

		final long currentTime = System.currentTimeMillis();

		synchronized (this.collectedEvents) {

			final Iterator<JobID> it = this.collectedEvents.keySet().iterator();
			while (it.hasNext()) {

				final JobID jobID = it.next();
				final List<AbstractEvent> eventList = this.collectedEvents.get(jobID);
				if (eventList == null) {
					continue;
				}

				final Iterator<AbstractEvent> it2 = eventList.iterator();
				while (it2.hasNext()) {

					final AbstractEvent event = it2.next();
					// If the event is older than TIMERTASKINTERVAL, remove it
					if ((event.getTimestamp() + TIMERTASKINTERVAL) < currentTime) {
						it2.remove();
					}
				}

				if (eventList.isEmpty()) {
					it.remove();
				}
			}
		}
	}

	@Override
	public void processProfilingEvents(ProfilingEvent profilingEvent) {

		// Simply add profiling events to the job's event queue
		addEvent(profilingEvent.getJobID(), profilingEvent);
	}
}
