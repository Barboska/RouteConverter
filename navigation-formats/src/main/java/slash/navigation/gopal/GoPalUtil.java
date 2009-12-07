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

import slash.navigation.gopal.binding3.ObjectFactory;
import slash.navigation.gopal.binding3.Tour;
import slash.navigation.jaxb.JaxbUtils;

import javax.xml.bind.*;
import javax.xml.namespace.QName;
import java.io.*;

class GoPalUtil {
    private static final JAXBContext CONTEXT = JaxbUtils.newContext(ObjectFactory.class);

    private static final String GOPAL_NAMESPACE_URI = "";

    private static Unmarshaller newUnmarshaller() {
        return JaxbUtils.newUnmarshaller(CONTEXT);
    }

    private static Marshaller newMarshaller() {
        Marshaller marshaller = JaxbUtils.newMarshaller(CONTEXT);
        try {
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "ISO-8859-1");
        } catch (PropertyException e) {
            // intentionally left empty
        }
        return marshaller;
    }


    public static Tour unmarshal(InputStream in) throws JAXBException {
        Tour result = null;
        try {
            result = (Tour) newUnmarshaller().unmarshal(in);
        } catch (ClassCastException e) {
            throw new JAXBException("Parse error with " + result + ": " + e.getMessage(), e);
        }
        return result;
    }


    public static void marshal(Tour tour, OutputStream out) throws JAXBException {
        try {
            try {
                newMarshaller().marshal(new JAXBElement<Tour>(new QName(GOPAL_NAMESPACE_URI, "tour"), Tour.class, tour), out);
            }
            finally {
                out.flush();
                out.close();
            }
        } catch (IOException e) {
            throw new JAXBException("Error while marshalling: " + e.getMessage());
        }
    }
}