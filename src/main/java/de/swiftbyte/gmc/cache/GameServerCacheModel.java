package de.swiftbyte.gmc.cache;

import de.swiftbyte.gmc.common.packet.entity.ServerSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameServerCacheModel {


    private String friendlyName;
    private String installDir;

    private ServerSettings settings;
}