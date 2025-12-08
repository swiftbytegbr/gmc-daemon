package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@CustomLog
public class RollbackTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(@NonNull NodeTask task, @Nullable Object payload) {
        if (payload instanceof RollbackTaskPayload(String backupId, boolean rollbackPlayers)) {
            BackupService.rollbackBackup(backupId, rollbackPlayers);
            return;
        }
        throw new IllegalArgumentException("Expected RollbackTaskPayload for RollbackTaskConsumer");
    }

    public record RollbackTaskPayload(@NonNull String backupId, boolean rollbackPlayers) {
    }
}
