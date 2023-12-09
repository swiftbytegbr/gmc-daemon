package de.swiftbyte.gmc.cache;

import de.swiftbyte.gmc.packet.entity.NodeSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CacheModel {

    private String nodeName;
    private String teamName;
    private String serverPath;
    private NodeSettings.AutoBackup autoBackup;
    private String serverStopMessage;
    private String serverRestartMessage;

    private HashMap<String, GameServerCacheModel> gameServerCacheModelHashMap;
}
