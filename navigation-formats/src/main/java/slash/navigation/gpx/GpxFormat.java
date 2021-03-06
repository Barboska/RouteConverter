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

package slash.navigation.gpx;

import slash.navigation.base.BaseNavigationPosition;
import slash.navigation.base.MultipleRoutesFormat;
import slash.navigation.base.RouteCharacteristics;
import slash.navigation.base.XmlNavigationFormat;

import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static slash.common.io.Transfer.parseDouble;
import static slash.navigation.util.Conversion.msToKmh;

/**
 * The base of all GPS Exchange formats.
 *
 * @author Christian Pesch
 */

public abstract class GpxFormat extends XmlNavigationFormat<GpxRoute> implements MultipleRoutesFormat<GpxRoute> {
    private static final Preferences preferences = Preferences.userNodeForPackage(GpxFormat.class);
    static final Pattern TRIPMASTER_REASON_PATTERN = Pattern.compile("(Punkt|Richtung \\d+|Abstand \\d+|Dur. \\d+:\\d+:\\d+|Course \\d+|Dist. \\d+) (-|:) (.+)");
    private static final Pattern TRIPMASTER_SPEED_PATTERN = Pattern.compile("[^-\\d\\.]*([-\\d\\.]+)\\s*(K|k)m/h\\s*");
    private static final Pattern QSTARTZ_SPEED_PATTERN = Pattern.compile(".*Speed[^-\\d\\.]*([-\\d\\.]+)(K|k)m/h.*Course[^\\d\\.]*([\\d]+).*");

    public String getExtension() {
        return ".gpx";
    }

    public int getMaximumPositionCount() {
        return UNLIMITED_MAXIMUM_POSITION_COUNT;
    }

    public boolean isSupportsMultipleRoutes() {
        return true;
    }

    public boolean isWritingRouteCharacteristics() {
        return true; 
    }

    @SuppressWarnings({"unchecked"})
    public <P extends BaseNavigationPosition> GpxRoute createRoute(RouteCharacteristics characteristics, String name, List<P> positions) {
        return new GpxRoute(this, characteristics, name, null, (List<GpxPosition>) positions);
    }

    protected String asDescription(List<String> strings) {
        if (strings == null)
            return null;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < strings.size(); i++) {
            buffer.append(strings.get(i));
            if (i != strings.size() - 1)
                buffer.append(",\n");
        }
        return buffer.toString();
    }

    protected String asWayPointComment(String name, String description) {
        return asComment(name, description);
    }

    protected Double parseSpeed(String comment) {
        if (comment != null) {
            Matcher tripMasterPattern = TRIPMASTER_SPEED_PATTERN.matcher(comment);
            if (tripMasterPattern.matches())
                return parseDouble(tripMasterPattern.group(1));
            Matcher qstartzPattern = QSTARTZ_SPEED_PATTERN.matcher(comment);
            if (qstartzPattern.matches())
                return parseDouble(qstartzPattern.group(1));
        }
        return null;
    }

    protected Double parseHeading(String comment) {
        if (comment != null) {
            Matcher qstartzPattern = QSTARTZ_SPEED_PATTERN.matcher(comment);
            if (qstartzPattern.matches())
                return parseDouble(qstartzPattern.group(3));
        }
        return null;
    }

    protected Double asKmh(Double metersPerSecond) {
        if (metersPerSecond == null)
            return null;
        return msToKmh(metersPerSecond);
    }

    protected boolean isWriteAccuracy() {
        return preferences.getBoolean("writeAccuracy", true);
    }

    protected boolean isWriteElevation() {
        return preferences.getBoolean("writeElevation", true);
    }

    protected boolean isWriteHeading() {
        return preferences.getBoolean("writeHeading", true);
    }

    protected boolean isWriteName() {
        return preferences.getBoolean("writeName", true);
    }

    protected boolean isWriteSpeed() {
        return preferences.getBoolean("writeSpeed", true);
    }

    protected boolean isWriteTime() {
        return preferences.getBoolean("writeTime", true);
    }
}
