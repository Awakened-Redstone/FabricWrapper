package org.jigsawlabs.fabricwrapper.fabricloader.util;

import java.io.File;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class UrlUtil {
    private UrlUtil() {

    }

    public static URL getSource(String filename, URL resourceURL) throws UrlConversionException {
        URL codeSourceURL;

        try {
            URLConnection connection = resourceURL.openConnection();
            if (connection instanceof JarURLConnection) {
                codeSourceURL = ((JarURLConnection) connection).getJarFileURL();
            } else {
                String path = resourceURL.getPath();

                if (path.endsWith(filename)) {
                    codeSourceURL = new URL(resourceURL.getProtocol(), resourceURL.getHost(), resourceURL.getPort(), path.substring(0, path.length() - filename.length()));
                } else {
                    throw new UrlConversionException("Could not figure out code source for file '" + filename + "' and URL '" + resourceURL + "'!");
                }
            }
        } catch (Exception e) {
            throw new UrlConversionException(e);
        }

        return codeSourceURL;
    }

    public static File asFile(URL url) throws UrlConversionException {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new UrlConversionException(e);
        }
    }

    public static Path asPath(URL url) throws UrlConversionException {
        if (url.getProtocol().equals("file")) {
            // TODO: Is this required?
            return asFile(url).toPath();
        } else {
            try {
                return Paths.get(url.toURI());
            } catch (URISyntaxException e) {
                throw new UrlConversionException(e);
            }
        }
    }

    public static URL asUrl(File file) throws UrlConversionException {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new UrlConversionException(e);
        }
    }

    public static URL asUrl(Path path) throws UrlConversionException {
        try {
            return new URL(null, path.toUri().toString());
        } catch (MalformedURLException e) {
            throw new UrlConversionException(e);
        }
    }
}
