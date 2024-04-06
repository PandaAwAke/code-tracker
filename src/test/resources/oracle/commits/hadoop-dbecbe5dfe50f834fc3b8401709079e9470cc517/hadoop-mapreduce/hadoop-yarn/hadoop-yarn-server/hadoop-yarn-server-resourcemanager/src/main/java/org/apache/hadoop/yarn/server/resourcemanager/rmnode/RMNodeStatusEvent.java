package org.apache.hadoop.yarn.server.resourcemanager.rmnode;

import java.util.List;
import java.util.Map;

import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.NodeHealthStatus;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.server.api.records.HeartbeatResponse;

public class RMNodeStatusEvent extends RMNodeEvent {

  private final NodeHealthStatus nodeHealthStatus;
  private Map<ApplicationId, List<Container>> containersCollection;
  private final HeartbeatResponse latestResponse;

  public RMNodeStatusEvent(NodeId nodeId, NodeHealthStatus nodeHealthStatus,
      Map<ApplicationId, List<Container>> collection,
      HeartbeatResponse latestResponse) {
    super(nodeId, RMNodeEventType.STATUS_UPDATE);
    this.nodeHealthStatus = nodeHealthStatus;
    this.containersCollection = collection;
    this.latestResponse = latestResponse;
  }

  public NodeHealthStatus getNodeHealthStatus() {
    return this.nodeHealthStatus;
  }

  public Map<ApplicationId, List<Container>> getContainersCollection() {
    return this.containersCollection;
  }

  public HeartbeatResponse getLatestResponse() {
    return this.latestResponse;
  }
}
