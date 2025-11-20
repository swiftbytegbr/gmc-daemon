package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackupTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(NodeTask task, Object payload) {

        if(!(payload instanceof BackupTaskPayload(boolean isAutoUpdate))) {
            throw new IllegalArgumentException("Expected BackupTaskPayload");
        }

        task.getTargetIds().forEach(id -> BackupService.backupServer(id, isAutoUpdate));
    }

    public record BackupTaskPayload(boolean isAutoUpdate) {}
}
