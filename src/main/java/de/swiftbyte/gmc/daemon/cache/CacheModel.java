package de.swiftbyte.gmc.daemon.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CacheModel {

    private String nodeName;
    private String teamName;
    private String serverPath;
    private boolean manageFirewallAutomatically;
    private boolean isAutoUpdateEnabled;

    private HashMap<String, GameServerCacheModel> gameServerCacheModelHashMap;
}
