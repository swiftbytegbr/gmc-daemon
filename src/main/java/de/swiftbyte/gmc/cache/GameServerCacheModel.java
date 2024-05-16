package de.swiftbyte.gmc.cache;

import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.common.model.SettingProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameServerCacheModel {

    private String friendlyName;
    private GameType gameType;
    private String installDir;

    private SettingProfile settings;
}