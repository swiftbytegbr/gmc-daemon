package de.swiftbyte.gmc.daemon.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CacheModel {

    private @Nullable String nodeName;
    private @Nullable String teamName;
    private @Nullable String serverPath;
    private @Nullable String defaultServerDirectory;
    private @Nullable String backupPath;
    private boolean manageFirewallAutomatically;
    private boolean isAutoUpdateEnabled;

    private @Nullable HashMap<@NonNull String, @Nullable GameServerCacheModel> gameServerCacheModelHashMap;
}
