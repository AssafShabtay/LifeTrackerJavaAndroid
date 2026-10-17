package com.example.myapplication.tracking.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.location.Location;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.model.StillLocation;
import com.example.myapplication.tracking.geofence.GeofenceUtilsManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class StillTrackingQueueTest {
    @Test
    public void overlappingStillRequestsDuringLocationLookupInsertOnce() throws Exception {
        assertOverlappingRequestsInsertOnce(false);
    }

    @Test
    public void overlappingStillRequestsDuringPlaceLookupInsertOnce() throws Exception {
        assertOverlappingRequestsInsertOnce(true);
    }

    private void assertOverlappingRequestsInsertOnce(boolean blockPlaceLookup) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ActivityDatabase db = Room.inMemoryDatabaseBuilder(context, ActivityDatabase.class).build();
        // A separate application instance supplies the production queue without starting the service.
        LifeTrackerApp app = new LifeTrackerApp();
        ExecutorService trackingExecutor = app.getLocationTrackingExecutor();
        CountDownLatch lookupStarted = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        AtomicInteger locationLookups = new AtomicInteger();
        AtomicInteger placeLookups = new AtomicInteger();
        Long previousStillId = LocationService.currentStillTrackingId;
        LocationService.currentStillTrackingId = null;

        try {
            Location location = new Location("test");
            location.setLatitude(31.907402);
            location.setLongitude(34.995247);
            LocationService service = new LocationService() {
                @Override
                public File getFilesDir() {
                    return context.getCacheDir();
                }
            };
            GeofenceUtilsManager places = new GeofenceUtilsManager(null, context, null, null) {
                @Override
                public void findPlaceAndUpdateStill(Location currentLocation, StillLocation still) {
                    if (placeLookups.incrementAndGet() == 1 && blockPlaceLookup) {
                        pauseLookup(lookupStarted, releaseLookup);
                    }
                    still.setPlaceName("Test place");
                }
            };
            LocationProvider provider = new LocationProvider(
                    context, null, db.activityDao(), trackingExecutor, places, service) {
                @Override
                Location getLocationOnceBlocking() {
                    if (locationLookups.incrementAndGet() == 1 && !blockPlaceLookup) {
                        pauseLookup(lookupStarted, releaseLookup);
                    }
                    return location;
                }
            };
            setField(service, "dao", db.activityDao());
            setField(service, "locationProvider", provider);
            setField(service, "geofenceUtilsManager", places);
            setField(service, "activityMergeManager",
                    new ActivityMergeManager(db.activityDao(), service, provider));

            // Linger and STILL ENTER can request the same still while a lookup is blocked.
            Future<?> linger = trackingExecutor.submit(() -> service.startStillTracking(new Date(1000)));
            assertTrue("First still request never reached its lookup",
                    lookupStarted.await(5, TimeUnit.SECONDS));
            Future<?> stillEnter = trackingExecutor.submit(() -> service.startStillTracking(new Date(1001)));
            assertThrows(TimeoutException.class, () -> stillEnter.get(250, TimeUnit.MILLISECONDS));

            // Slow tracking lookups must not hold up the application's other database work.
            app.getDatabaseWriteExecutor().submit(() -> {}).get(5, TimeUnit.SECONDS);
            releaseLookup.countDown();
            linger.get(5, TimeUnit.SECONDS);
            stillEnter.get(5, TimeUnit.SECONDS);

            List<StillLocation> stills = db.activityDao().getAllStillLocations();
            assertEquals(1, stills.size());
            assertEquals(Long.valueOf(stills.get(0).getId()), LocationService.currentStillTrackingId);
            assertEquals(new Date(1000), stills.get(0).getStartTimeDate());
            assertEquals(1, locationLookups.get());
            assertEquals(1, placeLookups.get());
        } finally {
            releaseLookup.countDown();
            trackingExecutor.shutdownNow();
            trackingExecutor.awaitTermination(5, TimeUnit.SECONDS);
            app.getDatabaseWriteExecutor().shutdownNow();
            LocationService.currentStillTrackingId = previousStillId;
            db.close();
        }
    }

    private static void pauseLookup(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            assertTrue("Timed out waiting to finish the lookup", release.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void setField(LocationService service, String name, Object value) throws Exception {
        Field field = LocationService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
