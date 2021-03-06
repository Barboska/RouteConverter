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

import chrriis.dj.nativeswing.swtimpl.components.*;
import slash.common.io.Externalization;
import slash.common.io.Platform;
import slash.common.io.TokenResolver;
import slash.navigation.base.BaseNavigationPosition;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.logging.Logger;

import static chrriis.dj.nativeswing.swtimpl.NativeInterface.isOpen;
import static chrriis.dj.nativeswing.swtimpl.components.JWebBrowser.useWebkitRuntime;
import static chrriis.dj.nativeswing.swtimpl.components.JWebBrowser.useXULRunnerRuntime;
import static slash.common.io.Platform.isLinux;
import static slash.common.io.Transfer.parseDouble;

/**
 * Implementation for a component that displays the positions of a position list on a map.
 *
 * @author Christian Pesch
 */

public class EclipseSWTMapView extends BaseMapView {
    private static final Logger log = Logger.getLogger(EclipseSWTMapView.class.getName());
    private static final String GOOGLE_MAPS_SERVER_PREFERENCE = "mapServer";
    private static final String DEBUG_PREFERENCE = "debug";

    private JWebBrowser webBrowser;
    private boolean debug = preferences.getBoolean(DEBUG_PREFERENCE, false);

    public boolean isSupportedPlatform() {
        return isLinux() || Platform.isMac() || Platform.isWindows();
    }

    public Component getComponent() {
        return webBrowser;
    }

    // initialization

    private JWebBrowser createWebBrowser() {
        try {
            if (!isOpen())
                throw new Exception("Native Interface is not initialized");
            JWebBrowser browser;
            if (isLinux()) {
                try {
                    System.setProperty("org.eclipse.swt.browser.UseWebKitGTK", "true");
                    // System.setProperty("nativeswing.webbrowser.runtime", "webkit");
                    browser = new JWebBrowser(useWebkitRuntime());
                    log.info("Using WebKit runtime to create WebBrowser");
                } catch (IllegalStateException e) {
                    System.clearProperty("org.eclipse.swt.browser.UseWebKitGTK");
                    browser = new JWebBrowser(useXULRunnerRuntime());
                    log.info("Using XULRunner runtime to create WebBrowser: " + e.getMessage());
                }
            } else {
                browser = new JWebBrowser();
            }
            browser.setBarsVisible(false);
            browser.setJavascriptEnabled(true);
            return browser;
        } catch (Throwable t) {
            log.severe("Cannot create WebBrowser: " + t.getMessage());
            setInitializationCause(t);
            return null;
        }
    }

    private boolean loadWebPage(final JWebBrowser webBrowser) {
        try {
            final String country = Locale.getDefault().getLanguage().toLowerCase();
            File html = Externalization.extractFile("slash/navigation/converter/gui/mapview/routeconverter.html", country, new TokenResolver() {
                public String resolveToken(String tokenName) {
                    if (tokenName.equals("locale"))
                        return country;
                    if (tokenName.equals("mapserver"))
                        return preferences.get(GOOGLE_MAPS_SERVER_PREFERENCE, "maps.google.com");
                    if (tokenName.equals("maptype"))
                        return preferences.get(MAP_TYPE_PREFERENCE, "roadmap");
                    return tokenName;
                }
            });
            if (html == null)
                throw new IllegalArgumentException("Cannot extract routeconverter.html");
            Externalization.extractFile("slash/navigation/converter/gui/mapview/contextmenu.js");
            Externalization.extractFile("slash/navigation/converter/gui/mapview/keydragzoom.js");

            final String url = html.toURI().toURL().toExternalForm();
            webBrowser.runInSequence(new Runnable() {
                public void run() {
                    webBrowser.navigate(url);
                }
            });
            log.fine(System.currentTimeMillis() + " loadWebPage thread " + Thread.currentThread());
        } catch (Throwable t) {
            log.severe("Cannot create WebBrowser: " + t.getMessage());
            setInitializationCause(t);
            return false;
        }
        return true;
    }

    protected void initializeBrowser() {
        webBrowser = createWebBrowser();
        if (webBrowser == null)
            return;

        webBrowser.addWebBrowserListener(new WebBrowserListener() {
            public void windowWillOpen(WebBrowserWindowWillOpenEvent e) {
                log.fine("WebBrowser windowWillOpen " + e.isConsumed() + " thread " + Thread.currentThread());
            }

            public void windowOpening(WebBrowserWindowOpeningEvent e) {
                log.fine("WebBrowser windowOpening " + e.getLocation() + "/" + e.getSize() + " thread " + Thread.currentThread());
            }

            public void windowClosing(WebBrowserEvent e) {
                log.fine("WebBrowser windowClosing " + e + " thread " + Thread.currentThread());
            }

            public void locationChanging(WebBrowserNavigationEvent e) {
                log.fine("WebBrowser locationChanging " + e.getNewResourceLocation() + " thread " + Thread.currentThread());
            }

            public void locationChanged(WebBrowserNavigationEvent e) {
                log.fine("WebBrowser locationChanged " + e.getNewResourceLocation() + " thread " + Thread.currentThread());
            }

            public void locationChangeCanceled(WebBrowserNavigationEvent e) {
                log.fine("WebBrowser locationChangeCanceled " + e.getNewResourceLocation() + " thread " + Thread.currentThread());
            }

            private int startCount = 0;

            public void loadingProgressChanged(WebBrowserEvent e) {
                log.fine("WebBrowser loadingProgressChanged " + e.getWebBrowser().getLoadingProgress() + " thread " + Thread.currentThread());

                if (e.getWebBrowser().getLoadingProgress() == 100 && startCount == 0) {
                    // get out of the listener callback
                    new Thread(new Runnable() {
                        public void run() {
                            tryToInitialize(startCount++, System.currentTimeMillis());
                        }
                    }, "MapViewInitializer").start();
                }
            }

            public void titleChanged(WebBrowserEvent e) {
                log.fine("WebBrowser titleChanged " + e.getWebBrowser().getPageTitle() + " thread " + Thread.currentThread());
            }

            public void statusChanged(WebBrowserEvent e) {
                log.fine("WebBrowser statusChanged " + e.getWebBrowser().getStatusText() + " thread " + Thread.currentThread());
            }

            public void commandReceived(WebBrowserCommandEvent e) {
                // log.fine("WebBrowser commandReceived " + e.getCommand() + " thread " + Thread.currentThread());
            }
        });

        if (!loadWebPage(webBrowser))
            dispose();
    }

    private void tryToInitialize(int count, long start) {
        boolean initialized = getComponent() != null && isMapInitialized();
        synchronized (this) {
            this.initialized = initialized;
        }
        log.fine("Initialized map: " + initialized);

        if (isInitialized()) {
            runBrowserInteractionCallbacksAndTests(start);
        } else {
            long end = System.currentTimeMillis();
            int timeout = count++ * 100;
            if (timeout > 3000)
                timeout = 3000;
            log.info("Failed to initialize map since " + (end - start) + " ms, sleeping for " + timeout + " ms");

            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                // intentionally left empty
            }
            tryToInitialize(count, start);
        }
    }

    private void runBrowserInteractionCallbacksAndTests(long start) {
        long end = System.currentTimeMillis();
        log.fine("Starting browser interaction, callbacks and tests after " + (end - start) + " ms");
        initializeAfterLoading();
        initializeBrowserInteraction();
        initializeCallbackListener();
        checkLocalhostResolution();
        checkCallback();
        end = System.currentTimeMillis();
        log.fine("Browser interaction is running after " + (end - start) + " ms");
    }

    private boolean isMapInitialized() {
        String result = executeScriptWithResult("return isInitialized();");
        return Boolean.parseBoolean(result);
    }

    private void initializeAfterLoading() {
        resize();
        update(true);
    }

    // resizing

    private boolean hasBeenResizedToInvisible = false;

    public void resize() {
        new Thread(new Runnable() {
            public void run() {
                if (!isInitialized() || !getComponent().isShowing())
                    return;

                synchronized (notificationMutex) {
                    // if map is not visible remember to update and resize it again
                    // once the map becomes visible again
                    if (!isVisible()) {
                        hasBeenResizedToInvisible = true;
                    } else if (hasBeenResizedToInvisible) {
                        hasBeenResizedToInvisible = false;
                        update(true);
                    }
                    resizeMap();
                }
            }
        }, "BrowserResizer").start();
    }

    private int lastWidth = -1, lastHeight = -1;

    private void resizeMap() {
        synchronized (notificationMutex) {
            int width = Math.max(getComponent().getWidth(), 0);
            int height = Math.max(getComponent().getHeight(), 0);
            if (width != lastWidth || height != lastHeight) {
                executeScript("resize(" + width + "," + height + ");");
            }
            lastWidth = width;
            lastHeight = height;
        }
    }

    // zoom level and bounds

    protected int getCurrentZoomLevel() {
        String zoomLevel = executeScriptWithResult("return map.getZoom();");
        return zoomLevel != null ? parseDouble(zoomLevel).intValue() : 1;
    }

    protected BaseNavigationPosition getNorthEastBounds() {
        return getBounds("return getNorthEastBounds();");
    }

    protected BaseNavigationPosition getSouthWestBounds() {
        return getBounds("return getSouthWestBounds();");
    }

    // script execution

    protected void executeScript(final String script) {
        if (webBrowser == null || script.length() == 0)
            return;

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    webBrowser.runInSequence(new Runnable() {
                        public void run() {
                            webBrowser.executeJavascript(script);
                        }
                    });
                    logJavaScript(script, null);
                }
            });
        } else {
            webBrowser.runInSequence(new Runnable() {
                public void run() {
                    webBrowser.executeJavascript(script);
                }
            });
            logJavaScript(script, null);
        }
    }

    protected String executeScriptWithResult(final String script) {
        if (script.length() == 0)
            return null;

        final boolean pollingCallback = !script.contains("getCallbacks");
        final Object[] result = new Object[1];
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        webBrowser.runInSequence(new Runnable() {
                            public void run() {
                                result[0] = webBrowser.executeJavascriptWithResult(script);
                                if (debug && pollingCallback) {
                                    log.info("After invokeLater, executeJavascriptWithResult " + result[0]);
                                }
                            }
                        });
                    }
                });
            } catch (InterruptedException e) {
                log.severe("Cannot execute script with result: " + e.getMessage());
            } catch (InvocationTargetException e) {
                log.severe("Cannot execute script with result: " + e.getMessage());
            }
        } else {
            webBrowser.runInSequence(new Runnable() {
                public void run() {
                    result[0] = webBrowser.executeJavascriptWithResult(script);
                    if (debug && pollingCallback) {
                        log.info("After executeJavascriptWithResult " + result[0]);
                    }
                }
            });
        }

        if (pollingCallback) {
            logJavaScript(script, result[0]);
        }
        return result[0] != null ? result[0].toString() : null;
    }
}
