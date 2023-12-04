package de.swiftbyte.gmc.cache;

import de.swiftbyte.gmc.packet.entity.Backup;
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

    private HashMap<String, GameServerCacheModel> gameServerCacheModelHashMap;
}
