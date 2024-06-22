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

package eu.stratosphere.nephele.instance;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.nephele.jobgraph.JobID;
import eu.stratosphere.nephele.topology.NetworkTopology;

/**
 * In Nephele an instance manager maintains the set of available compute resources. It is responsible for allocating new
 * compute resources,
 * provisioning available compute resources to the JobManager and keeping track of the availability of the utilized
 * compute resources in order
 * to report unexpected resource outages.
 * 
 * @author warneke
 */
public interface InstanceManager {

	/**
	 * Requests an instance of the provided instance type from the instance manager.
	 * 
	 * @param jobID
	 *        the ID of the job this instance is requested for
	 * @param conf
	 *        a configuration object including additional request information (e.g. credentials)
	 * @param instanceType
	 *        the type of the requested instance
	 * @throws InstanceException
	 *         thrown if an error occurs during the instance request
	 */
	void requestInstance(JobID jobID, Configuration conf, InstanceType instanceType) throws InstanceException;

	/**
	 * Releases an allocated resource from a job.
	 * 
	 * @param jobID
	 *        the ID of the job the instance has been used for
	 * @param conf
	 *        a configuration object including additional release information (e.g. credentials)
	 * @param allocatedResource
	 *        the allocated resource to be released
	 * @throws InstanceException
	 *         thrown if an error occurs during the release process
	 */
	void releaseAllocatedResource(JobID jobID, Configuration conf, AllocatedResource allocatedResource)
			throws InstanceException;

	/**
	 * Suggests a suitable instance type according to the provided hardware characteristics.
	 * 
	 * @param minNumComputeUnits
	 *        the minimum number of compute units
	 * @param minNumCPUCores
	 *        the minimum number of CPU cores
	 * @param minMemorySize
	 *        the minimum number of main memory (in MB)
	 * @param minDiskCapacity
	 *        the minimum hard disk capacity (in GB)
	 * @param maxPricePerHour
	 *        the maximum price per hour for the instance
	 * @return the instance type matching the requested hardware profile best or <code>null</code> if no such instance
	 *         type is available
	 */
	InstanceType getSuitableInstanceType(int minNumComputeUnits, int minNumCPUCores, int minMemorySize,
			int minDiskCapacity, int maxPricePerHour);

	/**
	 * Reports a heart beat message of an instance.
	 * 
	 * @param instanceConnectionInfo
	 *        the {@link InstanceConnectionInfo} object attached to the heart beat message
	 */
	void reportHeartBeat(InstanceConnectionInfo instanceConnectionInfo);

	/**
	 * Translates the name of an instance type to the corresponding instance type object.
	 * 
	 * @param instanceTypeName
	 *        the name of the instance type
	 * @return the instance type object matching the name or <code>null</code> if no such instance type exists
	 */
	InstanceType getInstanceTypeByName(String instanceTypeName);

	/**
	 * Returns the default instance type used by the instance manager.
	 * 
	 * @return the default instance type
	 */
	InstanceType getDefaultInstanceType();

	/**
	 * Returns the network topology for the job with the given ID. The network topology
	 * for the job might only be an excerpt of the overall network topology. It only
	 * includes those instances as leaf nodes which are really allocated for the
	 * execution of the job.
	 * 
	 * @param jobID
	 *        the ID of the job to get the topology for
	 * @return the network topology for the job
	 */
	NetworkTopology getNetworkTopology(JobID jobID);

	/**
	 * Sets the {@link InstanceListener} object which is supposed to be
	 * notified about instance availability and deaths.
	 * 
	 * @param instanceListener
	 *        the instance listener to set for this instance manager
	 */
	void setInstanceListener(InstanceListener instanceListener);

	/**
	 * Shuts the instance manager down and stops all its internal processes.
	 */
	void shutdown();
}
