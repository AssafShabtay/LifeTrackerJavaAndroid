package com.trace.data.model;

import com.trace.R;
import org.junit.Test;
import java.util.Date;
import java.util.Locale;
import static org.junit.Assert.*;

public class MovementActivityTest {
    @Test public void concreteTypesOwnTheirPresentation() {
        MovementActivity[] activities = {new WalkingActivity(), new DrivingActivity(),
                new RunningActivity(), new CyclingActivity()};
        String[] names = {"Walking", "Driving", "Running", "Cycling"};
        int[] colors = {R.color.activity_walking, R.color.activity_vehicle,
                R.color.activity_running, R.color.activity_cycling};
        int[] icons = {R.drawable.ic_walk, R.drawable.ic_car,
                R.drawable.sprint_24px, R.drawable.ic_bike};
        for (int i = 0; i < activities.length; i++) {
            assertEquals(names[i], activities[i].getActivityTypeName());
            assertEquals(colors[i], activities[i].getColorRes());
            assertEquals(icons[i], activities[i].getIconRes());
            assertEquals(activities[i].getClass(),
                    MovementActivity.createFromTypeName(names[i]).getClass());
        }
    }

    @Test public void roomRowRoundTripPreservesAllStoredValues() {
        MovementActivity source = new DrivingActivity();
        source.setId(42);
        source.setStartLat(31.2);
        source.setStartLng(34.8);
        source.setEndLat(31.5);
        source.setEndLng(35.0);
        source.setStartTimeDate(new Date(1000));
        source.setEndTimeDate(new Date(2000));
        // Simulate Room hydration through the entity's stored fields and setters.
        MovementActivity row = new MovementActivity();
        row.setStoredTypeName(source.getStoredTypeName());
        row.setId(source.getId());
        row.setStartLat(source.getStartLat());
        row.setStartLng(source.getStartLng());
        row.setEndLat(source.getEndLat());
        row.setEndLng(source.getEndLng());
        row.setStartTimeDate(source.getStartTimeDate());
        row.setEndTimeDate(source.getEndTimeDate());
        MovementActivity restored = row.toActivity();
        assertTrue(restored instanceof DrivingActivity);
        assertEquals(source, restored);
        assertEquals(source.hashCode(), restored.hashCode());
    }

    @Test public void legacyAliasesKeepExactDatabaseNamesAcrossEditsAndCopies() {
        String[] aliases = {"IN_VEHICLE", "ON_BICYCLE", "On Foot", "WALKING"};
        Class<?>[] types = {DrivingActivity.class, CyclingActivity.class,
                WalkingActivity.class, WalkingActivity.class};
        for (int i = 0; i < aliases.length; i++) {
            MovementActivity row = new MovementActivity();
            row.setStoredTypeName(aliases[i]);
            MovementActivity activity = row.toActivity();
            assertEquals(types[i], activity.getClass());
            assertEquals(aliases[i], activity.copy().getStoredTypeName());
        }
    }

    @Test public void unknownAndNullNamesSurviveWithoutInventingAType() {
        for (String name : new String[]{"Skiing", "", null}) {
            MovementActivity activity = MovementActivity.createFromTypeName(name);
            assertTrue(activity instanceof UnknownMovementActivity);
            assertEquals(name, activity.copy().getStoredTypeName());
            assertNull(activity.getEndTimeDate());
            assertNull(activity.getStartLat());
        }
    }

    @Test public void copyDeepCopiesDatesAndNestedStops() {
        MovementActivity activity = new RunningActivity();
        activity.setStartTimeDate(new Date(1000));
        StillLocation stop = new StillLocation();
        stop.setStartTimeDate(new Date(1100));
        activity.getStops().add(stop);
        MovementActivity copy = activity.copy();
        assertEquals(activity, copy);
        copy.getStartTimeDate().setTime(2000);
        copy.getStops().get(0).getStartTimeDate().setTime(2100);
        assertEquals(1000, activity.getStartTimeDate().getTime());
        assertEquals(1100, stop.getStartTimeDate().getTime());
        activity.setStops(null);
        assertNull(activity.copy().getStops());
    }

    @Test public void changingTypePreservesIdentityCoordinatesAndStops() {
        MovementActivity original = new WalkingActivity();
        original.setId(7);
        original.setStartLat(31.0);
        original.setStartLng(34.0);
        original.setEndLat(32.0);
        original.setEndLng(35.0);
        original.setStartTimeDate(new Date(1000));
        original.getStops().add(new StillLocation());
        MovementActivity changed = original.copyWithTypeName("Cycling");
        assertTrue(changed instanceof CyclingActivity);
        assertTrue(original instanceof WalkingActivity);
        assertEquals(7, changed.getId());
        assertEquals(original.getStartLat(), changed.getStartLat());
        assertEquals(original.getStartLng(), changed.getStartLng());
        assertEquals(original.getEndLat(), changed.getEndLat());
        assertEquals(original.getEndLng(), changed.getEndLng());
        assertEquals(original.getStartTimeDate(), changed.getStartTimeDate());
        assertNull(changed.getEndTimeDate());
        assertEquals(original.getStops(), changed.getStops());
        assertNotSame(original.getStops().get(0), changed.getStops().get(0));
    }

    @Test public void typeRecognitionIsIndependentOfDeviceLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertTrue(MovementActivity.createFromTypeName("DRIVING") instanceof DrivingActivity);
            assertTrue(MovementActivity.createFromTypeName("CYCLING") instanceof CyclingActivity);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void stillAndMovementShareBaseIdentityAndTimeRange() {
        BaseActivity[] activities = {new StillLocation(), new WalkingActivity()};
        for (BaseActivity activity : activities) {
            activity.setId(3);
            activity.setStartTimeDate(new Date(1000));
            assertEquals(3, activity.getId());
            assertEquals(new Date(1000), activity.getStartTimeDate());
            assertNull(activity.getEndTimeDate());
        }
        StillLocation still = (StillLocation) activities[0];
        StillLocation copy = still.copy();
        assertEquals(still, copy);
        copy.getStartTimeDate().setTime(2000);
        assertEquals(1000, still.getStartTimeDate().getTime());
    }
}
