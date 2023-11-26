package de.swiftbyte.gmc.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameServerCacheModel {


    private String friendlyName;
    private String installDir;

    private int gamePort;
    private int rawPort;
    private int queryPort;
    private int rconPort;
    private boolean isAutoRestartEnabled;
    private String rconPassword;

    private List<String> startPreArguments;
    private List<String> startPostArguments1;
    private List<String> startPostArguments2;
    private String map;
}