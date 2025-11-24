package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackupTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(NodeTask task, Object payload) {
        if (payload instanceof BackupTaskPayload p) {
            task.getTargetIds().forEach(serverId -> {
                GameServer server = GameServer.getServerById(serverId);
                BackupService.backupServer(server, p.isAutoUpdate(), p.name());
            });
            return;
        }

        throw new IllegalArgumentException("Unsupported payload for BackupTaskConsumer: " + (payload == null ? "null" : payload.getClass()));
    }

    public record BackupTaskPayload(boolean isAutoUpdate, String name) {
    }
}
