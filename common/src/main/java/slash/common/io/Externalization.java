/*
    This file is part of RouteConverter.

    RouteConverter is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    RouteConverter is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with RouteConverter; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Copyright (C) 2007 Christian Pesch. All Rights Reserved.
*/

package slash.common.io;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Logger;

/**
 * Provides externalization functionality.
 *
 * @author Christian Pesch
 */

public class Externalization {
    private static final Logger log = Logger.getLogger(Externalization.class.getName());
    private static final File tempDirectory = new File(System.getProperty("java.io.tmpdir") + File.separator +
            "routeconverter" + (!Platform.isWindows() ? "-" + System.getProperty("user.name") : ""));

    public synchronized static File getTempDirectory() {
        if (!tempDirectory.exists())
            if (!tempDirectory.mkdirs())
                log.severe("Could not create temp directory " + tempDirectory);
        return tempDirectory;
    }

    private static File getTempFile(String fileName) {
        int index = fileName.lastIndexOf('/');
        if (index != -1)
            fileName = fileName.substring(index);
        return new File(getTempDirectory(), fileName);
    }

    private static long getLastModified(String fileName) throws IOException {
        long lastModified = Long.MAX_VALUE;
        URL url = Externalization.class.getClassLoader().getResource(fileName);
        if (url != null) {
            URLConnection connection = url.openConnection();
            lastModified = connection.getLastModified();
        }
        return lastModified;
    }

    public static File extractFile(String fileName) throws IOException {
        File target = getTempFile(fileName);
        long lastModifiedInClassPath = getLastModified(fileName);
        long lastModifiedTarget = target.lastModified();
        if (target.exists() && lastModifiedTarget / 1000 == lastModifiedInClassPath / 1000)
            return target;

        InputStream in = Externalization.class.getClassLoader().getResourceAsStream(fileName);
        if (in == null)
            return null;

        log.info("Extracting " + fileName + " to " + target);
        InputOutput.copy(in, new FileOutputStream(target));
        if (!target.setLastModified(lastModifiedInClassPath))
            log.warning("Cannot set last modified date for " + target);
        return target;
    }

    public static File extractFile(String fileName, String targetInfix, TokenResolver tokenResolver) throws IOException {
        InputStream in = Externalization.class.getClassLoader().getResourceAsStream(fileName);
        if (in == null)
            return null;

        File target = getTempFile(fileName.replace(".", "_" + targetInfix + "."));
        log.info("Extracting " + fileName + " to " + target);
        Reader reader = new TokenReplacingReader(new InputStreamReader(in), tokenResolver);
        FileWriter writer = new FileWriter(target);
        InputOutput.copy(reader, writer);
        return target;
    }
}
