package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@CustomLog
public class BackupTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(@NonNull NodeTask task, @Nullable Object payload) {
        if (payload instanceof BackupTaskPayload(boolean isAutoUpdate, String name)) {
            task.getTargetIds().forEach(serverId -> {
                GameServer server = GameServer.getServerById(serverId);
                if(server == null) throw new IllegalArgumentException("Server id not found");
                BackupService.backupServer(server, isAutoUpdate, name);
            });
            return;
        }

        throw new IllegalArgumentException("Unsupported payload for BackupTaskConsumer: " + (payload == null ? "null" : payload.getClass()));
    }

    public record BackupTaskPayload(boolean isAutoUpdate, @Nullable String name) {
    }
}
