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

package slash.navigation.gopal;

import slash.common.io.CompactCalendar;
import slash.navigation.base.BaseNavigationPosition;
import slash.navigation.base.RouteCharacteristics;
import slash.navigation.base.XmlNavigationFormat;
import slash.navigation.gopal.binding3.ObjectFactory;
import slash.navigation.gopal.binding3.Tour;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Reads and writes GoPal Route 3 (.xml) files.
 *
 * @author Christian Pesch
 */

public class GoPal3RouteFormat extends XmlNavigationFormat<GoPal3Route> {
    private static final Preferences preferences = Preferences.userNodeForPackage(GoPal3RouteFormat.class);

    public String getExtension() {
        return ".xml";
    }

    public String getName() {
        return "GoPal Route 3 (*" + getExtension() + ")";
    }

    public int getMaximumPositionCount() {
        return UNLIMITED_MAXIMUM_POSITION_COUNT;
    }

    public boolean isSupportsMultipleRoutes() {
        return false;
    }

    public boolean isWritingRouteCharacteristics() {
        return false;
    }

    @SuppressWarnings("unchecked")
    public <P extends BaseNavigationPosition> GoPal3Route createRoute(RouteCharacteristics characteristics, String name, List<P> positions) {
        return new GoPal3Route(name, (List<GoPalPosition>) positions);
    }

    private GoPal3Route process(Tour tour) {
        List<GoPalPosition> positions = new ArrayList<GoPalPosition>();
        for (Tour.Dest dest : tour.getDest()) {
            Short country = dest.getCountry() != 0 ? dest.getCountry() : null;
            positions.add(new GoPalPosition(dest.getLongitude(), dest.getLatitude(), country, dest.getZip(), dest.getCity(), dest.getStreet(), dest.getHouse()));
        }
        return new GoPal3Route(null, tour.getOptions(), positions);
    }

    public List<GoPal3Route> read(InputStream source, CompactCalendar startDate) throws IOException {
        try {
            Tour tour = GoPalUtil.unmarshal3(source);
            return Arrays.asList(process(tour));
        } catch (JAXBException e) {
            return null;
        }
    }

    private Tour.Options createOptions(GoPal3Route route) {
        Tour.Options options = route.getOptions();
        if (options == null) {
            options = new ObjectFactory().createTourOptions();
            options.setType((short) preferences.getInt("type", 3)); // Fahrzeugtyp: 0=PKW 1=Fussgaenger 2=Fahrrad 3=Motorrad
            options.setMode((short) preferences.getInt("mode", 2)); // Art der Route: 0=kurz 1=schnell 2=Oekonomisch
            options.setFerries((short) preferences.getInt("ferries", 1)); // Faehren: 0=meiden 1=verwenden
            options.setMotorWays((short) preferences.getInt("motorWays", 0)); // Autobahn: 0=meiden 1=verwenden
            options.setTollRoad((short) preferences.getInt("tollRoad", 1)); // Mautstrassen: 0=meiden 1=verwenden
            options.setTunnels((short) preferences.getInt("tunnels", 1)); // Tunnel: 0=meiden 1=verwenden
            options.setTTIMode((short) preferences.getInt("ttiMode", 0)); // Stauumfahrung: 0=automatisch 1=manuell 2=keine
            options.setVehicleSpeedMotorway((short) preferences.getInt("vehicleSpeedMotorway", 33));
            options.setVehicleSpeedNonMotorway((short) preferences.getInt("vehicleSpeedNonMotorway", 27));
            options.setVehicleSpeedInPedestrianArea((short) preferences.getInt("vehicleSpeedInPedestrianArea", 2));
            options.setPedestrianSpeed((short) preferences.getInt("pedestrianSpeed", 1));
            options.setCyclistSpeed((short) preferences.getInt("cyclistSpeed", 4));
        }
        return options;
    }

    private Tour createGoPal(GoPal3Route route) {
        ObjectFactory objectFactory = new ObjectFactory();
        Tour tour = objectFactory.createTour();
        tour.setOptions(createOptions(route));
        for (GoPalPosition position : route.getPositions()) {
            Tour.Dest dest = objectFactory.createTourDest();
            if (position.getX() != null)
                dest.setLongitude(position.getX());
            if (position.getY() != null)
                dest.setLatitude(position.getY());
            dest.setCity(position.getCity());
            if (position.getCountry() != null)
                dest.setCountry(position.getCountry());
            if (position.getHouseNumber() != null)
                dest.setHouse(position.getHouseNumber());
            dest.setStreet(position.getStreet());
            dest.setZip(position.getZipCode());
            tour.getDest().add(dest);
        }
        return tour;
    }

    public void write(GoPal3Route route, OutputStream target, int startIndex, int endIndex) throws IOException {
        try {
            GoPalUtil.marshal3(createGoPal(route), target);
        } catch (JAXBException e) {
            throw new IllegalArgumentException(e);
        }
    }
}