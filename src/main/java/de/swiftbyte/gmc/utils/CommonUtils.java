package de.swiftbyte.gmc.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class CommonUtils {

    public static boolean unzip(String zipFilePath, String destDir) {
        log.debug("Start unzipping '" + zipFilePath + "' to '" + destDir + "'...");
        File dir = new File(destDir);

        if(!dir.exists()) dir.mkdirs();
        FileInputStream fis;

        byte[] buffer = new byte[1024];
        try {
            fis = new FileInputStream(zipFilePath);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while(ze != null){
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);

                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                //close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            //close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
            return true;
        } catch (IOException e) {
            log.error("Unzipping failed: " + e.getMessage());
            return false;
        }

    }

}
