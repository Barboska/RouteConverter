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

package slash.navigation.copilot;

import slash.common.io.CompactCalendar;
import slash.common.io.Transfer;
import slash.navigation.base.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static slash.common.io.Transfer.formatIntAsString;
import static slash.common.io.Transfer.trim;

/**
 * The base of all CoPilot formats.
 *
 * @author Christian Pesch
 */

public abstract class CoPilotFormat extends SimpleFormat<Wgs84Route> {
    protected static final String DATA_VERSION = "Data Version";
    private static final String START_TRIP = "Start Trip";
    private static final String END_TRIP = "End Trip";
    private static final String START_STOP = "Start Stop";
    private static final String END_STOP = "End Stop";
    private static final String START_STOP_OPT = "Start StopOpt";
    private static final String END_STOP_OPT = "End StopOpt";
    private static final String STOP = "Stop";

    protected static final char NAME_VALUE_SEPARATOR = '=';
    protected static final Pattern NAME_VALUE_PATTERN = Pattern.compile("(.+?)" + NAME_VALUE_SEPARATOR + "(.+|)");
    protected static final double INTEGER_FACTOR = 1000000.0;

    private static final String CREATOR = "Creator";
    private static final String LONGITUDE = "Longitude";
    private static final String LATITUDE = "Latitude";
    private static final String STATE = "State";
    private static final String ZIP = "Zip";
    private static final String CITY = "City";
    private static final String COUNTY = "County";
    private static final String ADDRESS = "Address"; // houseNumber<space>street
    private static final String SHOW = "Show";
    private static final String SEQUENCE = "Sequence";

    public String getExtension() {
        return ".trp";
    }

    public int getMaximumPositionCount() {
        return UNLIMITED_MAXIMUM_POSITION_COUNT;
    }

    @SuppressWarnings("unchecked")
    public <P extends BaseNavigationPosition> Wgs84Route createRoute(RouteCharacteristics characteristics, String name, List<P> positions) {
        return new Wgs84Route(this, characteristics, (List<Wgs84Position>) positions);
    }

    public BaseNavigationPosition getDuplicateFirstPosition(BaseRoute<BaseNavigationPosition, BaseNavigationFormat> route) {
        List<BaseNavigationPosition> positions = route.getPositions();
        BaseNavigationPosition first = positions.get(0);
        return new Wgs84Position(first.getLongitude(), first.getLatitude(), null, null, null, "Start:" + first.getComment());
    }

    public List<Wgs84Route> read(BufferedReader reader, CompactCalendar startDate, String encoding) throws IOException {
        List<Wgs84Position> positions = new ArrayList<Wgs84Position>();
        Map<String, String> map = new HashMap<String, String>();

        while (true) {
            String line = reader.readLine();
            if (line == null)
                break;
            if (trim(line) == null)
                continue;

            if (isDataVersion(line) || line.startsWith(END_TRIP) || line.startsWith(END_STOP_OPT)) {
            } else if (line.startsWith(START_TRIP) || line.startsWith(START_STOP) || line.startsWith(START_STOP_OPT)) {
                map.clear();
            } else if (line.startsWith(END_STOP)) {
                Wgs84Position position = parsePosition(map);
                positions.add(position);
                map.clear();
            } else if (isNameValue(line)) {
                String name = parseName(line);
                String value = parseValue(line);
                map.put(name, value);
            } else {
                return null;
            }
        }

        if (positions.size() > 0)
            return Arrays.asList(new Wgs84Route(this, RouteCharacteristics.Route, positions));
        else
            return null;
    }

    protected abstract boolean isDataVersion(String line);

    boolean isNameValue(String line) {
        Matcher matcher = NAME_VALUE_PATTERN.matcher(line);
        return matcher.matches();
    }

    private String parseName(String line) {
        Matcher matcher = NAME_VALUE_PATTERN.matcher(line);
        if (!matcher.matches())
            throw new IllegalArgumentException("'" + line + "' does not match");
        return matcher.group(1);
    }

    private String parseValue(String line) {
        Matcher matcher = NAME_VALUE_PATTERN.matcher(line);
        if (!matcher.matches())
            throw new IllegalArgumentException("'" + line + "' does not match");
        return matcher.group(2);
    }

    Wgs84Position parsePosition(Map<String, String> map) {
        Integer latitude = Transfer.parseInt(map.get(LATITUDE));
        Integer longitude = Transfer.parseInt(map.get(LONGITUDE));
        String state = trim(map.get(STATE));
        String zip = trim(map.get(ZIP));
        String city = trim(map.get(CITY));
        String county = trim(map.get(COUNTY));
        String address = trim(map.get(ADDRESS));
        String comment = (state != null ? state + (zip != null ? "-" : " ") : "") +
                (zip != null ? zip + " " : "") + (city != null ? city : "") +
                (county != null ? ", " + county : "") + (address != null ? ", " + address : "");
        return new Wgs84Position(longitude != null ? longitude / INTEGER_FACTOR : null,
                latitude != null ? latitude / INTEGER_FACTOR : null,
                null, null, null, trim(comment));
    }

    protected void writeHeader(Wgs84Route route, PrintWriter writer) {
        writer.println(START_TRIP + NAME_VALUE_SEPARATOR + route.getName());
        writer.println(CREATOR + NAME_VALUE_SEPARATOR + GENERATED_BY);
        writer.println("TollClosed=0");
        writer.println(END_TRIP);
        writer.println();
    }

    protected void writePositions(Wgs84Route route, PrintWriter writer, int startIndex, int endIndex) {
        List<Wgs84Position> positions = route.getPositions();
        for (int i = startIndex; i < endIndex; i++) {
            Wgs84Position position = positions.get(i);
            writer.println(START_STOP + NAME_VALUE_SEPARATOR + STOP + " " + i);
            String longitude = formatIntAsString(position.getLongitude() != null ? (int) (position.getLongitude() * INTEGER_FACTOR) : null);
            writer.println(LONGITUDE + NAME_VALUE_SEPARATOR + longitude);
            String latitude = formatIntAsString(position.getLatitude() != null ? (int) (position.getLatitude() * INTEGER_FACTOR) : null);
            writer.println(LATITUDE + NAME_VALUE_SEPARATOR + latitude);

            // TODO write decomposed comment
            // Name=
            // Address=11 Veilchenstrasse
            // City=Gladbeck
            // State=DE
            // County=Recklinghausen
            // Zip=47853

            String comment = position.getComment();
            int index = comment.indexOf(',');
            String city = index != -1 ? comment.substring(0, index) : comment;
            city = trim(city);
            String address = index != -1 ? comment.substring(index + 1) : comment;
            address = trim(address);
            boolean first = i == startIndex;
            boolean last = i == endIndex - 1;

            // only store address if there was a comma in the comment
            writer.println(ADDRESS + NAME_VALUE_SEPARATOR + (index != -1 ? address : ""));
            // otherwise store comment als city
            writer.println(CITY + NAME_VALUE_SEPARATOR + city);
            if (first || last)
                writer.println(SHOW + NAME_VALUE_SEPARATOR + "1"); // Target/Stop target
            else
                writer.println(SHOW + NAME_VALUE_SEPARATOR + "0"); // Waypoint
            writer.println(SEQUENCE + NAME_VALUE_SEPARATOR + i);
            writer.println(END_STOP);
            writer.println();

            writer.println(START_STOP_OPT + NAME_VALUE_SEPARATOR + STOP + " " + i);
            writer.println("Loaded=1");
            writer.println(END_STOP_OPT);
            writer.println();
        }
    }
}
