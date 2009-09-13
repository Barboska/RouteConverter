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

package slash.navigation.converter.gui.panels;

import slash.navigation.converter.gui.elevationview.ElevationView;
import slash.navigation.converter.gui.RouteConverter;

import javax.swing.*;
import java.awt.*;

import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;

/**
 * The analyse panel of the route converter user interface.
 *
 * @author Christian Pesch
 */

public class AnalysePanel {
    private static final GridConstraints ELEVATION_PANEL_CONSTRAINTS = new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            new Dimension(0, 0), new Dimension(0, 0), new Dimension(2000, 2640), 0, true);

    private JPanel analysePanel;

    public AnalysePanel() {
        initialize();
    }

    private void initialize() {
        final RouteConverter r = RouteConverter.getInstance();

        ElevationView elevationView = new ElevationView(r.getPositionsModel());
        analysePanel.add(elevationView.getComponent(), ELEVATION_PANEL_CONSTRAINTS);
    }

    public Component getRootComponent() {
        return analysePanel;
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        analysePanel = new JPanel();
        analysePanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return analysePanel;
    }
}