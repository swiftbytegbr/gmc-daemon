package de.swiftbyte.gmc.daemon.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.common.model.SettingProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameServerCacheModel {

    private @Nullable String friendlyName;
    private @Nullable GameType gameType;
    private @Nullable String installDir;

    private @Nullable SettingProfile settings;

    public boolean validateModel() {
        return friendlyName != null && gameType != null && installDir != null && settings != null;
    }
}