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

package slash.navigation.bcr;

import slash.navigation.NavigationTestCase;

import java.io.*;
import java.util.Arrays;
import java.util.List;

public class MTP0607FormatTest extends NavigationTestCase {
    MTP0607Format format = new MTP0607Format();
    BcrRoute route = new BcrRoute(format, "RouteName", Arrays.asList("Description1", "Description2"), Arrays.asList(new BcrPosition(1, 2, 3, "Start"), new BcrPosition(3, 4, 5, "End")));

    public void testIsSectionTitle() {
        assertTrue(format.isSectionTitle("[CLIENT]"));
        assertTrue(format.isSectionTitle("[COORDINATES]"));
        assertTrue(format.isSectionTitle("[DESCRIPTION]"));
        assertTrue(format.isSectionTitle("[ROUTE]"));

        assertFalse(format.isSectionTitle(" [COORDINATES]"));
        assertFalse(format.isSectionTitle("[DESCRIPTION] "));
        assertFalse(format.isSectionTitle(" [ROUTE] "));
        assertFalse(format.isSectionTitle("[[ROUTE]"));
        assertFalse(format.isSectionTitle("ROUTE]"));
        assertFalse(format.isSectionTitle("[NEW]"));

        assertFalse(format.isSectionTitle("[Egal]"));
        assertFalse(format.isSectionTitle("[Symbol 1]"));
        assertFalse(format.isSectionTitle("[Symbol 12]"));
        assertFalse(format.isSectionTitle("[Symbol 123]"));
        assertFalse(format.isSectionTitle("[Symbol 1234]"));
        assertFalse(format.isSectionTitle("[Overlay]"));
        assertFalse(format.isSectionTitle("[MapLage]"));
    }

    public void testParsePositionWithStreet() {
        BcrPosition position = format.parsePosition("TOWN,210945415755", "1115508,7081108", "D 22081,Hamburg/Uhlenhorst,Finkenau,0,");
        assertEquals(210945415755L, position.getAltitude());
        assertEquals(1115508, position.getX());
        assertEquals(7081108, position.getY());
        assertEquals("D 22081", position.getZipCode());
        assertEquals("Hamburg/Uhlenhorst", position.getCity());
        assertEquals("Finkenau", position.getStreet());
        assertEquals("0", position.getType());
        assertFalse(position.isUnstructured());
    }

    public void testParsePositionFromMTP20082009() {
        BcrPosition position = format.parsePosition("TOWN,210945415755,1", "1115508,7081108", "D 22081,Hamburg/Uhlenhorst,Finkenau,0,");
        assertEquals(210945415755L, position.getAltitude());
        assertEquals(1115508, position.getX());
        assertEquals(7081108, position.getY());
        assertEquals("D 22081", position.getZipCode());
        assertEquals("Hamburg/Uhlenhorst", position.getCity());
        assertEquals("Finkenau", position.getStreet());
        assertEquals("0", position.getType());
        assertFalse(position.isUnstructured());
    }

    public void testParsePosition() {
        BcrPosition position = format.parsePosition("Standort,999999999", "1139093,7081574", "bei D 22885,Barsb�ttel/Stemwarde,,0,");
        assertEquals(999999999, position.getAltitude());
        assertEquals(1139093, position.getX());
        assertEquals(7081574, position.getY());
        assertEquals("bei D 22885", position.getZipCode());
        assertEquals("Barsb�ttel/Stemwarde", position.getCity());
        assertNull(position.getStreet());
        assertEquals("0", position.getType());
        assertFalse(position.isUnstructured());
    }

    public void testParsePositionFromMotorradTourenplaner() {
        BcrPosition position = format.parsePosition("Standort,999999999", "1115508,7081108", "Gro�ensee/Schwarzeka,,@,0,");
        assertNull(position.getZipCode());
        assertEquals("Gro�ensee/Schwarzeka", position.getCity());
        assertEquals(BcrPosition.STREET_DEFINES_CENTER_NAME, position.getStreet());
        assertEquals("0", position.getType());
        assertFalse(position.isUnstructured());
    }

    public void testParsePositionFromITNConv() {
        BcrPosition position = format.parsePosition("Standort,999999999", "1115508,7081108", "Hamburg/Uhlenhorst");
        assertEquals(999999999, position.getAltitude());
        assertEquals(1115508, position.getX());
        assertEquals(7081108, position.getY());
        assertNull(position.getZipCode());
        assertEquals("Hamburg/Uhlenhorst", position.getCity());
        assertNull(position.getStreet());
        assertNull(position.getType());
        assertTrue(position.isUnstructured());
    }

    public void testParseNegativePosition() {
        BcrPosition position = format.parsePosition("Standort,999999999", "-449242,6182322", "bei F 29400,Lampaul Guimiliau,@,0,");
        assertEquals("bei F 29400", position.getZipCode());
        assertEquals("Lampaul Guimiliau", position.getCity());
        assertEquals(BcrPosition.STREET_DEFINES_CENTER_NAME, position.getStreet());
        assertEquals("0", position.getType());
        assertFalse(position.isUnstructured());
        assertEquals(BcrPosition.NO_ALTITUDE_DEFINED, position.getAltitude());
        assertEquals(-449242, position.getX());
        assertEquals(6182322, position.getY());
    }

    public void testSetComment() {
        BcrPosition position = format.parsePosition("TOWN,210845415855", "2115508,9081108", null);
        assertEquals(210845415855L, position.getAltitude());
        assertEquals(2115508, position.getX());
        assertEquals(9081108, position.getY());
        assertNull(position.getZipCode());
        assertNull(position.getCity());
        assertNull(position.getStreet());
        assertNull(position.getType());
        assertNull(position.getComment());
        assertTrue(position.isUnstructured());
        position.setComment(null);
        assertNull(position.getComment());
    }

    public void testSetLongitudeAndLatitudeAndElevation() {
        BcrPosition position = format.parsePosition("TOWN,210945416161", "2115508,9081108", null);
        assertEquals(2115508, position.getX());
        assertEquals(9081108, position.getY());
        assertEquals(55.52, position.getElevation());
        position.setLongitude(19.02522);
        position.setLatitude(62.963395);
        position.setElevation(14.42);
        assertEquals(2115508, position.getX());
        assertEquals(9081108, position.getY());
        assertEquals(19.02522, position.getLongitude());
        assertEquals(62.96339, position.getLatitude());
        assertEquals(14.42, position.getElevation());
        position.setLongitude(null);
        position.setLatitude(null);
        position.setElevation(null);
        assertNull(position.getX());
        assertNull(position.getY());
        assertNull(position.getLongitude());
        assertNull(position.getLatitude());
        assertNull(position.getElevation());
    }

    public void testSetCommentForMTPFirstAndLastPosition() {
        BcrPosition position = new BcrPosition(1, 2, 3, ",Hamburg/Uhlenhorst,,0,");
        assertNull(position.getZipCode());
        assertEquals("Hamburg/Uhlenhorst", position.getCity());
        assertNull(position.getStreet());
        assertEquals("0", position.getType());
        assertFalse(position.isUnstructured());
    }

    public void testReadComment() throws IOException {
        StringWriter writer = new StringWriter();
        format.write(route, new PrintWriter(writer), 0, 2);
        List<BcrRoute> routes = format.read(new BufferedReader(new StringReader(writer.toString())), null, BcrFormat.DEFAULT_ENCODING);
        assertEquals(1, routes.size());
        BcrRoute route = routes.get(0);
        List<BcrPosition> positions = route.getPositions();
        assertEquals(2, positions.size());
        BcrPosition position1 = positions.get(0);
        assertEquals("Start", position1.getComment());
        BcrPosition position2 = positions.get(1);
        assertEquals("End", position2.getComment());
    }
    
    public void testWriteComment() {
        StringWriter writer = new StringWriter();
        format.write(route, new PrintWriter(writer), 0, 2);
        assertTrue(writer.toString().contains("STATION1=Start"));
        assertTrue(writer.toString().contains("STATION2=End"));
    }
}
