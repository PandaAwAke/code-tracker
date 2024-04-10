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

package eu.stratosphere.nephele.instance.cloud;

import java.util.HashMap;
import java.util.Map;

import eu.stratosphere.nephele.instance.AbstractInstance;
import eu.stratosphere.nephele.instance.AllocatedResource;
import eu.stratosphere.nephele.instance.AllocationID;
import eu.stratosphere.nephele.instance.InstanceConnectionInfo;
import eu.stratosphere.nephele.instance.InstanceType;
import eu.stratosphere.nephele.io.channels.ChannelID;
import eu.stratosphere.nephele.topology.NetworkNode;
import eu.stratosphere.nephele.topology.NetworkTopology;

/**
 * A CloudInstance is a concrete implementation of the {@link Instance} interface for instances running
 * inside a cloud. Typically a cloud instance represents a virtual machine that can be controlled by
 * a cloud management system.
 * 
 * @author warneke
 */
public class CloudInstance extends AbstractInstance {

	/**
	 * The cached allocated resource object.
	 */
	private final AllocatedResource allocatedResource;

	/** The instance ID. */
	private final String instanceID;

	/** The owner of the instance. */
	private final String instanceOwner;

	/** The time the instance was allocated. */
	private final long allocationTime;

	/** Mapping channel IDs to filenames. */
	private final Map<ChannelID, String> filenames = new HashMap<ChannelID, String>();

	/** The last received heart beat. */
	private long lastReceivedHeartBeat = System.currentTimeMillis();

	/**
	 * Creates a new cloud instance.
	 * 
	 * @param instanceID
	 *        the instance ID assigned by the cloud management system
	 * @param type
	 *        the instance type
	 * @param instanceOwner
	 *        the owner of the instance
	 * @param instanceConnectionInfo
	 *        the information required to connect to the instance's task manager
	 * @param allocationTime
	 *        the time the instance was allocated
	 * @param parentNode
	 *        the parent node in the network topology
	 */
	public CloudInstance(String instanceID, InstanceType type, String instanceOwner,
			InstanceConnectionInfo instanceConnectionInfo, long allocationTime, NetworkNode parentNode,
			NetworkTopology networkTopology) {
		super(type, instanceConnectionInfo, parentNode, networkTopology);

		this.allocatedResource = new AllocatedResource(this, new AllocationID());

		this.instanceID = instanceID;
		this.instanceOwner = instanceOwner;
		this.allocationTime = allocationTime;
	}

	/**
	 * Returns the instance ID.
	 * 
	 * @return the instance ID
	 */
	public String getInstanceID() {
		return this.instanceID;
	}

	/**
	 * Returns the unique file name corresponding to the channel ID.
	 * If the channel ID does not have a file name, a random file name is generated.
	 * 
	 * @param id
	 *        the channel ID
	 * @return the unique file name
	 */
	@Override
	public String getUniqueFilename(ChannelID id) {

		if (this.filenames.containsKey(id)) {
			return this.filenames.get(id);
		}

		// Simple implementation to generate a random filename
		final char[] alphabet = { 'a', 'b', 'c', 'd', 'e', 'f', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

		String filename = "ne";

		for (int i = 0; i < 16; i++) {
			filename += alphabet[(int) (Math.random() * alphabet.length)];
		}

		filename += ".dat";
		// Store filename with id
		this.filenames.put(id, filename);

		return filename;
	}

	/**
	 * Returns the time of last received heart beat.
	 * 
	 * @return the time of last received heart beat
	 */
	public long getLastReceivedHeartBeat() {
		return this.lastReceivedHeartBeat;
	}

	/**
	 * Updates the time of last received heart beat to the current system time.
	 */
	public void updateLastReceivedHeartBeat() {
		this.lastReceivedHeartBeat = System.currentTimeMillis();
	}

	/**
	 * Returns the time the instance was allocated.
	 * 
	 * @return the time the instance was allocated
	 */
	public long getAllocationTime() {
		return this.allocationTime;
	}

	/**
	 * Returns the instance's owner.
	 * 
	 * @return the instance's owner
	 */
	public String getOwner() {
		return this.instanceOwner;
	}

	public AllocatedResource asAllocatedResource() {
		return this.allocatedResource;
	}
}
