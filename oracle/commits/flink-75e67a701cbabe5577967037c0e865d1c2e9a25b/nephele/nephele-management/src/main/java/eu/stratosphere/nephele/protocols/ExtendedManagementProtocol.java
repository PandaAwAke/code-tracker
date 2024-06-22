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

package eu.stratosphere.nephele.protocols;

import java.io.IOException;

import eu.stratosphere.nephele.event.job.AbstractEvent;
import eu.stratosphere.nephele.event.job.EventList;
import eu.stratosphere.nephele.event.job.NewJobEvent;
import eu.stratosphere.nephele.jobgraph.JobID;
import eu.stratosphere.nephele.managementgraph.ManagementGraph;
import eu.stratosphere.nephele.managementgraph.ManagementVertexID;
import eu.stratosphere.nephele.protocols.JobManagementProtocol;
import eu.stratosphere.nephele.topology.NetworkTopology;
import eu.stratosphere.nephele.types.StringRecord;

/**
 * This protocol provides extended management capabilities beyond the
 * simple {@link JobManagementProtocol}. It can be used to retrieve
 * internal scheduling information, the network topology, or profiling
 * information about thread or instance utilization.
 * 
 * @author warneke
 */
public interface ExtendedManagementProtocol extends JobManagementProtocol {

	/**
	 * Retrieves the management graph for the job
	 * with the given ID.
	 * 
	 * @param jobID
	 *        the ID identifying the job
	 * @return the management graph for the job
	 * @thrown IOException thrown if an error occurs while retrieving the management graph
	 */
	ManagementGraph getManagementGraph(JobID jobID) throws IOException;

	/**
	 * Retrieves the current network topology for the job with
	 * the given ID.
	 * 
	 * @param jobID
	 *        the ID identifying the job
	 * @return the network topology for the job
	 * @thrown IOException thrown if an error occurs while retrieving the network topology
	 */
	NetworkTopology getNetworkTopology(JobID jobID) throws IOException;

	/**
	 * Retrieves a list of new jobs which arrived during the last query interval.
	 * 
	 * @return a (possibly) empty list of new jobs
	 * @throws IOException
	 *         thrown if an error occurs while retrieving the job list
	 */
	EventList<NewJobEvent> getNewJobs() throws IOException;

	/**
	 * Retrieves the collected events for the job with the given job ID.
	 * 
	 * @param jobID
	 *        the ID of the job to retrieve the events for
	 * @return a (possibly empty) list of events which occurred for that event and which
	 *         are not older than the query interval
	 * @throws IOException
	 *         thrown if an error occurs while retrieving the list of events
	 */
	EventList<AbstractEvent> getEvents(JobID jobID) throws IOException;

	/**
	 * Cancels the task with the given vertex ID.
	 * 
	 * @param jobID
	 *        the ID of the job the vertex to be canceled belongs to
	 * @param id
	 *        the vertex ID which identified the task be canceled.
	 * @throws IOException
	 *         thrown if an error occurs while transmitting the cancel request
	 */
	void cancelTask(JobID jobID, ManagementVertexID id) throws IOException;

	/**
	 * Kills the instance with the given name (i.e. shuts down its task manager).
	 * 
	 * @param instanceName
	 *        the name of the instance to be killed
	 * @throws IOException
	 *         thrown if an error occurs while transmitting the kill request
	 */
	void killInstance(StringRecord instanceName) throws IOException;
}
