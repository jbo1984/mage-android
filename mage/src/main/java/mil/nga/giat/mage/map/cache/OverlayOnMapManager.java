package mil.nga.giat.mage.map.cache;

import android.support.annotation.MainThread;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@MainThread
public class OverlayOnMapManager implements CacheManager.CacheOverlaysUpdateListener {

    public interface OverlayOnMapListener {

        /**
         * Notify the listener that the {@link CacheManager} has updated the {@link #getOverlaysInZOrder() cache list}.
         * {@link OverlayOnMapManager} will not invoke this method as result of its own on-map interactions,
         * such as adding, removing, showing, and hiding overlays.
         */
        void overlaysChanged();
    }

    public abstract class OverlayOnMap {

        /**
         * Add this overlay's objects to the map, e.g. {@link com.google.android.gms.maps.model.TileOverlay}s,
         * {@link com.google.android.gms.maps.model.Marker}s, {@link com.google.android.gms.maps.model.Polygon}s, etc.
         *
         */
        abstract protected void addToMap();

        /**
         * Remove this overlay's objects visible and hidden objects from the map.
         */
        abstract protected void removeFromMap();
        abstract protected void show();
        abstract protected void hide();
        abstract protected void setZIndex(int z);

        /**
         * TODO: change to CacheOverlay.getBoundingBox() instead so OverlayOnMapManager can do the zoom
         */
        abstract protected void zoomMapToBoundingBox();

        /**
         * Return true if this overlay's {@link #addToMap() map objects} have been
         * created and added to the map, regardless of visibility.
         *
         * @return
         */
        abstract protected boolean isOnMap();
        abstract protected boolean isVisible();
        // TODO: this is awkward passing the map view and returning a string; probably can do better
        abstract protected String onMapClick(LatLng latLng, MapView mapView);

        /**
         * Clear all the resources this overlay might hold such as data source connections or large
         * geometry collections and prepare for garbage collection.
         */
        abstract protected void dispose();
    }

    private static String keyForCache(MapCache cache) {
        return cache.getName() + ":" + cache.getType().getName();
    }

    private static String keyForCache(CacheOverlay overlay) {
        return overlay.getCacheName() + ":" + overlay.getCacheType().getName();
    }

    private final CacheManager cacheManager;
    private final GoogleMap map;
    private final Map<Class<? extends CacheProvider>, CacheProvider> providers = new HashMap<>();
    private final Map<CacheOverlay, OverlayOnMap> overlaysOnMap = new HashMap<>();
    private final List<OverlayOnMapListener> listeners = new ArrayList<>();
    private List<CacheOverlay> overlaysInZOrder = new ArrayList<>();

    public OverlayOnMapManager(CacheManager cacheManager, List<CacheProvider> providers, GoogleMap map) {
        this.cacheManager = cacheManager;
        this.map = map;
        for (CacheProvider provider : providers) {
            this.providers.put(provider.getClass(), provider);
        }
        for (MapCache cache : cacheManager.getCaches()) {
            overlaysInZOrder.addAll(cache.getCacheOverlays().values());
        }
        cacheManager.addUpdateListener(this);
    }

    @Override
    public void onCacheOverlaysUpdated(CacheManager.CacheOverlayUpdate update) {
        Set<String> removedCacheNames = new HashSet<>(update.removed.size());
        for (MapCache removed : update.removed) {
			removedCacheNames.add(removed.getName());
		}
		Map<String, Map<String, CacheOverlay>> updatedCaches = new HashMap<>(update.updated.size());
        for (MapCache cache : update.updated) {
            Map<String, CacheOverlay> updatedOverlays = new HashMap<>(cache.getCacheOverlays());
            updatedCaches.put(keyForCache(cache), updatedOverlays);
        }

        int position = 0;
        Iterator<CacheOverlay> orderIterator = overlaysInZOrder.iterator();
        while (orderIterator.hasNext()) {
            CacheOverlay overlay = orderIterator.next();
            if (removedCacheNames.contains(overlay.getCacheName())) {
                removeFromMapReturningVisibility(overlay);
                orderIterator.remove();
                position--;
            }
            else {
                String cacheKey = keyForCache(overlay);
                Map<String, CacheOverlay> updatedCacheOverlays = updatedCaches.get(cacheKey);
                if (updatedCacheOverlays != null) {
                    CacheOverlay updatedOverlay = updatedCacheOverlays.remove(overlay.getOverlayName());
                    if (updatedOverlay != null) {
                        refreshOverlayAtPositionFromUpdatedCache(position, updatedOverlay);
                    }
                    else {
                        removeFromMapReturningVisibility(overlay);
                        orderIterator.remove();
                        position--;
                    }
                }
            }
            position++;
        }

        for (Map<String, CacheOverlay> newOverlaysFromUpdatedCaches : updatedCaches.values()) {
            overlaysInZOrder.addAll(newOverlaysFromUpdatedCaches.values());
        }

        for (MapCache added : update.added) {
            overlaysInZOrder.addAll(added.getCacheOverlays().values());
        }

        for (OverlayOnMapListener listener : listeners) {
		    listener.overlaysChanged();
        }
    }

    public void addOverlayOnMapListener(OverlayOnMapListener x) {
        listeners.add(x);
    }

    public void removeOverlayOnMapListener(OverlayOnMapListener x) {
        listeners.remove(x);
    }

    public GoogleMap getMap() {
        return map;
    }

    /**
     * Return a modifiable copy of the overlay list in z-order.  The last element
     * (index {@code size() - 1}) in the list is the top-most element.
     */
    public List<CacheOverlay> getOverlaysInZOrder() {
        return new ArrayList<>(overlaysInZOrder);
    }

    public void showOverlay(CacheOverlay cacheOverlay) {
        addOverlayToMap(cacheOverlay);
    }

    public void hideOverlay(CacheOverlay cacheOverlay) {
        OverlayOnMap onMap = overlaysOnMap.get(cacheOverlay);
        if (onMap == null || !onMap.isVisible()) {
            return;
        }
        onMap.hide();
    }

    public boolean isOverlayVisible(CacheOverlay cacheOverlay) {
        OverlayOnMap onMap = overlaysOnMap.get(cacheOverlay);
        return onMap != null && onMap.isVisible();
    }

    public void onMapClick(LatLng latLng, MapView mapView) {
        for (CacheOverlay overlay : overlaysInZOrder) {
            OverlayOnMap onMap = overlaysOnMap.get(overlay);
            if (onMap != null) {
                onMap.onMapClick(latLng, mapView);
            }
        }
    }

    public void setZOrder(List<CacheOverlay> order) {
        if (order.size() != overlaysInZOrder.size()) {
            return;
        }
        Map<CacheOverlay, CacheOverlay> index = new HashMap<>(overlaysInZOrder.size());
        for (CacheOverlay overlay : overlaysInZOrder) {
            index.put(overlay, overlay);
        }
        List<CacheOverlay> targetOrder = new ArrayList<>(overlaysInZOrder.size());
        for (CacheOverlay overlayToMove : order) {
            CacheOverlay target = index.remove(overlayToMove);
            if (target == null) {
                return;
            }
            targetOrder.add(target);
        }
        if (index.size() > 0) {
            return;
        }
        overlaysInZOrder = targetOrder;
        int zIndex = 0;
        for (CacheOverlay overlay : overlaysInZOrder) {
            OverlayOnMap onMap = overlaysOnMap.get(overlay);
            if (onMap != null) {
                onMap.setZIndex(zIndex);
            }
            zIndex += 1;
        }
    }

    public void moveZIndex(int fromPosition, int toPosition) {
        List<CacheOverlay> order = getOverlaysInZOrder();
        CacheOverlay target = order.remove(fromPosition);
        order.add(toPosition, target);
        setZOrder(order);
    }

    public void dispose() {
        // TODO: remove and dispose all overlays/notify providers
        cacheManager.removeUpdateListener(this);
        Iterator<Map.Entry<CacheOverlay, OverlayOnMap>> entries = overlaysOnMap.entrySet().iterator();
        while (entries.hasNext()) {
            OverlayOnMap onMap = entries.next().getValue();
            onMap.removeFromMap();
            onMap.dispose();
            entries.remove();
        }
    }

    private boolean removeFromMapReturningVisibility(CacheOverlay overlay) {
        boolean wasVisible = false;
        OverlayOnMap onMap = overlaysOnMap.remove(overlay);
        if (onMap != null) {
            wasVisible = onMap.isVisible();
            onMap.removeFromMap();
        }
        return wasVisible;
    }

    private void refreshOverlayAtPositionFromUpdatedCache(int position, CacheOverlay updatedOverlay) {
        CacheOverlay currentOverlay = overlaysInZOrder.get(position);
        if (currentOverlay == updatedOverlay) {
            return;
        }
        overlaysInZOrder.set(position, updatedOverlay);
        if (removeFromMapReturningVisibility(currentOverlay)) {
            addOverlayToMapAtPosition(position);
        }
    }

    private void disposeOverlay(CacheOverlay overlay) {

    }

    private void addOverlayToMap(CacheOverlay overlay) {
        int position = overlaysInZOrder.indexOf(overlay);
        if (position > -1) {
            addOverlayToMapAtPosition(position);
        }
    }

    private void addOverlayToMapAtPosition(int position) {
        CacheOverlay overlay = overlaysInZOrder.get(position);
        OverlayOnMap onMap = overlaysOnMap.remove(overlay);
        if (onMap == null) {
            CacheProvider provider = providers.get(overlay.getCacheType());
            onMap = provider.createOverlayOnMapFromCache(overlay, this);
            onMap.setZIndex(position);
        }
        overlaysOnMap.put(overlay, onMap);
        if (!onMap.isOnMap()) {
            onMap.addToMap();
        }
        onMap.show();
    }
}