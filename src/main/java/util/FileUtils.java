package util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {

    public static void mkDirIfNotExists(String dirName) {
        try {
            File directory = new File(dirName);
            if (!directory.exists()) {
                Files.createDirectories(Paths.get(dirName));
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error folder creation %s", dirName));
        }
    }
}
