package de.swiftbyte.gmc.daemon.stomp.consumers.node;

import de.swiftbyte.gmc.common.packet.from.backend.node.NodeTaskCancelPacket;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.CustomLog;

@CustomLog
@StompPacketInfo(path = "/user/queue/node/task-cancel", packetClass = NodeTaskCancelPacket.class)
public class NodeTaskCancelConsumer implements StompPacketConsumer<NodeTaskCancelPacket> {

    @Override
    public void onReceive(NodeTaskCancelPacket packet) {

        log.debug("Received task cancel packet: {}", packet);

        if (TaskService.cancelTask(packet.getNodeTaskId())) {
            log.info("Task cancelled task with id: {}", packet.getNodeTaskId());
        } else {
            log.warn("Task cancellation failed task with id: {}", packet.getNodeTaskId());
        }

    }
}
