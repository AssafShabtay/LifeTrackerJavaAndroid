package com.example.myapplication.data.sync;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.model.MovementActivity;
import com.example.myapplication.data.model.Place;
import com.example.myapplication.data.model.RoutePoint;
import com.example.myapplication.data.model.StillLocation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SyncStoreTest {
    private ActivityDatabase first;
    private ActivityDatabase second;
    private SyncStore source;
    private SyncStore target;
    private ActivityDatabase database() {
        return Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ActivityDatabase.class)
                .addCallback(new SyncSchema()).allowMainThreadQueries().build();
    }
    @Before public void setup() {
        first = database(); second = database();
        source = new SyncStore(first); target = new SyncStore(second);
    }
    @After public void close() { first.close(); second.close(); }
    private Place place(String name) { Place p = new Place(); p.setName(name); return p; }
    private SyncStore.Pending pending(SyncStore store, String table) { return store.pending(table, "", 100).get(0); }

    @Test public void restoreMergesAndRemapsForeignKeysWithoutDuplicates() {
        long placeId = first.placeDao().insertPlace(place("Home"));
        StillLocation still = new StillLocation();
        still.setPlaceId(placeId); still.setStartTimeDate(new Date(100)); still.setEndTimeDate(new Date(200));
        first.activityDao().insertStillLocation(still);
        // Existing rows on the new device deliberately collide with the old device's integer IDs.
        second.placeDao().insertPlace(place("Existing"));
        SyncStore.Pending cloudPlace = pending(source, "places");
        SyncStore.Pending cloudStill = pending(source, "still_locations");
        assertTrue(target.apply("places", cloudPlace.id, cloudPlace.document));
        assertTrue(target.apply("still_locations", cloudStill.id, cloudStill.document));
        assertTrue(target.apply("places", cloudPlace.id, cloudPlace.document));
        assertTrue(target.apply("still_locations", cloudStill.id, cloudStill.document));
        assertEquals(2, second.placeDao().getAllPlaces().size());
        Long restoredPlace = target.localId("places", cloudPlace.id);
        assertNotEquals(Long.valueOf(placeId), restoredPlace);
        assertEquals(restoredPlace, second.activityDao().getStillLocationById(target.localId("still_locations", cloudStill.id)).getPlaceId());
        assertTrue(target.pending("still_locations", "", 100).isEmpty());
    }
    @Test public void acknowledgementCannotLoseAnEditMadeDuringUpload() {
        long id = first.placeDao().insertPlace(place("Before"));
        SyncStore.Pending upload = pending(source, "places");
        Place edited = first.placeDao().getPlaceById(id); edited.setName("After");
        first.placeDao().updatePlace(edited);
        source.acknowledge(upload);
        SyncStore.Pending remaining = pending(source, "places");
        assertTrue(remaining.version > upload.version);
        assertNotEquals(upload.document.get("mutation"), remaining.document.get("mutation"));
        assertEquals("After", ((Map<?, ?>) remaining.document.get("fields")).get("name"));
    }
    @Test public void localPendingEditIsNotOverwrittenByDownload() {
        first.placeDao().insertPlace(place("Home"));
        SyncStore.Pending cloud = pending(source, "places");
        target.apply("places", cloud.id, cloud.document);
        Place local = second.placeDao().getPlaceById(target.localId("places", cloud.id));
        local.setName("My edit"); second.placeDao().updatePlace(local);
        assertFalse(target.apply("places", cloud.id, cloud.document));
        assertEquals("My edit", second.placeDao().getPlaceById(local.getId()).getName());
    }
    @Test public void deletionWinsAndCannotBeResurrectedByOldData() {
        first.placeDao().insertPlace(place("Home"));
        SyncStore.Pending old = pending(source, "places");
        target.apply("places", old.id, old.document);
        first.placeDao().deleteAllPlaces();
        SyncStore.Pending deletion = pending(source, "places");
        assertEquals(old.id, deletion.id);
        assertEquals(true, deletion.document.get("deleted"));
        target.apply("places", deletion.id, deletion.document);
        target.apply("places", old.id, old.document);
        assertTrue(second.placeDao().getAllPlaces().isEmpty());
        assertNull(target.localId("places", old.id));
    }
    @Test public void routePointsFollowTheirRemappedMovementAndCascadeDeletion() {
        MovementActivity movement = new MovementActivity();
        movement.setStartTimeDate(new Date(100)); movement.setEndTimeDate(new Date(200));
        long id = first.activityDao().insertMovementActivity(movement);
        RoutePoint point = new RoutePoint(); point.setMovementActivityId(id); point.setTimestamp(150);
        first.activityDao().insertRoutePoint(point);
        second.activityDao().insertMovementActivity(movement);
        SyncStore.Pending parent = pending(source, "movement_activities");
        SyncStore.Pending child = pending(source, "route_points");
        target.apply("movement_activities", parent.id, parent.document);
        target.apply("route_points", child.id, child.document);
        Long restored = target.localId("movement_activities", parent.id);
        assertNotEquals(Long.valueOf(id), restored);
        assertEquals(1, second.activityDao().getRoutePointsForMovement(restored).size());
        first.activityDao().deleteMovementActivity(id);
        assertEquals(true, pending(source, "route_points").document.get("deleted"));
        target.apply("movement_activities", parent.id, pending(source, "movement_activities").document);
        assertTrue(second.activityDao().getRoutePointsForMovement(restored).isEmpty());
        assertEquals(true, pending(target, "route_points").document.get("deleted"));
    }
    @Test public void incompleteActivitiesStayQueuedUntilFinished() {
        MovementActivity movement = new MovementActivity(); movement.setStartTimeDate(new Date(100));
        long id = first.activityDao().insertMovementActivity(movement);
        RoutePoint point = new RoutePoint(); point.setMovementActivityId(id); first.activityDao().insertRoutePoint(point);
        assertNull(pending(source, "movement_activities").document);
        assertNull(pending(source, "route_points").document);
        first.activityDao().endMovementActivity(id, 1.0, 2.0, new Date(200));
        assertNotNull(pending(source, "movement_activities").document);
        assertNotNull(pending(source, "route_points").document);
    }
    @Test public void accountBindingRejectsDifferentAccount() {
        assertTrue(source.bindOwner("first"));
        assertTrue(source.bindOwner("first"));
        assertFalse(source.bindOwner("second"));
        assertEquals("first", source.state("owner"));
    }
    @Test public void schemaReopenKeepsIdentitiesAndDoesNotQueueCleanRows() {
        first.placeDao().insertPlace(place("Home"));
        SyncStore.Pending row = pending(source, "places");
        source.acknowledge(row);
        new SyncSchema().onOpen(first.getOpenHelper().getWritableDatabase());
        assertEquals(Long.valueOf(1), source.localId("places", row.id));
        assertTrue(source.pending("places", "", 100).isEmpty());
    }
    @Test public void tombstoneCanArriveBeforeTheOriginalRecord() {
        first.placeDao().insertPlace(place("Home"));
        SyncStore.Pending original = pending(source, "places");
        Map<String, Object> deletion = new HashMap<>(original.document);
        deletion.put("deleted", true);
        target.apply("places", original.id, deletion);
        target.apply("places", original.id, original.document);
        assertTrue(second.placeDao().getAllPlaces().isEmpty());
    }
}
