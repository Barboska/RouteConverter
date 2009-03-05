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

package slash.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;
import java.io.InputStream;
import java.io.IOException;

/**
 * The base of all navigation formats.
 *
 * @author Christian Pesch
 */

public abstract class BaseNavigationFormat<R extends BaseRoute> implements NavigationFormat<R> {
    private static final Preferences preferences = Preferences.userNodeForPackage(BaseNavigationFormat.class);
    public static final String DEFAULT_ENCODING = "ISO8859-1";
    public static final String UTF8_ENCODING = "UTF-8";
    public static final String UTF16_ENCODING = "UTF-16";
    public static final String UTF16LE_ENCODING = "UTF-16LE";
    protected static final String GENERATED_BY = "Generated by Christian Pesch's RouteConverter. See http://www.routeconverter.de";
    protected static final int UNLIMITED_MAXIMUM_POSITION_COUNT = Integer.MAX_VALUE;

    protected List<String> asDescription(String string) {
        if (string == null || string.length() == 0)
            return null;
        List<String> strings = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(string, ",\n");
        while (tokenizer.hasMoreTokens()) {
            strings.add(tokenizer.nextToken().trim());
        }
        return strings;
    }

    private String trimLineFeeds(String string) {
        string = string.replace('\n', ' ');
        string = string.replace('\r', ' ');
        return string;
    }

    protected String asComment(String name, String description) {
        if (name == null && description == null)
            return null;
        if (description == null)
            return trimLineFeeds(name);
        if (name == null || description.startsWith(name))
            return trimLineFeeds(description);
        if (name.startsWith(description))
            return name;
        return trimLineFeeds(name + "; " + description);
    }


    public boolean isSupportsReading() {
        return true;
    }

    public boolean isSupportsWriting() {
        return true;
    }

    public int getMaximumFileNameLength() {
        return preferences.getInt("maximumFileNameLength", 64);
    }

    public abstract <P extends BaseNavigationPosition> R createRoute(RouteCharacteristics characteristics, String name, List<P> positions);

    public List<R> read(InputStream source) throws IOException {
        return read(source, null);
    }
}
