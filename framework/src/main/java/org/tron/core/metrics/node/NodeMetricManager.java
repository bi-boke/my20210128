package org.bok.core.metrics.node;

import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.bok.common.backup.BackupManager;
import org.bok.common.parameter.CommonParameter;
import org.bok.core.ChainBaseManager;
import org.bok.core.config.args.Args;
import org.bok.program.Version;
import org.bok.protos.Protocol;


@Component
public class NodeMetricManager {

  @Autowired
  private ChainBaseManager chainBaseManager;

  @Autowired
  private BackupManager backupManager;


  /**
   * get node info.
   *
   * @return NodeInfo
   */
  public NodeInfo getNodeInfo() {
    NodeInfo nodeInfo = new NodeInfo();
    setNodeInfo(nodeInfo);
    return nodeInfo;
  }

  private void setNodeInfo(NodeInfo nodeInfo) {

    nodeInfo.setIp(Args.getInstance().getNodeExternalIp());

    ByteString witnessAddress = ByteString.copyFrom(Args.getLocalWitnesses()
        .getWitnessAccountAddress(CommonParameter.getInstance().isECKeyCryptoEngine()));
    if (chainBaseManager.getWitnessScheduleStore().getActiveWitnesses().contains(witnessAddress)) {
      nodeInfo.setNodeType(1);
    } else {
      nodeInfo.setNodeType(0);
    }

    nodeInfo.setVersion(Version.getVersion());
    if (backupManager.getStatus() == BackupManager.BackupStatusEnum.MASTER) {
      nodeInfo.setBackupStatus(1);
    } else {
      nodeInfo.setBackupStatus(0);
    }
  }

  public Protocol.MetricsInfo.NodeInfo getNodeProtoInfo() {
    Protocol.MetricsInfo.NodeInfo.Builder nodeInfo = Protocol.MetricsInfo.NodeInfo.newBuilder();
    NodeInfo node = getNodeInfo();
    nodeInfo.setIp(node.getIp());
    nodeInfo.setNodeType(node.getNodeType());
    nodeInfo.setVersion(node.getVersion());
    nodeInfo.setBackupStatus(node.getBackupStatus());
    return nodeInfo.build();
  }


}
