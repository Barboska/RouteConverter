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

package slash.navigation.catalog.domain;

import slash.navigation.gpx.binding11.GpxType;
import slash.navigation.gpx.binding11.RteType;

import java.io.IOException;
import java.net.URL;

/**
 * Represents a route on the server which is transferred via {@link RouteCatalog}
 * and represented with GPX documents.
 *
 * @author Christian Pesch
 */

public class Route {
    private final RouteCatalog routeCatalog;
    private final String url;
    private String name, creator, description;
    private boolean fromCategory = false;
    private GpxType gpx;


    public Route(RouteCatalog routeCatalog, String url) {
        this.routeCatalog = routeCatalog;
        this.url = url;
    }

    public Route(RouteCatalog routeCatalog, String url, String name, String creator, String description) {
        this.routeCatalog = routeCatalog;
        this.url = url;
        this.name = name;
        this.creator = creator;
        this.description = description;
        fromCategory = true;
    }

    private synchronized GpxType getGpx() throws IOException {
        if (gpx == null) {
            gpx = routeCatalog.fetchGpx(url);
        }
        return gpx;
    }

    private synchronized void invalidate() {
        gpx = null;
        name = null;
        creator = null;
        description = null;
        fromCategory = false;
    }

    private RteType getRte() throws IOException {
        return getGpx().getRte().get(0);
    }

    public synchronized String getName() throws IOException {
        if (fromCategory)
            return name;
        return getRte().getName();
    }

    public synchronized String getCreator() throws IOException {
        if (fromCategory)
            return creator;
        return getRte().getSrc();
    }

    public synchronized String getDescription() throws IOException {
        if (fromCategory)
            return description;
        return getRte().getDesc();
    }

    private String getRteLinkHref() throws IOException {
        return getRte().getLink().get(0).getHref();
    }

    public URL getUrl() throws IOException {
        return new URL(getRteLinkHref());
    }

    public void update(String categoryUrl, String description) throws IOException {
        routeCatalog.updateRoute(categoryUrl, url, description, getRteLinkHref());
        invalidate();
    }

    public void delete() throws IOException {
        routeCatalog.deleteRoute(url);
        routeCatalog.deleteFile(getRteLinkHref());
    }


    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Route route = (Route) o;

        return routeCatalog.equals(route.routeCatalog) && url.equals(route.url);
    }

    public int hashCode() {
        int result;
        result = routeCatalog.hashCode();
        result = 31 * result + url.hashCode();
        return result;
    }

    public String toString() {
        return super.toString() + "[url=" + url + ", name=" + name + ", creator=" + creator + ", description=" + description + ", fromCategory=" + fromCategory + "]";
    }
}
