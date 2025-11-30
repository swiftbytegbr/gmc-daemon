package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RollbackTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(NodeTask task, Object payload) {
        if (payload instanceof RollbackTaskPayload p) {
            BackupService.rollbackBackup(p.backupId(), p.rollbackPlayers());
            return;
        }
        throw new IllegalArgumentException("Expected RollbackTaskPayload for RollbackTaskConsumer");
    }

    public record RollbackTaskPayload(String backupId, boolean rollbackPlayers) {
    }
}
