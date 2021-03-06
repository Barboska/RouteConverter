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

package slash.navigation.converter.cmdline;

import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Formats log messages in a terse style.
 *
 * @author Christian Pesch
 */

public class TerseFormatter extends Formatter {
    public String format(LogRecord record) {
        StringBuilder buffer = new StringBuilder();
        if (record.getLevel().intValue() > Level.INFO.intValue()) 
            buffer.append("Error: ");
        buffer.append(record.getMessage());
        buffer.append('\n');
        return buffer.toString();
    }
}
