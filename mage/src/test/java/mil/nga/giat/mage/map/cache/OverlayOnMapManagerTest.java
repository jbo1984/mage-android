package mil.nga.giat.mage.map.cache;

import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.bechte.junit.runners.context.HierarchicalContextRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@RunWith(HierarchicalContextRunner.class)
public class OverlayOnMapManagerTest implements MapDataManager.CreateUpdatePermission {

    static class TestOverlayOnMap extends OverlayOnMapManager.OverlayOnMap {

        boolean visible;
        boolean onMap;
        boolean disposed;
        int zIndex;

        TestOverlayOnMap(OverlayOnMapManager manager) {
            manager.super();
        }

        @Override
        protected void addToMap() {
            onMap = true;
        }

        @Override
        protected void removeFromMap() {
            onMap = false;
        }

        @Override
        protected void show() {
            visible = true;
        }

        @Override
        protected void hide() {
            visible = false;
        }

        @Override
        protected void setZIndex(int z) {
            zIndex = z;
        }

        int getZIndex() {
            return zIndex;
        }

        @Override
        protected void zoomMapToBoundingBox() {

        }

        @Override
        protected boolean isOnMap() {
            return onMap;
        }

        @Override
        protected boolean isVisible() {
            return visible;
        }


        @Override
        protected String onMapClick(LatLng latLng, MapView mapView) {
            return null;
        }

        @Override
        protected void dispose() {
            disposed = true;
        }

        OverlayOnMapManager.OverlayOnMap visible(boolean x) {
            if (x) {
                show();
            }
            else {
                hide();
            }
            return this;
        }

        OverlayOnMapManager.OverlayOnMap onMap(boolean x) {
            if (x) {
                addToMap();
            }
            else {
                removeFromMap();
            }
            return this;
        }
    }

    static OverlayOnMapManager.OverlayOnMap mockOverlayOnMap(OverlayOnMapManager overlayManager) {
        return mock(OverlayOnMapManager.OverlayOnMap.class, withSettings().useConstructor(overlayManager));
    }

    @SafeVarargs
    static <T> Set<T> setOf(T... things) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(things)));
    }

    static URI makeUri() {
        try {
            return new URI("test:" + UUID.randomUUID());
        }
        catch (URISyntaxException e) {
            throw new Error(e);
        }
    }


    MapDataManager mapDataManager;
    MapLayerDescriptorTest.TestMapDataProvider1 provider1;
    MapLayerDescriptorTest.TestMapDataProvider2 provider2;
    List<MapDataProvider> providers;

    @Before
    public void setup() {

        provider1 = mock(MapLayerDescriptorTest.TestMapDataProvider1.class);
        provider2 = mock(MapLayerDescriptorTest.TestMapDataProvider2.class);
        mapDataManager = mock(MapDataManager.class, withSettings().useConstructor(new MapDataManager.Config().updatePermission(this)));
        providers = Arrays.asList(provider1, provider2);
    }

    @Test
    public void listensToCacheManagerUpdates() {

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        verify(mapDataManager).addUpdateListener(overlayManager);
    }

    @Test
    public void addsOverlaysFromAddedCaches() {

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 1", "test cache", provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 2", "test cache", provider1.getClass());
        MapDataResource mapDataResource = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1, overlay2));
        Set<MapDataResource> added = setOf(mapDataResource);
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, added, Collections.<MapDataResource>emptySet(), Collections.<MapDataResource>emptySet());

        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(mockOverlayOnMap(overlayManager));
        when(provider1.createMapLayerFromDescriptor(overlay2, overlayManager)).thenReturn(mockOverlayOnMap(overlayManager));

        overlayManager.onMapDataUpdated(update);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(2));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItems(overlay1, overlay2));
    }

    @Test
    public void removesOverlaysFromRemovedCaches() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 1", "test cache", provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 2", "test cache", provider1.getClass());
        MapDataResource mapDataResource = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1, overlay2));

        when(mapDataManager.getCaches()).thenReturn(setOf(mapDataResource));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(2));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItems(overlay1, overlay2));

        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.<MapDataResource>emptySet(), Collections.<MapDataResource>emptySet(), setOf(mapDataResource));
        overlayManager.onMapDataUpdated(update);

        assertTrue(overlayManager.getOverlaysInZOrder().isEmpty());
    }

    @Test
    public void removesOverlaysFromUpdatedCaches() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 1", "test cache", provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 2", "test cache", provider1.getClass());
        MapDataResource mapDataResource = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1, overlay2));

        when(mapDataManager.getCaches()).thenReturn(setOf(mapDataResource));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        List<MapLayerDescriptor> overlays = overlayManager.getOverlaysInZOrder();
        assertThat(overlays.size(), is(2));
        assertThat(overlays, hasItems(overlay1, overlay2));

        overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1(overlay2.getOverlayName(), overlay2.getCacheName(), overlay2.getCacheType());
        mapDataResource = new MapDataResource(mapDataResource.getUri(), mapDataResource.getName(), mapDataResource.getType(), setOf(overlay2));
        Set<MapDataResource> updated = setOf(mapDataResource);
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this,
            Collections.<MapDataResource>emptySet(), updated, Collections.<MapDataResource>emptySet());

        when(provider1.createMapLayerFromDescriptor(overlay2, overlayManager)).thenReturn(mockOverlayOnMap(overlayManager));

        overlayManager.onMapDataUpdated(update);

        overlays = overlayManager.getOverlaysInZOrder();
        assertThat(overlays.size(), is(1));
        assertThat(overlays, hasItem(overlay2));
    }

    @Test
    public void addsOverlaysFromUpdatedCaches() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 1", "test cache", provider1.getClass());
        MapDataResource cache = new MapDataResource(makeUri(), "test cache", provider1.getClass(), setOf(overlay1));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(1));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItem(overlay1));


        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("test overlay 2", "test cache", provider1.getClass());
        cache = new MapDataResource(makeUri(), cache.getName(), cache.getType(), setOf(overlay1, overlay2));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(
            this, Collections.<MapDataResource>emptySet(), setOf(cache), Collections.<MapDataResource>emptySet());
        overlayManager.onMapDataUpdated(update);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(2));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItems(overlay1, overlay2));
    }

    @Test
    public void addsAndRemovesOverlaysFromUpdatedCachesWhenOverlayCountIsUnchanged() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", "test cache", provider1.getClass());
        MapDataResource cache = new MapDataResource(makeUri(), "test cache", provider1.getClass(), setOf(overlay1));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(1));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItem(overlay1));

        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 2", "test cache", provider1.getClass());
        cache = new MapDataResource(makeUri(), cache.getName(), cache.getType(), setOf(overlay2));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(
            this, Collections.<MapDataResource>emptySet(), setOf(cache), Collections.<MapDataResource>emptySet());
        overlayManager.onMapDataUpdated(update);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(1));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItems(overlay2));
    }

    @Test
    public void replacesLikeOverlaysFromUpdatedCaches() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", "test cache", provider1.getClass());
        MapDataResource cache = new MapDataResource(makeUri(), "test cache", provider1.getClass(), setOf(overlay1));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(1));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItem(overlay1));

        MapLayerDescriptor overlay1Updated = new MapLayerDescriptorTest.TestLayerDescriptor1(overlay1.getOverlayName(), overlay1.getCacheName(), overlay1.getCacheType());
        cache = new MapDataResource(makeUri(), cache.getName(), cache.getType(), setOf(overlay1Updated));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(
            this, Collections.<MapDataResource>emptySet(), setOf(cache), Collections.<MapDataResource>emptySet());
        overlayManager.onMapDataUpdated(update);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(1));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItem(overlay1));
        assertThat(overlayManager.getOverlaysInZOrder(), not(hasItem(sameInstance(overlay1))));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItem(sameInstance(overlay1Updated)));
    }

    @Test
    public void createsOverlaysOnMapLazily() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", "test cache", provider1.getClass());
        MapDataResource mapDataResource = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1));
        Set<MapDataResource> added = setOf(mapDataResource);
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, added, Collections.<MapDataResource>emptySet(), Collections.<MapDataResource>emptySet());
        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        overlayManager.onMapDataUpdated(update);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(1));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItems(overlay1));
        assertFalse(overlayManager.isOverlayVisible(overlay1));
        verify(provider1, never()).createMapLayerFromDescriptor(any(MapLayerDescriptor.class), Mockito.same(overlayManager));

        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(new TestOverlayOnMap(overlayManager));

        overlayManager.showOverlay(overlay1);

        assertTrue(overlayManager.isOverlayVisible(overlay1));
        verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
    }


    @Test
    public void refreshesVisibleOverlayOnMapWhenUpdated() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", "test cache", provider1.getClass());
        MapDataResource cache = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(1));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItem(overlay1));
        assertFalse(overlayManager.isOverlayVisible(overlay1));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
        verify(onMap).addToMap();

        overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1(overlay1.getOverlayName(), overlay1.getCacheName(), overlay1.getCacheType());
        cache = new MapDataResource(makeUri(), cache.getName(), cache.getType(), setOf(overlay1));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.<MapDataResource>emptySet(), setOf(cache), Collections.<MapDataResource>emptySet());

        OverlayOnMapManager.OverlayOnMap onMapUpdated = mockOverlayOnMap(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMapUpdated);
        when(onMap.isOnMap()).thenReturn(true);
        when(onMap.isVisible()).thenReturn(true);

        overlayManager.onMapDataUpdated(update);

        verify(onMap).removeFromMap();
        verify(provider1).createMapLayerFromDescriptor(Mockito.same(overlay1), Mockito.same(overlayManager));
        verify(onMapUpdated).addToMap();
    }

    @Test
    public void doesNotRefreshHiddenOverlayOnMapWhenUpdated() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", "test cache", provider1.getClass());
        MapDataResource cache = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(1));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItem(overlay1));
        assertFalse(overlayManager.isOverlayVisible(overlay1));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
        verify(onMap).addToMap();

        overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1(overlay1.getOverlayName(), overlay1.getCacheName(), overlay1.getCacheType());
        cache = new MapDataResource(makeUri(), cache.getName(), cache.getType(), setOf(overlay1));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.<MapDataResource>emptySet(), setOf(cache), Collections.<MapDataResource>emptySet());

        OverlayOnMapManager.OverlayOnMap onMapUpdated = mockOverlayOnMap(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMapUpdated);
        when(onMap.isOnMap()).thenReturn(true);
        when(onMap.isVisible()).thenReturn(false);

        overlayManager.onMapDataUpdated(update);

        verify(onMap).removeFromMap();
        verify(provider1, never()).createMapLayerFromDescriptor(Mockito.same(overlay1), Mockito.same(overlayManager));
        verify(onMapUpdated, never()).addToMap();
    }

    @Test
    public void doesNotRefreshUnchangedVisibleOverlaysFromUpdatedCaches() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", "test cache", provider1.getClass());
        MapDataResource cache = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(1));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItem(overlay1));
        assertFalse(overlayManager.isOverlayVisible(overlay1));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
        verify(onMap).addToMap();

        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 2", cache.getName(), cache.getType());
        cache = new MapDataResource(makeUri(), cache.getName(), cache.getType(), setOf(overlay1, overlay2));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.<MapDataResource>emptySet(), setOf(cache), Collections.<MapDataResource>emptySet());

        OverlayOnMapManager.OverlayOnMap onMapUpdated = mockOverlayOnMap(overlayManager);

        overlayManager.onMapDataUpdated(update);

        verify(onMap, never()).removeFromMap();
        verify(provider1, times(1)).createMapLayerFromDescriptor(Mockito.same(overlay1), Mockito.same(overlayManager));
        verify(onMapUpdated, never()).addToMap();
    }

    @Test
    public void removesOverlayOnMapWhenOverlayIsRemovedFromCache() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", "test cache", provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 2", "test cache", provider1.getClass());
        MapDataResource cache = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1, overlay2));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(2));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItems(overlay1, overlay2));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
        verify(onMap).addToMap();

        cache = new MapDataResource(makeUri(), cache.getName(), cache.getType(), setOf(overlay2));
        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.<MapDataResource>emptySet(), setOf(cache), Collections.<MapDataResource>emptySet());

        overlayManager.onMapDataUpdated(update);

        verify(onMap).removeFromMap();
    }

    @Test
    public void removesOverlayOnMapWhenCacheIsRemoved() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", "test cache", provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 2", "test cache", provider1.getClass());
        MapDataResource cache = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1, overlay2));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(2));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItems(overlay1, overlay2));

        OverlayOnMapManager.OverlayOnMap onMap =  mockOverlayOnMap(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
        verify(onMap).addToMap();

        MapDataManager.MapDataUpdate update = mapDataManager.new MapDataUpdate(this, Collections.<MapDataResource>emptySet(), Collections.<MapDataResource>emptySet(), setOf(cache));

        overlayManager.onMapDataUpdated(update);

        verify(onMap).removeFromMap();
    }

    @Test
    public void showsOverlayTheFirstTimeOverlayIsAdded() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay 1", "test cache", provider1.getClass());
        MapDataResource cache = new MapDataResource(new File("test").toURI(), "test cache", provider1.getClass(), setOf(overlay1));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertFalse(overlayManager.isOverlayVisible(overlay1));

        TestOverlayOnMap onMap =  new TestOverlayOnMap(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMap);

        overlayManager.showOverlay(overlay1);

        assertTrue(overlayManager.isOverlayVisible(overlay1));
        assertTrue(onMap.isOnMap());
        assertTrue(onMap.isVisible());
    }

    @Test
    public void behavesWhenTwoCachesHaveOverlaysWithTheSameName() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay1", "cache1", provider1.getClass());
        MapDataResource cache1 = new MapDataResource(makeUri(), "cache1", provider1.getClass(), setOf(overlay1));

        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor2("overlay1", "cache2", provider1.getClass());
        MapDataResource cache2 = new MapDataResource(makeUri(), "cache2", provider1.getClass(), setOf(overlay2));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache1, cache2));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        assertThat(overlayManager.getOverlaysInZOrder().size(), is(2));
        assertThat(overlayManager.getOverlaysInZOrder(), hasItems(overlay1, overlay2));

        OverlayOnMapManager.OverlayOnMap onMap1 = mockOverlayOnMap(overlayManager);
        OverlayOnMapManager.OverlayOnMap onMap2 = mockOverlayOnMap(overlayManager);
        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMap1);
        when(provider1.createMapLayerFromDescriptor(overlay2, overlayManager)).thenReturn(onMap2);

        overlayManager.showOverlay(overlay1);

        verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
        verify(provider1, never()).createMapLayerFromDescriptor(overlay2, overlayManager);
        verify(onMap1).addToMap();

        overlayManager.showOverlay(overlay2);

        verify(provider1).createMapLayerFromDescriptor(overlay1, overlayManager);
        verify(provider1).createMapLayerFromDescriptor(overlay2, overlayManager);
        verify(onMap1).addToMap();
        verify(onMap2).addToMap();

        when(onMap2.isVisible()).thenReturn(true);

        overlayManager.hideOverlay(overlay2);

        verify(onMap2).hide();
        verify(onMap1, never()).hide();
    }

    @Test
    public void behavesWhenTwoOverlaysAndTheirCachesHaveTheSameNames() {

        fail("unimplemented");
    }

    @Test
    public void maintainsOrderOfUpdatedCacheOverlays() {

        fail("unimplemented");
    }

    @Test
    public void forwardsMapClicksToOverlaysInZOrder() {

        fail("unimplemented");
    }

    @Test
    public void notifiesListenersWhenOverlaysUpdate() {

        fail("unimplemented");
    }

    @Test
    public void notifiesListenersWhenZOrderChanges() {

        fail("unimplemented");
    }

    @Test
    public void disposeStopsListeningToCacheManager() {

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
        overlayManager.dispose();

        verify(mapDataManager).removeUpdateListener(overlayManager);
    }

    @Test
    public void disposeRemovesAndDisposesAllOverlays() {

        MapLayerDescriptor overlay1 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay1", "cache1", provider1.getClass());
        MapLayerDescriptor overlay2 = new MapLayerDescriptorTest.TestLayerDescriptor1("overlay2", "cache1", provider1.getClass());
        MapLayerDescriptor overlay3 = new MapLayerDescriptorTest.TestLayerDescriptor2("overlay3", "cache2", provider2.getClass());
        MapDataResource cache1 = new MapDataResource(makeUri(), "cache1", provider1.getClass(), setOf(overlay1, overlay2));
        MapDataResource cache2 = new MapDataResource(makeUri(), "cache1", provider2.getClass(), setOf(overlay3));

        when(mapDataManager.getCaches()).thenReturn(setOf(cache1, cache2));

        OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);

        OverlayOnMapManager.OverlayOnMap onMap1 = mockOverlayOnMap(overlayManager);
        OverlayOnMapManager.OverlayOnMap onMap2 = mockOverlayOnMap(overlayManager);
        OverlayOnMapManager.OverlayOnMap onMap3 = mockOverlayOnMap(overlayManager);

        when(provider1.createMapLayerFromDescriptor(overlay1, overlayManager)).thenReturn(onMap1);
        when(provider1.createMapLayerFromDescriptor(overlay2, overlayManager)).thenReturn(onMap2);
        when(provider2.createMapLayerFromDescriptor(overlay3, overlayManager)).thenReturn(onMap3);

        overlayManager.showOverlay(overlay1);
        overlayManager.showOverlay(overlay2);
        overlayManager.showOverlay(overlay3);

        when(onMap1.isOnMap()).thenReturn(true);
        when(onMap2.isOnMap()).thenReturn(true);
        when(onMap3.isOnMap()).thenReturn(true);

        overlayManager.dispose();

        verify(onMap1).removeFromMap();
        verify(onMap1).dispose();
        verify(onMap2).removeFromMap();
        verify(onMap2).dispose();
        verify(onMap3).removeFromMap();
        verify(onMap3).dispose();
    }

    public class ZOrderTests {

        private MapLayerDescriptor c1o1;
        private MapLayerDescriptor c1o2;
        private MapLayerDescriptor c1o3;
        private MapLayerDescriptor c2o1;
        private MapLayerDescriptor c2o2;
        private MapDataResource cache1;
        private MapDataResource cache2;

        @Before
        public void setup() {

            c1o1 = new MapLayerDescriptorTest.TestLayerDescriptor1("c1.1", "c1", provider1.getClass());
            c1o2 = new MapLayerDescriptorTest.TestLayerDescriptor1("c1.2", "c1", provider1.getClass());
            c1o3 = new MapLayerDescriptorTest.TestLayerDescriptor1("c1.3", "c1", provider1.getClass());
            cache1 = new MapDataResource(makeUri(), "c1", provider1.getClass(), setOf(c1o1, c1o2, c1o3));

            c2o1 = new MapLayerDescriptorTest.TestLayerDescriptor2("c2.1", "c2", provider2.getClass());
            c2o2 = new MapLayerDescriptorTest.TestLayerDescriptor2("c2.2", "c2", provider2.getClass());
            cache2 = new MapDataResource(makeUri(), "c2", provider2.getClass(), setOf(c2o2, c2o1));

            when(mapDataManager.getCaches()).thenReturn(setOf(cache1, cache2));
        }

        @Test
        public void returnsModifiableCopyOfOverlayZOrder() {

            OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> orderModified = overlayManager.getOverlaysInZOrder();
            Collections.reverse(orderModified);
            List<MapLayerDescriptor> orderUnmodified = overlayManager.getOverlaysInZOrder();

            assertThat(orderUnmodified, not(sameInstance(orderModified)));
            assertThat(orderUnmodified, not(contains(orderModified.toArray())));
            assertThat(orderUnmodified.get(0), sameInstance(orderModified.get(orderModified.size() - 1)));
        }

        @Test
        public void initializesOverlaysOnMapWithProperZOrder() {

            OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> order = overlayManager.getOverlaysInZOrder();
            int c1o1z = order.indexOf(c1o1);
            int c2o1z = order.indexOf(c2o1);

            TestOverlayOnMap c1o1OnMap = new TestOverlayOnMap(overlayManager);
            TestOverlayOnMap c2o1OnMap = new TestOverlayOnMap(overlayManager);
            when(provider1.createMapLayerFromDescriptor(c1o1, overlayManager)).thenReturn(c1o1OnMap);
            when(provider2.createMapLayerFromDescriptor(c2o1, overlayManager)).thenReturn(c2o1OnMap);

            overlayManager.showOverlay(c1o1);
            overlayManager.showOverlay(c2o1);

            assertThat(c1o1OnMap.getZIndex(), is(c1o1z));
            assertThat(c2o1OnMap.getZIndex(), is(c2o1z));
        }

        @Test
        public void setsComprehensiveZOrderFromList() {

            OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> order = overlayManager.getOverlaysInZOrder();
            Collections.reverse(order);

            assertTrue(overlayManager.setZOrder(order));

            List<MapLayerDescriptor> orderMod = overlayManager.getOverlaysInZOrder();

            assertThat(orderMod, equalTo(order));
        }

        @Test
        public void setsZOrderOfOverlaysOnMapFromComprehensiveUpdate() {

            OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> order = overlayManager.getOverlaysInZOrder();
            int c1o1z = order.indexOf(c1o1);
            int c2o1z = order.indexOf(c2o1);
            int c2o2z = order.indexOf(c2o2);
            TestOverlayOnMap c1o1OnMap = new TestOverlayOnMap(overlayManager);
            TestOverlayOnMap c2o1OnMap = new TestOverlayOnMap(overlayManager);
            TestOverlayOnMap c2o2OnMap = new TestOverlayOnMap(overlayManager);

            when(provider1.createMapLayerFromDescriptor(c1o1, overlayManager)).thenReturn(c1o1OnMap);
            when(provider2.createMapLayerFromDescriptor(c2o1, overlayManager)).thenReturn(c2o1OnMap);
            when(provider2.createMapLayerFromDescriptor(c2o2, overlayManager)).thenReturn(c2o2OnMap);

            overlayManager.showOverlay(c1o1);
            overlayManager.showOverlay(c2o1);
            overlayManager.showOverlay(c2o2);

            assertThat(c1o1OnMap.getZIndex(), is(c1o1z));
            assertThat(c2o1OnMap.getZIndex(), is(c2o1z));
            assertThat(c2o2OnMap.getZIndex(), is(c2o2z));

            int c1o1zMod = c2o1z;
            int c2o1zMod = c2o2z;
            int c2o2zMod = c1o1z;
            order.set(c1o1zMod, c1o1);
            order.set(c2o1zMod, c2o1);
            order.set(c2o2zMod, c2o2);

            assertTrue(overlayManager.setZOrder(order));

            List<MapLayerDescriptor> orderMod = overlayManager.getOverlaysInZOrder();

            assertThat(orderMod, equalTo(order));
            assertThat(orderMod.indexOf(c1o1), is(c1o1zMod));
            assertThat(orderMod.indexOf(c2o1), is(c2o1zMod));
            assertThat(orderMod.indexOf(c2o2), is(c2o2zMod));
            assertThat(c1o1OnMap.getZIndex(), is(c1o1zMod));
            assertThat(c2o1OnMap.getZIndex(), is(c2o1zMod));
            assertThat(c2o2OnMap.getZIndex(), is(c2o2zMod));
        }

        @Test
        public void setsZOrderOfHiddenOverlayOnMapFromComprehensiveUpdate() {

            OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> order = overlayManager.getOverlaysInZOrder();
            int c1o1z = order.indexOf(c1o1);
            int c2o1z = order.indexOf(c2o1);
            TestOverlayOnMap c1o1OnMap = new TestOverlayOnMap(overlayManager);
            TestOverlayOnMap c2o1OnMap = new TestOverlayOnMap(overlayManager);

            when(provider1.createMapLayerFromDescriptor(c1o1, overlayManager)).thenReturn(c1o1OnMap);
            when(provider2.createMapLayerFromDescriptor(c2o1, overlayManager)).thenReturn(c2o1OnMap);

            overlayManager.showOverlay(c1o1);
            overlayManager.showOverlay(c2o1);

            assertThat(c1o1OnMap.getZIndex(), is(c1o1z));
            assertThat(c2o1OnMap.getZIndex(), is(c2o1z));

            overlayManager.hideOverlay(c1o1);
            Collections.swap(order, c1o1z, c2o1z);

            assertTrue(overlayManager.setZOrder(order));

            List<MapLayerDescriptor> orderMod = overlayManager.getOverlaysInZOrder();

            assertThat(orderMod, equalTo(order));
            assertThat(orderMod.indexOf(c1o1), is(c2o1z));
            assertThat(orderMod.indexOf(c2o1), is(c1o1z));
            assertTrue(c1o1OnMap.isOnMap());
            assertFalse(c1o1OnMap.isVisible());
            assertTrue(c2o1OnMap.isOnMap());
            assertTrue(c2o1OnMap.isVisible());
        }

        @Test
        public void doesNotSetZOrderIfNewOrderHasDifferingElements() {

            OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> invalidOrder = overlayManager.getOverlaysInZOrder();
            TestOverlayOnMap firstOnMap = new TestOverlayOnMap(overlayManager);
            MapLayerDescriptor first = invalidOrder.get(0);

            when(provider1.createMapLayerFromDescriptor(first, overlayManager)).thenReturn(firstOnMap);
            when(provider2.createMapLayerFromDescriptor(first, overlayManager)).thenReturn(firstOnMap);

            overlayManager.showOverlay(first);

            assertThat(firstOnMap.getZIndex(), is(0));

            invalidOrder.set(1, new MapLayerDescriptorTest.TestLayerDescriptor1("c1.1.tainted", "c1", provider1.getClass()));

            assertFalse(overlayManager.setZOrder(invalidOrder));

            List<MapLayerDescriptor> unchangedOrder = overlayManager.getOverlaysInZOrder();

            assertThat(unchangedOrder, not(equalTo(invalidOrder)));
            assertThat(unchangedOrder, not(hasItem(invalidOrder.get(1))));
            assertThat(firstOnMap.getZIndex(), is(0));
        }

        @Test
        public void doesNotSetZOrderIfNewOrderHasDifferentSize() {

            OverlayOnMapManager overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
            List<MapLayerDescriptor> invalidOrder = overlayManager.getOverlaysInZOrder();
            TestOverlayOnMap lastOnMap = new TestOverlayOnMap(overlayManager);
            int lastZIndex = invalidOrder.size() - 1;
            MapLayerDescriptor last = invalidOrder.get(lastZIndex);

            when(provider1.createMapLayerFromDescriptor(last, overlayManager)).thenReturn(lastOnMap);
            when(provider2.createMapLayerFromDescriptor(last, overlayManager)).thenReturn(lastOnMap);

            overlayManager.showOverlay(last);

            assertThat(lastOnMap.getZIndex(), is(lastZIndex));

            invalidOrder.remove(0);

            assertFalse(overlayManager.setZOrder(invalidOrder));

            List<MapLayerDescriptor> unchangedOrder = overlayManager.getOverlaysInZOrder();

            assertThat(unchangedOrder, not(equalTo(invalidOrder)));
            assertThat(unchangedOrder.size(), is(invalidOrder.size() + 1));
            assertThat(lastOnMap.getZIndex(), is(lastZIndex));
        }

        public class MovingSingleOverlayZIndex {

            private String qnameOf(MapLayerDescriptor overlay) {
                return overlay.getCacheName() + ":" + overlay.getOverlayName();
            }

            OverlayOnMapManager overlayManager;
            Map<MapLayerDescriptor, TestOverlayOnMap> overlaysOnMap;

            @Before
            public void addAllOverlaysToMap() {
                overlayManager = new OverlayOnMapManager(mapDataManager, providers, null);
                overlaysOnMap = new HashMap<>();
                List<MapLayerDescriptor> orderByName = overlayManager.getOverlaysInZOrder();
                Collections.sort(orderByName, new Comparator<MapLayerDescriptor>() {
                    @Override
                    public int compare(MapLayerDescriptor o1, MapLayerDescriptor o2) {
                        return qnameOf(o1).compareTo(qnameOf(o2));
                    }
                });

                assertTrue(overlayManager.setZOrder(orderByName));

                for (MapLayerDescriptor overlay : orderByName) {
                    TestOverlayOnMap onMap = new TestOverlayOnMap(overlayManager);
                    overlaysOnMap.put(overlay, onMap);
                    if (overlay.getCacheType() == provider1.getClass()) {
                        when(provider1.createMapLayerFromDescriptor(overlay, overlayManager)).thenReturn(onMap);
                    }
                    else if (overlay.getCacheType() == provider2.getClass()) {
                        when(provider2.createMapLayerFromDescriptor(overlay, overlayManager)).thenReturn(onMap);
                    }
                    overlayManager.showOverlay(overlay);
                }
            }

            private void assertZIndexMove(int from, int to) {
                List<MapLayerDescriptor> order = overlayManager.getOverlaysInZOrder();
                List<MapLayerDescriptor> expectedOrder = new ArrayList<>(order);
                MapLayerDescriptor target = expectedOrder.remove(from);
                expectedOrder.add(to, target);

                assertTrue(overlayManager.moveZIndex(from, to));

                order = overlayManager.getOverlaysInZOrder();
                assertThat(String.format("%d to %d", from, to), order, equalTo(expectedOrder));
                for (int zIndex = 0; zIndex < expectedOrder.size(); zIndex++) {
                    MapLayerDescriptor overlay = expectedOrder.get(zIndex);
                    TestOverlayOnMap onMap = overlaysOnMap.get(overlay);
                    assertThat(qnameOf(overlay) + " on map", onMap.getZIndex(), is(zIndex));
                }
            }

            @Test
            public void movesTopToLowerZIndex() {
                assertZIndexMove(4, 2);
            }

            @Test
            public void movesTopToBottomZIndex() {
                assertZIndexMove(4, 0);
            }

            @Test
            public void movesBottomToTopZIndex() {
                assertZIndexMove(0, 4);
            }

            @Test
            public void movesBottomToHigherZIndex() {
                assertZIndexMove(0, 3);
            }

            @Test
            public void movesMiddleToLowerZIndex() {
                assertZIndexMove(2, 1);
            }

            @Test
            public void movesMiddleToHigherZIndex() {
                assertZIndexMove(2, 3);
            }
        }
    }
}
