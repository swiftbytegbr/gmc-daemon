package de.swiftbyte.gmc.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.common.model.SettingProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameServerCacheModel {

    private String friendlyName;
    private GameType gameType;
    private String installDir;

    private SettingProfile settings;
}