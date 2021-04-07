package org.jigsawlabs.fabricwrapper.fabricinstaller.util;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Collectors;

public class Utils {

    public static Reader urlReader(URL url) throws IOException {
        return new InputStreamReader(url.openStream(), StandardCharsets.UTF_8);
    }

    public static String readTextFile(URL url) throws IOException {
        try (BufferedReader reader = new BufferedReader(urlReader(url))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }


    public static void downloadFile(URL url, Path path) throws IOException {
        Files.createDirectories(path.getParent());

        try (InputStream in = url.openStream()) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
