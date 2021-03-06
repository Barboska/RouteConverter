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

package slash.navigation.converter.gui.mapview;

import slash.common.io.CompactCalendar;
import slash.common.io.Transfer;
import slash.navigation.base.BaseNavigationPosition;
import slash.navigation.base.BaseRoute;
import slash.navigation.converter.gui.augment.PositionAugmenter;
import slash.navigation.converter.gui.models.CharacteristicsModel;
import slash.navigation.converter.gui.models.PositionColumns;
import slash.navigation.converter.gui.models.PositionsModel;
import slash.navigation.converter.gui.models.PositionsSelectionModel;
import slash.navigation.nmn.NavigatingPoiWarnerFormat;
import slash.navigation.util.Positions;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.lang.Thread.sleep;
import static java.util.Calendar.SECOND;
import static javax.swing.event.TableModelEvent.ALL_COLUMNS;
import static slash.common.io.CompactCalendar.fromCalendar;
import static slash.common.io.Transfer.ceiling;
import static slash.common.io.Transfer.isEmpty;
import static slash.common.io.Transfer.parseDouble;
import static slash.common.io.Transfer.parseInt;
import static slash.common.io.Transfer.trim;
import static slash.navigation.base.RouteCharacteristics.Route;
import static slash.navigation.base.RouteCharacteristics.Waypoints;
import static slash.navigation.converter.gui.models.PositionColumns.ELEVATION_COLUMN_INDEX;
import static slash.navigation.converter.gui.models.PositionColumns.LATITUDE_COLUMN_INDEX;
import static slash.navigation.converter.gui.models.PositionColumns.LONGITUDE_COLUMN_INDEX;
import static slash.navigation.converter.gui.models.PositionColumns.TIME_COLUMN_INDEX;
import static slash.navigation.util.Positions.asPosition;
import static slash.navigation.util.Positions.center;
import static slash.navigation.util.Positions.contains;
import static slash.navigation.util.Positions.northEast;
import static slash.navigation.util.Positions.southWest;

/**
 * Base implementation for a component that displays the positions of a position list on a map.
 *
 * @author Christian Pesch
 */

public abstract class BaseMapView implements MapView {
    protected static final Preferences preferences = Preferences.userNodeForPackage(MapView.class);
    protected static final Logger log = Logger.getLogger(MapView.class.getName());

    protected static final String MAP_TYPE_PREFERENCE = "mapType3";
    private static final String CLEAN_ELEVATION_ON_MOVE_PREFERENCE = "cleanElevationOnMove";
    private static final String CLEAN_TIME_ON_MOVE_PREFERENCE = "cleanTimeOnMove";
    private static final String COMPLEMENT_TIME_ON_MOVE_PREFERENCE = "complementTimeOnMove";

    private static final int MAXIMUM_POLYLINE_SEGMENT_LENGTH = preferences.getInt("maximumTrackSegmentLength3", 35);
    private static final int MAXIMUM_POLYLINE_POSITION_COUNT = preferences.getInt("maximumTrackPositionCount3", 50 * 35);
    private static final int MAXIMUM_DIRECTIONS_SEGMENT_LENGTH = preferences.getInt("maximumRouteSegmentLength3", 8);
    private static final int MAXIMUM_DIRECTIONS_POSITION_COUNT = preferences.getInt("maximumRoutePositionCount3", 30 * 8);
    private static final int MAXIMUM_MARKER_SEGMENT_LENGTH = preferences.getInt("maximumWaypointSegmentLength3", 10);
    private static final int MAXIMUM_MARKER_POSITION_COUNT = preferences.getInt("maximumWaypointPositionCount3", 50 * 10);
    private static final int MAXIMUM_SELECTION_COUNT = preferences.getInt("maximumSelectionCount3", 5 * 10);
    private static final int[] ZOOMLEVEL_SCALE = {
            400000000,
            200000000,
            100000000,
            50000000,
            25000000,
            12500000,
            6400000,
            3200000,
            1600000,
            800000,
            400000,
            200000,
            100000,
            50000,
            25000,
            12500,
            6400,
            3200
    };
    private static final int MAXIMUM_ZOOMLEVEL_FOR_SIGNIFICANCE_CALCULATION = 16;

    private PositionsModel positionsModel;
    private List<BaseNavigationPosition> positions;
    private PositionsSelectionModel positionsSelectionModel;
    private List<BaseNavigationPosition> lastSelectedPositions;
    private int[] selectedPositionIndices = new int[0];
    private BaseNavigationPosition center;

    private ServerSocket callbackListenerServerSocket;
    private Thread positionListUpdater, selectionUpdater, callbackListener, callbackPoller;

    protected final Object notificationMutex = new Object();
    protected boolean initialized = false;
    private boolean running = true, recenterAfterZooming, avoidHighways, avoidTolls,
            haveToInitializeMapOnFirstStart = true, haveToRepaintSelectionImmediately = false,
            haveToRepaintRouteImmediately = false, haveToRecenterMap = false,
            haveToUpdateRoute = false, haveToReplaceRoute = false,
            haveToRepaintSelection = false, ignoreNextZoomCallback = false;
    private TravelMode travelMode;
    private String routeUpdateReason = "?", selectionUpdateReason = "?";
    private final Map<Integer, BitSet> significantPositionCache = new HashMap<Integer, BitSet>(ZOOMLEVEL_SCALE.length);
    private int meters = 0, seconds = 0, lastZoomLevel = -1;
    private PositionAugmenter positionAugmenter;
    private ExecutorService executor = Executors.newCachedThreadPool();

    // initialization

    public void initialize(PositionsModel positionsModel,
                           PositionsSelectionModel positionsSelectionModel,
                           CharacteristicsModel characteristicsModel,
                           PositionAugmenter positionAugmenter,
                           boolean recenterAfterZooming,
                           TravelMode travelMode, boolean avoidHighways, boolean avoidTolls) {
        initializeBrowser();
        setModel(positionsModel, positionsSelectionModel, characteristicsModel);
        this.positionAugmenter = positionAugmenter;
        this.recenterAfterZooming = recenterAfterZooming;
        this.travelMode = travelMode;
        this.avoidHighways = avoidHighways;
        this.avoidTolls = avoidTolls;
    }

    protected abstract void initializeBrowser();

    protected void setModel(PositionsModel positionsModel,
                            PositionsSelectionModel positionsSelectionModel,
                            CharacteristicsModel characteristicsModel) {
        this.positionsModel = positionsModel;
        this.positionsSelectionModel = positionsSelectionModel;

        positionsModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                boolean insertOrDelete = e.getType() == TableModelEvent.INSERT || e.getType() == TableModelEvent.DELETE;
                boolean allRowsChanged = e.getFirstRow() == 0 && e.getLastRow() == Integer.MAX_VALUE;
                // used to be limited to single rows which did work reliably but with usabilty problems
                // if (e.getFirstRow() == e.getLastRow() && insertOrDelete)
                if (!allRowsChanged && insertOrDelete)
                    updateRouteButDontRecenter();
                else {
                    // ignored updates on columns not displayed
                    if (e.getType() == TableModelEvent.UPDATE &&
                            !(e.getColumn() == PositionColumns.DESCRIPTION_COLUMN_INDEX ||
                                    e.getColumn() == LONGITUDE_COLUMN_INDEX ||
                                    e.getColumn() == LATITUDE_COLUMN_INDEX ||
                                    e.getColumn() == ALL_COLUMNS))
                        return;
                    update(allRowsChanged || insertOrDelete);
                }
                // update position marker on updates of longitude and latitude
                if (e.getType() == TableModelEvent.UPDATE &&
                        (e.getColumn() == LONGITUDE_COLUMN_INDEX ||
                                e.getColumn() == LATITUDE_COLUMN_INDEX ||
                                e.getColumn() == ALL_COLUMNS)) {
                    for (int selectedPositionIndex : selectedPositionIndices) {
                        if (selectedPositionIndex >= e.getFirstRow() && selectedPositionIndex <= e.getLastRow()) {
                            updateSelection();
                            break;
                        }
                    }
                }
            }
        });
        characteristicsModel.addListDataListener(new ListDataListener() {
            public void intervalAdded(ListDataEvent e) {
            }

            public void intervalRemoved(ListDataEvent e) {
            }

            public void contentsChanged(ListDataEvent e) {
                // ignore events following setRoute()
                if (e.getType() == ListDataEvent.CONTENTS_CHANGED && e.getIndex0() == CharacteristicsModel.IGNORE && e.getIndex1() == CharacteristicsModel.IGNORE)
                    return;
                updateRouteButDontRecenter();
            }
        });
    }

    private Throwable initializationCause = null;

    public Throwable getInitializationCause() {
        return initializationCause;
    }

    protected void setInitializationCause(Throwable initializationCause) {
        this.initializationCause = initializationCause;
    }

    public boolean isInitialized() {
        synchronized (this) {
            return initialized;
        }
    }

    protected void initializeBrowserInteraction() {
        getComponent().addComponentListener(new ComponentListener() {
            public void componentResized(ComponentEvent e) {
                resize();
            }

            public void componentMoved(ComponentEvent e) {
            }

            public void componentShown(ComponentEvent e) {
            }

            public void componentHidden(ComponentEvent e) {
            }
        });

        positionListUpdater = new Thread(new Runnable() {
            public void run() {
                long lastTime = 0;
                boolean recenter;
                while (true) {
                    List<BaseNavigationPosition> copiedPositions;
                    synchronized (notificationMutex) {
                        try {
                            notificationMutex.wait(1000);
                        } catch (InterruptedException e) {
                            // ignore this
                        }

                        if (!running)
                            return;
                        if (!hasPositions())
                            continue;
                        if (!isVisible())
                            continue;

                        /*
                           Update conditions:

                           - new route was loaded
                             - clear cache
                             - center map
                             - set zoom level according to route bounds
                             - repaint immediately
                           - user has moved position
                             - clear cache
                             - stay on current zoom level
                             - center map to position
                             - repaint
                           - user has removed position
                             - clear cache
                             - stay on current zoom level
                             - repaint
                           - user has zoomed map
                             - repaint if zooming into the map as it reveals more details
                           - user has moved map
                             - repaint if moved
                         */
                        long currentTime = System.currentTimeMillis();
                        if (haveToRepaintRouteImmediately ||
                                haveToReplaceRoute ||
                                (haveToUpdateRoute && (currentTime - lastTime > 5 * 1000))) {
                            log.fine("Woke up to update route: " + routeUpdateReason +
                                    " haveToUpdateRoute:" + haveToUpdateRoute +
                                    " haveToReplaceRoute:" + haveToReplaceRoute +
                                    " haveToRepaintRouteImmediately:" + haveToRepaintRouteImmediately);
                            copiedPositions = filterPositionsWithoutCoordinates(positions);
                            recenter = haveToReplaceRoute;
                            haveToUpdateRoute = false;
                            haveToReplaceRoute = false;
                            haveToRepaintRouteImmediately = false;
                        } else
                            continue;
                    }

                    setCenterOfMap(copiedPositions, recenter);
                    List<BaseNavigationPosition> render = reducePositions(copiedPositions, getMaximumPositionCount());
                    switch (positionsModel.getRoute().getCharacteristics()) {
                        case Route:
                            addDirectionsToMap(render);
                            break;
                        case Waypoints:
                            addMarkersToMap(render);
                            break;
                        default:
                            addPolylinesToMap(render);
                    }
                    log.info("Position list updated for " + render.size() + " positions of type " +
                            positionsModel.getRoute().getCharacteristics() + ", recentering: " + recenter);
                    lastTime = System.currentTimeMillis();
                }
            }
        }, "MapViewPositionListUpdater");
        positionListUpdater.start();

        selectionUpdater = new Thread(new Runnable() {
            public void run() {
                long lastTime = 0;
                while (true) {
                    int[] copiedSelectedPositionIndices;
                    List<BaseNavigationPosition> copiedPositions;
                    boolean recenter;
                    synchronized (notificationMutex) {
                        try {
                            notificationMutex.wait(100);
                        } catch (InterruptedException e) {
                            // ignore this
                        }

                        if (!running)
                            return;
                        if (!hasPositions())
                            continue;
                        if (!isVisible())
                            continue;

                        long currentTime = System.currentTimeMillis();
                        if (haveToRecenterMap || haveToRepaintSelectionImmediately ||
                                (haveToRepaintSelection && (currentTime - lastTime > 500))) {
                            log.fine("Woke up to update selected positions: " + selectionUpdateReason +
                                    " haveToRepaintSelection: " + haveToRepaintSelection +
                                    " haveToRepaintSelectionImmediately: " + haveToRepaintSelectionImmediately +
                                    " haveToRecenterMap: " + haveToRecenterMap);
                            recenter = haveToRecenterMap;
                            haveToRecenterMap = false;
                            haveToRepaintSelectionImmediately = false;
                            haveToRepaintSelection = false;
                            copiedSelectedPositionIndices = new int[selectedPositionIndices.length];
                            System.arraycopy(selectedPositionIndices, 0, copiedSelectedPositionIndices, 0, copiedSelectedPositionIndices.length);
                            copiedPositions = filterPositionsWithoutCoordinates(positions);
                        } else
                            continue;
                    }

                    List<BaseNavigationPosition> render = reducePositions(copiedPositions, copiedSelectedPositionIndices);
                    BaseNavigationPosition centerPosition = center != null ? center : render.size() > 0 ? render.get(0) : null;
                    selectPositions(render, recenter ? centerPosition : null);
                    log.info("Selected positions updated for " + render.size() + " positions, recentering: " + recenter + " to: " + centerPosition);
                    lastTime = System.currentTimeMillis();
                }
            }
        }, "MapViewSelectionUpdater");
        selectionUpdater.start();
    }

    private ServerSocket createCallbackListenerServerSocket() {
        try {
            ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            serverSocket.setSoTimeout(1000);
            int port = serverSocket.getLocalPort();
            log.info("Map listens on port " + port + " for callbacks");
            setCallbackListenerPort(port);
            return serverSocket;
        } catch (IOException e) {
            log.severe("Cannot open callback listener socket: " + e.getMessage());
            return null;
        }
    }

    protected void initializeCallbackListener() {
        callbackListenerServerSocket = createCallbackListenerServerSocket();
        if (callbackListenerServerSocket == null)
            return;

        callbackListener = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    synchronized (notificationMutex) {
                        if (!running) {
                            return;
                        }
                    }

                    Socket clientSocket = null;
                    BufferedReader is = null;
                    OutputStream os = null;
                    try {
                        clientSocket = callbackListenerServerSocket.accept();
                        is = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()), 64 * 1024);
                        os = clientSocket.getOutputStream();
                        processStream(is);
                    } catch (SocketTimeoutException e) {
                        // intentionally left empty
                    } catch (IOException e) {
                        synchronized (notificationMutex) {
                            if (running) {
                                log.severe("Cannot listen at callback listener socket: " + e.getMessage());
                            }
                        }
                        break;
                    } finally {
                        try {
                            if (is != null)
                                is.close();
                            if (os != null)
                                os.close();
                            if (clientSocket != null)
                                clientSocket.close();
                        } catch (IOException e) {
                            log.severe("Cannot close callback listener socket: " + e.getMessage());
                        }
                    }
                }
            }
        }, "MapViewCallbackListener");
        callbackListener.start();
    }

    protected void initializeCallbackPoller() {
        callbackPoller = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    synchronized (notificationMutex) {
                        if (!running) {
                            return;
                        }
                    }

                    String callbacks = Transfer.trim(executeScriptWithResult("return getCallbacks();"));
                    if (callbacks != null) {
                        String[] lines = callbacks.split("--");
                        for (String line : lines) {
                            processCallback(line);
                        }
                    }

                    try {
                        sleep(250);
                    } catch (InterruptedException e) {
                        // intentionally left empty
                    }
                }
            }
        }, "MapViewCallbackPoller");
        callbackPoller.start();
    }

    protected void checkLocalhostResolution() {
        try {
            InetAddress localhost = InetAddress.getByName("localhost");
            log.info("localhost is resolved to: " + localhost);
            String localhostName = localhost.getHostAddress();
            log.info("IP of localhost is: " + localhostName);
            if (!localhostName.equals("127.0.0.1"))
                throw new Exception("localhost does not resolve to 127.0.0.1");

            InetAddress ip = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
            log.info("127.0.0.1 is resolved to: " + ip);
            String ipName = localhost.getHostName();
            log.info("Name of 127.0.0.1 is: " + ipName);
            if (!ipName.equals("localhost"))
                throw new Exception("127.0.0.1 does not resolve to localhost");
        } catch (Exception e) {
            e.printStackTrace();
            final String message = "Probably faulty network setup: " + e.getMessage() + ".\nPlease check your network settings.";
            log.severe(message);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    JOptionPane.showMessageDialog(getComponent(), message, "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }
    }

    protected void checkCallback() {
        final Boolean[] receivedCallback = new Boolean[1];
        receivedCallback[0] = false;

        final MapViewListener callbackWaiter = new AbstractMapViewListener() {
            public void receivedCallback(int port) {
                synchronized (receivedCallback) {
                    receivedCallback[0] = true;
                    receivedCallback.notifyAll();
                }
            }
        };

        executor.execute(new Runnable() {
            public void run() {
                addMapViewListener(callbackWaiter);
                try {
                    executeScript("checkCallbackListenerPort();");

                    long start = System.currentTimeMillis();
                    while (true) {
                        synchronized (receivedCallback) {
                            if (receivedCallback[0]) {
                                long end = System.currentTimeMillis();
                                log.info("Received callback from browser after " + (end - start) + " milliseconds");
                                break;
                            }
                        }

                        if (start + 5000 < System.currentTimeMillis())
                            break;

                        try {
                            sleep(50);
                        } catch (InterruptedException e) {
                            // intentionally left empty
                        }
                    }

                    synchronized (receivedCallback) {
                        if (!receivedCallback[0]) {
                            setCallbackListenerPort(-1);
                            initializeCallbackPoller();
                            log.warning("Switched from callback to polling the browser");
                        }
                    }
                } finally {
                    removeMapViewListener(callbackWaiter);
                }
            }
        });
    }

    // disposal

    public void dispose() {
        long start = System.currentTimeMillis();
        synchronized (notificationMutex) {
            running = false;
            notificationMutex.notifyAll();
        }

        if (selectionUpdater != null) {
            try {
                selectionUpdater.join(500);
                selectionUpdater.interrupt();
                selectionUpdater.join();
            } catch (InterruptedException e) {
                // intentionally left empty
            }
            long end = System.currentTimeMillis();
            log.info("PositionUpdater stopped after " + (end - start) + " ms");
        }

        if (positionListUpdater != null) {
            try {
                positionListUpdater.join(500);
                positionListUpdater.interrupt();
                positionListUpdater.join();
            } catch (InterruptedException e) {
                // intentionally left empty
            }
            long end = System.currentTimeMillis();
            log.info("RouteUpdater stopped after " + (end - start) + " ms");
        }

        if (callbackListenerServerSocket != null) {
            try {
                callbackListenerServerSocket.close();
            } catch (IOException e) {
                log.warning("Cannot close callback listener socket:" + e.getMessage());
            }
            long end = System.currentTimeMillis();
            log.info("CallbackListenerSocket stopped after " + (end - start) + " ms");
        }

        if (callbackListener != null) {
            try {
                callbackListener.join();
            } catch (InterruptedException e) {
                // intentionally left empty
            }
            long end = System.currentTimeMillis();
            log.info("CallbackListener stopped after " + (end - start) + " ms");
        }

        if (callbackPoller != null) {
            try {
                if (callbackPoller.isAlive())
                    callbackPoller.join();
            } catch (InterruptedException e) {
                // intentionally left empty
            }
            long end = System.currentTimeMillis();
            log.info("CallbackPoller stopped after " + (end - start) + " ms");
        }

        executor.shutdownNow();
        long end = System.currentTimeMillis();
        log.info("Executors stopped after " + (end - start) + " ms");
    }

    // getter and setter

    private int getMaximumPositionCount() {
        switch (positionsModel.getRoute().getCharacteristics()) {
            case Route:
                return MAXIMUM_DIRECTIONS_POSITION_COUNT;
            case Waypoints:
                return MAXIMUM_MARKER_POSITION_COUNT;
            default:
                return MAXIMUM_POLYLINE_POSITION_COUNT;
        }
    }

    protected boolean isVisible() {
        return getComponent().getWidth() > 0;
    }

    private boolean hasPositions() {
        synchronized (notificationMutex) {
            return isInitialized() && positions != null;
        }
    }

    private void setCallbackListenerPort(int callbackListenerPort) {
        synchronized (notificationMutex) {
            executeScript("setCallbackListenerPort(" + callbackListenerPort + ")");
        }
    }

    public void setSelectedPositions(int[] selectedPositions, boolean replaceSelection) {
        synchronized (notificationMutex) {
            if (replaceSelection)
                this.selectedPositionIndices = selectedPositions;
            else {
                int[] indices = new int[selectedPositionIndices.length + selectedPositions.length];
                System.arraycopy(selectedPositionIndices, 0, indices, 0, selectedPositionIndices.length);
                System.arraycopy(selectedPositions, 0, indices, selectedPositionIndices.length, selectedPositions.length);
                this.selectedPositionIndices = indices;
            }
            haveToRecenterMap = true;
            haveToRepaintSelection = true;
            selectionUpdateReason = "selected " + selectedPositions.length + " positions; " +
                    "replacing selection: " + replaceSelection;
            notificationMutex.notifyAll();
        }
    }

    public void setRecenterAfterZooming(boolean recenterAfterZooming) {
        this.recenterAfterZooming = recenterAfterZooming;
    }

    public void setTravelMode(TravelMode travelMode) {
        this.travelMode = travelMode;
        if (positionsModel.getRoute().getCharacteristics() == Route)
            update(false);
    }

    public void setAvoidHighways(boolean avoidHighways) {
        this.avoidHighways = avoidHighways;
        if (positionsModel.getRoute().getCharacteristics() == Route)
            update(false);
    }

    public void setAvoidTolls(boolean avoidTolls) {
        this.avoidTolls = avoidTolls;
        if (positionsModel.getRoute().getCharacteristics() == Route)
            update(false);
    }

    public BaseNavigationPosition getCenter() {
        BaseNavigationPosition northEast = getNorthEastBounds();
        BaseNavigationPosition southWest = getSouthWestBounds();
        return northEast != null && southWest != null ? center(Arrays.asList(northEast, southWest)) : null;
    }

    public void setCenter(BaseNavigationPosition center) {
        this.center = center;
    }

    protected abstract BaseNavigationPosition getNorthEastBounds();

    protected abstract BaseNavigationPosition getSouthWestBounds();

    protected BaseNavigationPosition getBounds(String script) {
        String result = executeScriptWithResult(script);
        if (result == null)
            return null;

        StringTokenizer tokenizer = new StringTokenizer(result, ",");
        if (tokenizer.countTokens() != 2)
            return null;

        String latitude = tokenizer.nextToken();
        String longitude = tokenizer.nextToken();
        return asPosition(Double.parseDouble(longitude), Double.parseDouble(latitude));
    }

    // reduction of positions

    private BitSet calculateSignificantPositionsForZoomLevel(List<BaseNavigationPosition> positions, int zoomLevel) {
        BitSet significant = significantPositionCache.get(zoomLevel);
        if (significant == null) {
            significant = new BitSet(positions.size());

            if (zoomLevel <= MAXIMUM_ZOOMLEVEL_FOR_SIGNIFICANCE_CALCULATION) {
                double threshold = ZOOMLEVEL_SCALE[zoomLevel] / 2500.0;
                long start = System.currentTimeMillis();
                int[] significantPositions = Positions.getSignificantPositions(positions, threshold);
                long end = System.currentTimeMillis();
                log.info("zoomLevel " + zoomLevel + " < " + MAXIMUM_ZOOMLEVEL_FOR_SIGNIFICANCE_CALCULATION +
                        ": threshold " + threshold + ", significant positions " + significantPositions.length +
                        ", calculated in " + (end - start) + " milliseconds");
                for (int significantPosition : significantPositions)
                    significant.set(significantPosition);
            } else {
                // on all zoom level about MAXIMUM_ZOOMLEVEL_FOR_SIGNIFICANCE_CALCULATION
                // use all positions since the calculation is too expensive
                log.info("zoomLevel " + zoomLevel + " use all " + positions.size() + " positions");
                significant.set(0, positions.size(), true);
            }
            significantPositionCache.put(zoomLevel, significant);
        }
        return significant;
    }

    private List<BaseNavigationPosition> filterSignificantPositions(List<BaseNavigationPosition> positions) {
        long start = System.currentTimeMillis();

        int zoomLevel = getCurrentZoomLevel();
        BitSet pointStatus = calculateSignificantPositionsForZoomLevel(positions, zoomLevel);
        List<BaseNavigationPosition> result = new ArrayList<BaseNavigationPosition>();
        for (int i = 0; i < positions.size(); i++)
            if (pointStatus.get(i))
                result.add(positions.get(i));

        long end = System.currentTimeMillis();
        log.info(format("Filtered significant positions to reduce %d positions to %d in %d milliseconds",
                positions.size(), result.size(), (end - start)));
        return result;
    }

    private BaseNavigationPosition visibleNorthWest, visibleNorthEast, visibleSouthWest, visibleSouthEast;

    private List<BaseNavigationPosition> reducePositions(List<BaseNavigationPosition> positions, int maximumPositionCount) {
        if (positions.size() < 2)
            return positions;

        if (positions.size() > 50000)
            positions = filterEveryNthPosition(positions, 50000);

        // determine significant positions for this zoom level
        positions = filterSignificantPositions(positions);

        // reduce the number of significant positions by a visibility heuristic
        if (positions.size() > maximumPositionCount) {
            positions = filterVisiblePositions(positions, 2.5, false);
            visibleNorthEast = northEast(positions);
            visibleSouthWest = southWest(positions);
            visibleNorthWest = asPosition(visibleSouthWest.getLongitude(), visibleNorthEast.getLatitude());
            visibleSouthEast = asPosition(visibleNorthEast.getLongitude(), visibleSouthWest.getLatitude());
         } else {
            visibleNorthEast = null;
            visibleSouthWest = null;
            visibleNorthWest = null;
            visibleSouthEast = null;
        }

        // reduce the number of visible positions by a JS-stability heuristic
        if (positions.size() > maximumPositionCount)
            positions = filterEveryNthPosition(positions, maximumPositionCount);

        return positions;
    }

    private List<BaseNavigationPosition> reducePositions(List<BaseNavigationPosition> positions, int[] indices) {
        // reduce selected positions if they're not selected
        positions = filterSelectedPositions(positions, indices);

        // reduce the number of selected positions by a visibility heuristic
        if (positions.size() > MAXIMUM_SELECTION_COUNT)
            positions = filterVisiblePositions(positions, 1.25, true);

        // reduce the number of visible positions by a JS-stability heuristic
        if (positions.size() > MAXIMUM_SELECTION_COUNT)
            positions = filterEveryNthPosition(positions, MAXIMUM_SELECTION_COUNT);

        return positions;
    }

    protected abstract int getCurrentZoomLevel();

    private List<BaseNavigationPosition> filterVisiblePositions(List<BaseNavigationPosition> positions,
                                                                double factor, boolean includeFirstAndLastPosition) {
        long start = System.currentTimeMillis();

        BaseNavigationPosition northEast = getNorthEastBounds();
        BaseNavigationPosition southWest = getSouthWestBounds();
        if (northEast == null || southWest == null)
            return positions;

        // heuristic: increase bounds for visible positions to enable dragging the map
        // at the same zoom level, with a factor of 2 you hardly see the cropping even
        // with a small map and a big screen (meaning lots of space to drag the map)
        double width = (northEast.getLongitude() - southWest.getLongitude()) * factor;
        double height = (southWest.getLatitude() - northEast.getLatitude()) * factor;
        northEast.setLongitude(northEast.getLongitude() + width);
        northEast.setLatitude(northEast.getLatitude() - height);
        southWest.setLongitude(southWest.getLongitude() - width);
        southWest.setLatitude(southWest.getLatitude() + height);

        List<BaseNavigationPosition> result = new ArrayList<BaseNavigationPosition>();

        if (includeFirstAndLastPosition)
            result.add(positions.get(0));
        int firstIndex = includeFirstAndLastPosition ? 1 : 0;
        int lastIndex = includeFirstAndLastPosition ? positions.size() - 1 : positions.size();

        for (int i = firstIndex; i < lastIndex; i += 1) {
            BaseNavigationPosition position = positions.get(i);
            if (contains(northEast, southWest, position)) {
                result.add(position);
            }
        }

        if (includeFirstAndLastPosition)
            result.add(positions.get(positions.size() - 1));

        long end = System.currentTimeMillis();
        log.info(format("Filtered visible positions to reduce %d positions to %d in %d milliseconds",
                positions.size(), result.size(), (end - start)));
        return result;
    }

    private List<BaseNavigationPosition> filterEveryNthPosition(List<BaseNavigationPosition> positions, int maximumPositionCount) {
        long start = System.currentTimeMillis();

        List<BaseNavigationPosition> result = new ArrayList<BaseNavigationPosition>();
        result.add(positions.get(0));

        double increment = positions.size() / (double) maximumPositionCount;
        for (double i = 1; i < positions.size() - 1; i += increment) {
            result.add(positions.get((int) i));
        }

        result.add(positions.get(positions.size() - 1));

        long end = System.currentTimeMillis();
        log.info(format("Filtered every %fth position to reduce %d positions to %d in %d milliseconds",
                increment, positions.size(), result.size(), (end - start)));
        return result;
    }

    private List<BaseNavigationPosition> filterSelectedPositions(List<BaseNavigationPosition> positions, int[] selectedIndices) {
        long start = System.currentTimeMillis();

        List<BaseNavigationPosition> result = new ArrayList<BaseNavigationPosition>();
        for (int selectedIndex : selectedIndices) {
            if (selectedIndex >= positions.size())
                continue;
            result.add(positions.get(selectedIndex));
        }

        long end = System.currentTimeMillis();
        log.info(format("Filtered selected positions to reduce %d positions to %d in %d milliseconds",
                positions.size(), result.size(), (end - start)));
        return result;
    }

    private List<BaseNavigationPosition> filterPositionsWithoutCoordinates(List<BaseNavigationPosition> positions) {
        long start = System.currentTimeMillis();

        List<BaseNavigationPosition> result = new ArrayList<BaseNavigationPosition>();
        // copy to avoid ConcurrentModificationException
        positions = new ArrayList<BaseNavigationPosition>(positions);
        for (BaseNavigationPosition position : positions) {
            if (position.hasCoordinates())
                result.add(position);
        }

        long end = System.currentTimeMillis();
        log.info(format("Filtered positions without coordinates to reduce %d positions to %d in %d milliseconds",
                positions.size(), result.size(), (end - start)));
        return result;
    }

    // draw on map

    @SuppressWarnings({"unchecked"})
    protected void update(boolean haveToReplaceRoute) {
        if (!isInitialized() || !getComponent().isShowing())
            return;

        synchronized (notificationMutex) {
            this.positions = positionsModel.getRoute() != null ? positionsModel.getRoute().getPositions() : null;
            this.haveToUpdateRoute = true;
            routeUpdateReason = "update route";
            if (haveToReplaceRoute) {
                this.haveToReplaceRoute = true;
                routeUpdateReason = "replace route";
                this.haveToRepaintSelection = true;
                selectionUpdateReason = "replace route";
                significantPositionCache.clear();
            }
            notificationMutex.notifyAll();
        }
    }

    private void updateRouteButDontRecenter() {
        // repaint route immediately, simulates update(true) without recentering
        synchronized (notificationMutex) {
            haveToRepaintRouteImmediately = true;
            routeUpdateReason = "update route but don't recenter";
            significantPositionCache.clear();
            notificationMutex.notifyAll();
        }
    }

    private void updateSelection() {
        synchronized (notificationMutex) {
            haveToRepaintSelection = true;
            selectionUpdateReason = "update selection";
            notificationMutex.notifyAll();
        }
    }

    private void removeOverlays() {
        executeScript("removeOverlays();");
    }

    private void removeDirections() {
        executeScript("removeDirections();");
    }

    private void addDirectionsToMap(List<BaseNavigationPosition> positions) {
        meters = 0;
        seconds = 0;

        // avoid throwing javascript exceptions if there is nothing to direct
        if (positions.size() < 2) {
            addMarkersToMap(positions);
            return;
        }

        removeOverlays();

        int directionsCount = ceiling(positions.size(), MAXIMUM_DIRECTIONS_SEGMENT_LENGTH, false);
        for (int j = 0; j < directionsCount; j++) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("var latlngs").append(j).append(" = [");

            int start = Math.max(0, j * MAXIMUM_DIRECTIONS_SEGMENT_LENGTH - 1);
            int end = min(positions.size(), (j + 1) * MAXIMUM_DIRECTIONS_SEGMENT_LENGTH) - 1;
            for (int i = start + 1; i < end; i++) {
                BaseNavigationPosition position = positions.get(i);
                buffer.append("{location: new google.maps.LatLng(").append(position.getLatitude()).append(",").
                        append(position.getLongitude()).append(")}");
                if (i < end - 1)
                    buffer.append(",");
            }
            buffer.append("];\n");

            BaseNavigationPosition origin = positions.get(start);
            BaseNavigationPosition destination = positions.get(end);
            buffer.append("renderDirections({origin: new google.maps.LatLng(").append(origin.getLatitude()).
                    append(",").append(origin.getLongitude()).append("), ");
            buffer.append("destination: new google.maps.LatLng(").append(destination.getLatitude()).
                    append(",").append(destination.getLongitude()).append("), ");
            buffer.append("waypoints: latlngs").append(j).
                    append(", travelMode: google.maps.DirectionsTravelMode.").append(travelMode.toString().toUpperCase()).append(", ");
            buffer.append("avoidHighways: ").append(avoidHighways).append(", ");
            buffer.append("avoidTolls: ").append(avoidTolls).append(", ");
            buffer.append("region: '").append(Locale.getDefault().getCountry().toLowerCase()).append("'}, ");
            int startIndex = positionsModel.getIndex(origin);
            buffer.append(startIndex).append(", ");
            boolean lastSegment = (j == directionsCount - 1);
            buffer.append(lastSegment).append(");\n");
            if (lastSegment)
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                    // intentionally left empty
                }
            executeScript(buffer.toString());
        }
    }

    private void addPolylinesToMap(final List<BaseNavigationPosition> positions) {
        // display markers if there is no polyline to show
        if (positions.size() < 2) {
            addMarkersToMap(positions);
            return;
        }

        String color = preferences.get("trackLineColor", "0033FF");
        int width = preferences.getInt("trackLineWidth", 2);
        int polylinesCount = ceiling(positions.size(), MAXIMUM_POLYLINE_SEGMENT_LENGTH, true);
        for (int j = 0; j < polylinesCount; j++) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("var latlngs = [");
            int maximum = min(positions.size(), (j + 1) * MAXIMUM_POLYLINE_SEGMENT_LENGTH + 1);
            for (int i = j * MAXIMUM_POLYLINE_SEGMENT_LENGTH; i < maximum; i++) {
                BaseNavigationPosition position = positions.get(i);
                buffer.append("new google.maps.LatLng(").append(position.getLatitude()).append(",").
                        append(position.getLongitude()).append(")");
                if (i < maximum - 1)
                    buffer.append(",");
            }
            buffer.append("];\n");
            buffer.append("addOverlay(new google.maps.Polyline({path: latlngs, strokeColor: \"#").append(color).append("\", ").
                    append("strokeWeight: ").append(width).append(", strokeOpacity: 1, clickable: false}));");
            executeScript(buffer.toString());
        }
        removeOverlays();
        removeDirections();
    }

    private void addMarkersToMap(List<BaseNavigationPosition> positions) {
        int markersCount = ceiling(positions.size(), MAXIMUM_MARKER_SEGMENT_LENGTH, false);
        for (int j = 0; j < markersCount; j++) {
            StringBuilder buffer = new StringBuilder();
            int maximum = min(positions.size(), (j + 1) * MAXIMUM_MARKER_SEGMENT_LENGTH);
            for (int i = j * MAXIMUM_MARKER_SEGMENT_LENGTH; i < maximum; i++) {
                BaseNavigationPosition position = positions.get(i);
                buffer.append("addOverlay(new google.maps.Marker({position: new google.maps.LatLng(").
                        append(position.getLatitude()).append(",").append(position.getLongitude()).
                        append("), title: \"").append(escape(position.getComment())).
                        append("\", clickable: false, icon: markerIcon }));\n");
            }
            executeScript(buffer.toString());
        }
        removeOverlays();
        removeDirections();
    }

    private void setCenterOfMap(List<BaseNavigationPosition> positions, boolean recenter) {
        StringBuilder buffer = new StringBuilder();
        // if there are positions center on first start or if we have to recenter
        if (positions.size() > 0 && (haveToInitializeMapOnFirstStart || recenter)) {
            BaseNavigationPosition northEast = northEast(positions);
            BaseNavigationPosition southWest = southWest(positions);
            buffer.append("map.fitBounds(new google.maps.LatLngBounds(").
                    append("new google.maps.LatLng(").append(southWest.getLatitude()).append(",").append(southWest.getLongitude()).append("),").
                    append("new google.maps.LatLng(").append(northEast.getLatitude()).append(",").append(northEast.getLongitude()).append(")));\n");
            BaseNavigationPosition center = center(positions);
            buffer.append("map.setCenter(new google.maps.LatLng(").append(center.getLatitude()).append(",").
                    append(center.getLongitude()).append("));\n");
            ignoreNextZoomCallback = true;
        }
        executeScript(buffer.toString());
        haveToInitializeMapOnFirstStart = false;
    }

    private void selectPositions(List<BaseNavigationPosition> selectedPositions, BaseNavigationPosition center) {
        lastSelectedPositions = new ArrayList<BaseNavigationPosition>(selectedPositions);

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < selectedPositions.size(); i++) {
            BaseNavigationPosition selectedPosition = selectedPositions.get(i);
            buffer.append("addMarker(new google.maps.Marker({position: new google.maps.LatLng(").
                    append(selectedPosition.getLatitude()).append(",").append(selectedPosition.getLongitude()).
                    append("), title: \"").append(escape(selectedPosition.getComment())).
                    append("\", draggable: true, zIndex: 1000}), ").append(i).append(");\n");
        }

        if (center != null)
            buffer.append("centerMap(new google.maps.LatLng(").append(center.getLatitude()).append(",").
                    append(center.getLongitude()).append("));\n");
        buffer.append("removeMarkers();");
        executeScript(buffer.toString());
    }

    private final Map<Integer, List<BaseNavigationPosition>> insertWaypointsQueue = new HashMap<Integer, List<BaseNavigationPosition>>();

    private void insertWaypoints(final String mode, int[] startPositions) {
        final Map<Integer, List<BaseNavigationPosition>> addToQueue = new HashMap<Integer, List<BaseNavigationPosition>>();
        Random random = new Random();
        synchronized (notificationMutex) {
            for (int i = 0; i < startPositions.length; i++) {
                // skip the very last position without successor
                if (i == positions.size() - 1 || i == startPositions.length - 1)
                    continue;
                List<BaseNavigationPosition> successorPredecessor = new ArrayList<BaseNavigationPosition>();
                successorPredecessor.add(positions.get(startPositions[i]));
                successorPredecessor.add(positions.get(startPositions[i] + 1));
                addToQueue.put(random.nextInt(), successorPredecessor);
            }
        }

        synchronized (insertWaypointsQueue) {
            insertWaypointsQueue.putAll(addToQueue);
        }

        executor.execute(new Runnable() {
            public void run() {
                for (Integer key : addToQueue.keySet()) {
                    List<BaseNavigationPosition> successorPredecessor = addToQueue.get(key);
                    BaseNavigationPosition from = successorPredecessor.get(0);
                    BaseNavigationPosition to = successorPredecessor.get(1);
                    StringBuilder buffer = new StringBuilder();
                    buffer.append(mode).append("({");
                    buffer.append("origin: new google.maps.LatLng(").append(from.getLatitude()).append(",").append(from.getLongitude()).append("), ");
                    buffer.append("destination: new google.maps.LatLng(").append(to.getLatitude()).append(",").append(to.getLongitude()).append("), ");
                    buffer.append("travelMode: google.maps.DirectionsTravelMode.").append(travelMode.toString().toUpperCase()).append(", ");
                    buffer.append("avoidHighways: ").append(avoidHighways).append(", ");
                    buffer.append("avoidTolls: ").append(avoidTolls).append(", ");
                    buffer.append("region: '").append(Locale.getDefault().getCountry().toLowerCase()).append("'}, ").append(key).append(");\n");
                    executeScript(buffer.toString());
                    try {
                        sleep(500);
                    } catch (InterruptedException e) {
                        // don't care if this happens
                    }
                }
            }
        });
    }

    // call Google Maps API functions

    public void insertAllWaypoints(int[] startPositions) {
        insertWaypoints("insertAllWaypoints", startPositions);
    }

    public void insertOnlyTurnpoints(int[] startPositions) {
        insertWaypoints("insertOnlyTurnpoints", startPositions);
    }

    public void print(boolean withRoute) {
        executeScript("printMap(" + withRoute + ");");
    }

    // script execution

    private String escape(String string) {
        if (string == null)
            return "";
        StringBuilder buffer = new StringBuilder(string);
        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (!(Character.isLetterOrDigit(c) || Character.isWhitespace(c) || c == '\'' || c == ',')) {
                buffer.deleteCharAt(i);
                i--;
            }
        }
        return buffer.toString();
    }

    protected void logJavaScript(String script, Object result) {
        log.info("script '" + script + (result != null ? "'\nwith result '" + result : "") + "'");
    }

    protected abstract void executeScript(String script);

    protected abstract String executeScriptWithResult(String script);

    // browser callbacks

    private void processStream(BufferedReader reader) {
        List<String> lines = new ArrayList<String>();
        boolean processingPost = false, processingBody = false;
        while (true) {
            try {
                String line = Transfer.trim(reader.readLine());
                if (line == null) {
                    if (processingPost && !processingBody) {
                        processingBody = true;
                        continue;
                    } else
                        break;
                }
                if (line.startsWith("POST"))
                    processingPost = true;
                lines.add(line);
            } catch (IOException e) {
                log.severe("Cannot read line from callback listener port:" + e.getMessage());
                break;
            }
        }

        final List<String> theLines = new ArrayList<String>(lines);
        executor.execute(new Runnable() {
            public void run() {
                StringBuilder buffer = new StringBuilder();
                for (String line : theLines) {
                    buffer.append("  ").append(line).append("\n");
                }
                log.fine("processing callback: \n" + buffer.toString());

                if (!isAuthenticated(theLines))
                    return;

                processLines(theLines);
            }
        });
    }

    private boolean isAuthenticated(List<String> lines) {
        Map<String, String> map = asMap(lines);
        String host = Transfer.trim(map.get("Host"));
        return host != null && host.equals("127.0.0.1:" + getCallbackPort());
    }

    int getCallbackPort() {
        return callbackListenerServerSocket.getLocalPort();
    }

    private static final Pattern NAME_VALUE_PATTERN = Pattern.compile("^(.+?):(.+)$");

    private Map<String, String> asMap(List<String> lines) {
        Map<String, String> map = new HashMap<String, String>();
        for (String line : lines) {
            Matcher matcher = NAME_VALUE_PATTERN.matcher(line);
            if (matcher.matches())
                map.put(matcher.group(1), matcher.group(2));
        }
        return map;
    }

    private static final Pattern CALLBACK_REQUEST_PATTERN = Pattern.compile("^(GET|OPTIONS|POST) /(\\d+)/(.*) HTTP.+$");
    private int lastCallbackNumber = -1;

    void processLines(List<String> lines) {
        boolean hasValidCallbackNumber = false;
        for (String line : lines) {
            log.fine("processing line " + line);
            Matcher matcher = CALLBACK_REQUEST_PATTERN.matcher(line);
            if (matcher.matches()) {
                int callbackNumber = parseInt(matcher.group(2));
                if (lastCallbackNumber >= callbackNumber) {
                    log.info("ignoring callback number: " + callbackNumber + " last callback number is: " + lastCallbackNumber);
                    break;
                }
                lastCallbackNumber = callbackNumber;
                hasValidCallbackNumber = true;

                String callback = matcher.group(3);
                if (processCallback(callback)) {
                    log.fine("processed " + matcher.group(1) + " callback " + callback + " with number: " + callbackNumber);
                    break;
                }
            }

            // process body of POST requests
            if (hasValidCallbackNumber && processCallback(line)) {
                log.fine("processed POST callback " + line + " with number: " + lastCallbackNumber);
                break;
            }
        }
    }

    private static final Pattern DIRECTIONS_LOAD_PATTERN = Pattern.compile("^load/(\\d*)/(\\d*)$");
    private static final Pattern ADD_POSITION_PATTERN = Pattern.compile("^add-position/(.*)/(.*)$");
    private static final Pattern INSERT_POSITION_PATTERN = Pattern.compile("^insert-position/(.*)/(.*)/(.*)$");
    private static final Pattern MOVE_POSITION_PATTERN = Pattern.compile("^move-position/(.*)/(.*)/(.*)$");
    private static final Pattern DELETE_POSITION_PATTERN = Pattern.compile("^delete-position/(.*)/(.*)/(.*)$");
    private static final Pattern SELECT_POSITION_PATTERN = Pattern.compile("^select-position/(.*)/(.*)/(.*)/(.*)$");
    private static final Pattern SELECT_POSITIONS_PATTERN = Pattern.compile("^select-positions/(.*)/(.*)/(.*)/(.*)/(.*)");
    private static final Pattern MAP_TYPE_CHANGED_PATTERN = Pattern.compile("^maptypechanged/(.*)$");
    private static final Pattern ZOOM_CHANGED_PATTERN = Pattern.compile("^zoomchanged$");
    private static final Pattern CENTER_CHANGED_PATTERN = Pattern.compile("^centerchanged$");
    private static final Pattern CALLBACK_PORT_PATTERN = Pattern.compile("^callback-port/(\\d+)$");
    private static final Pattern INSERT_WAYPOINTS_PATTERN = Pattern.compile("^(Insert-All-Waypoints|Insert-Only-Turnpoints): (-?\\d+)/(.*)$");

    boolean processCallback(String callback) {
        Matcher directionsLoadMatcher = DIRECTIONS_LOAD_PATTERN.matcher(callback);
        if (directionsLoadMatcher.matches()) {
            meters += parseInt(directionsLoadMatcher.group(1));
            seconds += parseInt(directionsLoadMatcher.group(2));
            fireCalculatedDistance(meters, seconds);
            return true;
        }

        Matcher insertPositionMatcher = INSERT_POSITION_PATTERN.matcher(callback);
        if (insertPositionMatcher.matches()) {
            final int row = parseInt(insertPositionMatcher.group(1)) + 1;
            final Double latitude = parseDouble(insertPositionMatcher.group(2));
            final Double longitude = parseDouble(insertPositionMatcher.group(3));
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    insertPosition(row, longitude, latitude);
                }
            });
            return true;
        }

        Matcher addPositionMatcher = ADD_POSITION_PATTERN.matcher(callback);
        if (addPositionMatcher.matches()) {
            final int row = getAddRow();
            final Double latitude = parseDouble(addPositionMatcher.group(1));
            final Double longitude = parseDouble(addPositionMatcher.group(2));
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    insertPosition(row, longitude, latitude);
                }
            });
            return true;
        }

        Matcher movePositionMatcher = MOVE_POSITION_PATTERN.matcher(callback);
        if (movePositionMatcher.matches()) {
            final int row = getMoveRow(parseInt(movePositionMatcher.group(1)));
            final Double latitude = parseDouble(movePositionMatcher.group(2));
            final Double longitude = parseDouble(movePositionMatcher.group(3));
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    movePosition(row, longitude, latitude);
                }
            });
            return true;
        }

        Matcher deletePositionMatcher = DELETE_POSITION_PATTERN.matcher(callback);
        if (deletePositionMatcher.matches()) {
            final Double latitude = parseDouble(deletePositionMatcher.group(1));
            final Double longitude = parseDouble(deletePositionMatcher.group(2));
            final Double threshold = parseDouble(deletePositionMatcher.group(3));
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    deletePosition(longitude, latitude, threshold);
                }
            });
            return true;
        }

        Matcher selectPositionMatcher = SELECT_POSITION_PATTERN.matcher(callback);
        if (selectPositionMatcher.matches()) {
            final Double latitude = parseDouble(selectPositionMatcher.group(1));
            final Double longitude = parseDouble(selectPositionMatcher.group(2));
            final Double threshold = parseDouble(selectPositionMatcher.group(3));
            final Boolean replaceSelection = Boolean.parseBoolean(selectPositionMatcher.group(4));
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    selectPosition(longitude, latitude, threshold, replaceSelection);
                }
            });
            return true;
        }

        Matcher selectPositionsMatcher = SELECT_POSITIONS_PATTERN.matcher(callback);
        if (selectPositionsMatcher.matches()) {
            final Double latitudeNorthEast = parseDouble(selectPositionsMatcher.group(1));
            final Double longitudeNorthEast = parseDouble(selectPositionsMatcher.group(2));
            final Double latitudeSouthWest = parseDouble(selectPositionsMatcher.group(3));
            final Double longitudeSouthWest = parseDouble(selectPositionsMatcher.group(4));
            final Boolean replaceSelection = Boolean.parseBoolean(selectPositionsMatcher.group(5));
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    selectPositions(asPosition(longitudeNorthEast, latitudeNorthEast),
                            asPosition(longitudeSouthWest, latitudeSouthWest), replaceSelection);
                }
            });
            return true;
        }

        Matcher mapTypeChangedMatcher = MAP_TYPE_CHANGED_PATTERN.matcher(callback);
        if (mapTypeChangedMatcher.matches()) {
            String mapType = mapTypeChangedMatcher.group(1);
            preferences.put(MAP_TYPE_PREFERENCE, mapType);
            return true;
        }

        Matcher zoomChangedMatcher = ZOOM_CHANGED_PATTERN.matcher(callback);
        if (zoomChangedMatcher.matches()) {
            synchronized (notificationMutex) {
                // since setCenter() leads to a callback and thus paints the track twice
                if (ignoreNextZoomCallback)
                    ignoreNextZoomCallback = false;
                else {
                    haveToRepaintRouteImmediately = true;
                    // if enabled, recenter map to selected positions after zooming
                    if (recenterAfterZooming)
                        haveToRecenterMap = true;
                    haveToRepaintSelectionImmediately = true;
                    int currentZoomLevel = getCurrentZoomLevel();
                    selectionUpdateReason = "zoomed from " + lastZoomLevel + " to " + currentZoomLevel;
                    lastZoomLevel = currentZoomLevel;
                    notificationMutex.notifyAll();
                }
            }
            return true;
        }

        Matcher centerChangedMatcher = CENTER_CHANGED_PATTERN.matcher(callback);
        if (centerChangedMatcher.matches()) {
            if (visibleNorthEast != null && visibleSouthWest != null && visibleNorthWest != null && visibleSouthEast != null) {
                BaseNavigationPosition mapNorthEast = getNorthEastBounds();
                BaseNavigationPosition mapSouthWest = getSouthWestBounds();

                if (contains(mapNorthEast, mapSouthWest, visibleNorthEast) ||
                        contains(mapNorthEast, mapSouthWest, visibleNorthWest) ||
                        contains(mapNorthEast, mapSouthWest, visibleSouthEast) ||
                        contains(mapNorthEast, mapSouthWest, visibleSouthWest)) {
                    synchronized (notificationMutex) {
                        haveToRepaintRouteImmediately = true;
                        routeUpdateReason = "repaint not visible positions";
                        notificationMutex.notifyAll();
                    }
                }
            }
            return true;
        }

        Matcher callbackPortMatcher = CALLBACK_PORT_PATTERN.matcher(callback);
        if (callbackPortMatcher.matches()) {
            int port = parseInt(callbackPortMatcher.group(1));
            fireReceivedCallback(port);
            return true;
        }

        Matcher insertWaypointsMatcher = INSERT_WAYPOINTS_PATTERN.matcher(callback);
        if (insertWaypointsMatcher.matches()) {
            Integer key = parseInt(insertWaypointsMatcher.group(2));
            List<String> coordinates = parseCoordinates(insertWaypointsMatcher.group(3));

            List<BaseNavigationPosition> successorPredecessor;
            synchronized (insertWaypointsQueue) {
                successorPredecessor = insertWaypointsQueue.remove(key);
            }

            if (coordinates.size() < 5 || successorPredecessor == null)
                return true;

            BaseNavigationPosition before = successorPredecessor.get(0);
            BaseNavigationPosition after = successorPredecessor.get(1);
            final int row;
            synchronized (notificationMutex) {
                row = positions.indexOf(before) + 1;
            }
            final BaseRoute route = parseRoute(coordinates, before, after);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    insertPositions(row, route);
                    complementPositions(row, route);
                }
            });
            log.info("processed insert " + callback);
            return false;
        }
        return false;
    }

    private boolean isDuplicate(BaseNavigationPosition position, BaseNavigationPosition insert) {
        if (position == null)
            return false;
        Double distance = position.calculateDistance(insert);
        return distance != null && distance < 10.0;
    }

    private String trimSpaces(String string) {
        if ("-".equals(string))
            return null;
        try {
            return trim(new String(string.getBytes(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private List<String> parseCoordinates(String coordinates) {
        List<String> result = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(coordinates, "/");
        while (tokenizer.hasMoreTokens()) {
            String latitude = trim(tokenizer.nextToken());
            if (tokenizer.hasMoreTokens()) {
                String longitude = trim(tokenizer.nextToken());
                if (tokenizer.hasMoreTokens()) {
                    String meters = trim(tokenizer.nextToken());
                    if (tokenizer.hasMoreTokens()) {
                        String seconds = trim(tokenizer.nextToken());
                        if (tokenizer.hasMoreTokens()) {
                            String instructions = trimSpaces(tokenizer.nextToken());
                            result.add(latitude);
                            result.add(longitude);
                            result.add(meters);
                            result.add(seconds);
                            result.add(instructions);
                        }
                    }
                }
            }
        }
        return result;
    }

    private Double parseSeconds(String string) {
        Double result = parseDouble(string);
        return !isEmpty(result) ? result : null;
    }

    @SuppressWarnings("unchecked")
    private BaseRoute parseRoute(List<String> coordinates, BaseNavigationPosition before, BaseNavigationPosition after) {
        BaseRoute route = new NavigatingPoiWarnerFormat().createRoute(Waypoints, null, new ArrayList<BaseNavigationPosition>());
        // count backwards as inserting at position 0
        CompactCalendar time = after.getTime();
        int positionInsertionCount = coordinates.size() / 5;
        for (int i = coordinates.size() - 1; i > 0; i -= 5) {
            String instructions = trim(coordinates.get(i));
            Double seconds = parseSeconds(coordinates.get(i - 1));
            // Double meters = parseDouble(coordinates.get(i - 2));
            Double longitude = parseDouble(coordinates.get(i - 3));
            Double latitude = parseDouble(coordinates.get(i - 4));
            if (seconds != null && time != null) {
                Calendar calendar = time.getCalendar();
                calendar.add(SECOND, -seconds.intValue());
                time = fromCalendar(calendar);
            }
            int positionNumber = positionsModel.getRowCount() + (positionInsertionCount - route.getPositionCount()) - 1;
            String comment = instructions != null ? instructions : positionAugmenter.createComment(positionNumber);
            BaseNavigationPosition position = route.createPosition(longitude, latitude, null, null, seconds != null ? time : null, comment);
            if (!isDuplicate(before, position) && !isDuplicate(after, position)) {
                route.add(0, position);
            }
        }
        return route;
    }

    @SuppressWarnings("unchecked")
    private void insertPositions(int row, BaseRoute route) {
        try {
            positionsModel.add(row, route);
        } catch (IOException e) {
            log.severe("Cannot insert route: " + e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked"})
    private void complementPositions(int row, BaseRoute route) {
        List<BaseNavigationPosition> positions = route.getPositions();
        int index = row;
        for (BaseNavigationPosition position : positions) {
            // do not complement comment since this is limited to 2500 calls/day
            positionAugmenter.complementElevation(index, position.getLongitude(), position.getLatitude());
            positionAugmenter.complementTime(index, null);
            index++;
        }
    }

    private void insertPosition(int row, Double longitude, Double latitude) {
        positionsModel.add(row, longitude, latitude, null, null, null, positionAugmenter.createComment(positionsModel.getRowCount() + 1));
        positionsSelectionModel.setSelectedPositions(new int[]{row}, true);

        positionAugmenter.complementComment(row, longitude, latitude);
        positionAugmenter.complementElevation(row, longitude, latitude);
        positionAugmenter.complementTime(row, null);
    }

    private int getAddRow() {
        BaseNavigationPosition position = lastSelectedPositions.size() > 0 ? lastSelectedPositions.get(lastSelectedPositions.size() - 1) : null;
        // quite crude logic to be as robust as possible on failures
        if (position == null && positionsModel.getRowCount() > 0)
            position = positionsModel.getPosition(positionsModel.getRowCount() - 1);
        return position != null ? positionsModel.getIndex(position) + 1 : 0;
    }

    private int getMoveRow(int index) {
        BaseNavigationPosition position = lastSelectedPositions.get(index);
        final int row;
        synchronized (notificationMutex) {
            row = positions.indexOf(position);
        }
        return row;
    }

    private void movePosition(int row, Double longitude, Double latitude) {
        BaseNavigationPosition reference = positionsModel.getPosition(row);
        Double diffLongitude = reference != null ? longitude - reference.getLongitude() : 0.0;
        Double diffLatitude = reference != null ? latitude - reference.getLatitude() : 0.0;

        int minimum = row;
        for (int index : selectedPositionIndices) {
            if (index < minimum)
                minimum = index;

            BaseNavigationPosition position = positionsModel.getPosition(index);
            if (position == null)
                continue;

            if (index != row) {
                positionsModel.edit(position.getLongitude() + diffLongitude, index, LONGITUDE_COLUMN_INDEX, false, true);
                positionsModel.edit(position.getLatitude() + diffLatitude, index, LATITUDE_COLUMN_INDEX, false, true);
            } else {
                positionsModel.edit(longitude, index, LONGITUDE_COLUMN_INDEX, false, true);
                positionsModel.edit(latitude, index, LATITUDE_COLUMN_INDEX, false, true);
            }

            if (preferences.getBoolean(CLEAN_ELEVATION_ON_MOVE_PREFERENCE, false))
                positionsModel.edit(null, index, ELEVATION_COLUMN_INDEX, false, false);
            if (preferences.getBoolean(CLEAN_TIME_ON_MOVE_PREFERENCE, false))
                positionsModel.edit(null, index, TIME_COLUMN_INDEX, false, false);
            if (preferences.getBoolean(COMPLEMENT_TIME_ON_MOVE_PREFERENCE, false))
                positionAugmenter.complementTime(index, null);
        }

        // updating all rows behind the modified is quite expensive, but necessary due to the distance
        // calculation - if that didn't exist the single update of row would be sufficient
        int size;
        synchronized (notificationMutex) {
            size = positions.size() - 1;
            haveToRepaintRouteImmediately = true;
            routeUpdateReason = "move position";
            significantPositionCache.clear();
            haveToRepaintSelectionImmediately = true;
            selectionUpdateReason = "move position";
        }
        positionsModel.fireTableRowsUpdated(minimum, size, ALL_COLUMNS);
    }

    private void selectPosition(Double longitude, Double latitude, Double threshold, boolean replaceSelection) {
        int row = positionsModel.getClosestPosition(longitude, latitude, threshold);
        if (row != -1)
            positionsSelectionModel.setSelectedPositions(new int[]{row}, replaceSelection);
    }

    private void selectPositions(BaseNavigationPosition northEastCorner, BaseNavigationPosition southWestCorner, boolean replaceSelection) {
        int[] rows = positionsModel.getContainedPositions(northEastCorner, southWestCorner);
        if (rows.length > 0) {
            positionsSelectionModel.setSelectedPositions(rows, replaceSelection);
        }
    }

    private void deletePosition(Double longitude, Double latitude, Double threshold) {
        int row = positionsModel.getClosestPosition(longitude, latitude, threshold);
        if (row != -1) {
            positionsModel.remove(new int[]{row});

            executor.execute(new Runnable() {
                public void run() {
                    synchronized (notificationMutex) {
                        haveToRepaintRouteImmediately = true;
                        routeUpdateReason = "delete position";
                        notificationMutex.notifyAll();
                    }
                }
            });
        }
    }

    // listeners

    private final List<MapViewListener> mapViewListeners = new CopyOnWriteArrayList<MapViewListener>();

    public void addMapViewListener(MapViewListener listener) {
        mapViewListeners.add(listener);
    }

    public void removeMapViewListener(MapViewListener listener) {
        mapViewListeners.remove(listener);
    }

    private void fireCalculatedDistance(int meters, int seconds) {
        for (MapViewListener listener : mapViewListeners) {
            listener.calculatedDistance(meters, seconds);
        }
    }

    private void fireReceivedCallback(int port) {
        for (MapViewListener listener : mapViewListeners) {
            listener.receivedCallback(port);
        }
    }
}
