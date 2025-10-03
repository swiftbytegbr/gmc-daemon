package de.swiftbyte.gmc.daemon.migration;

public interface MigrationScript {

    /**
     *
     * @return the version the migration script is migrating too
     */
    String run();

}
