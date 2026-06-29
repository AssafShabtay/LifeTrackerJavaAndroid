package com.example.myapplication.mainScreen;

import static com.example.myapplication.helpers.ColorAndIcons.getStillColor;
import static com.example.myapplication.helpers.ColorAndIcons.getStillIconRes;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.myapplication.R;
import com.example.myapplication.database.ActivityDao;
import com.example.myapplication.database.ActivityDatabase;
import com.example.myapplication.database.MovementActivity;
import com.example.myapplication.database.RoutePoint;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.database.TimelineItem;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("ALL")
public class MapManager implements OnMapReadyCallback {
    private static final String TAG = "MapManager";
    private final Fragment fragment;
    private final int fragmentId;
    private GoogleMap mMap;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public MapManager(Fragment fragment, int fragmentId) {
        this.fragment = fragment;
        this.fragmentId = fragmentId;
    }

    public void init() {
        SupportMapFragment mapFragment = (SupportMapFragment) fragment.getChildFragmentManager()
                .findFragmentById(fragmentId);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this); //calling the map
        }

        View touchOverlay = fragment.requireView().findViewById(R.id.map_touch_overlay);

        if (touchOverlay != null) { // Set a touch listener if there is no overlay
            touchOverlay.setOnTouchListener((v, event) -> {
                if (v.getParent() != null) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            break;
                        case MotionEvent.ACTION_UP:
                            v.performClick();
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            break;
                        case MotionEvent.ACTION_CANCEL:
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            break;
                    }
                }
                // Return false so the touch event still reaches the map underneath
                return false;
            });
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setAllGesturesEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setScrollGesturesEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);

        updateMyLocationEnabled();
    }

    public void onResume() {
        updateMyLocationEnabled();
    }

    /**
     * Focuses the map on a specific timeline item (Still or Movement).
     * Clears previous markers and animates the camera to the new location(s).
     */
    public void focusOnItem(TimelineItem item) {
        if (mMap == null) return;

        // Clear existing markers
        mMap.clear();

        if (item instanceof StillLocation) {
            addStillToMap((StillLocation) item, 1, true);
            StillLocation still = (StillLocation) item;
            if (still.lat != null && still.lng != null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(still.lat, still.lng), 15f));
            }
        } else if (item instanceof MovementActivity) {
            MovementActivity movement = (MovementActivity) item;

            io.execute(() -> {
                ActivityDao dao = ActivityDatabase.getDatabase(fragment.requireContext()).activityDao();
                List<RoutePoint> points = dao.getRoutePointsForMovement(movement.id);

                fragment.requireActivity().runOnUiThread(() -> {
                    LatLngBounds bounds = drawMovementOnMap(movement, points, 1, true);
                    if (bounds != null) {
                        try {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                        } catch (IllegalStateException e) {
                            if (movement.startLat != null) {
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(movement.startLat, movement.startLng), 15f));
                            }
                        }
                    }
                });
            });
        }
    }

    private BitmapDescriptor getNumberedMarkerIcon(int number, int color) {
        int size = 90;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Circle background
        paint.setColor(color);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // White border
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(4);
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2, paint);

        // Text
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(36);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);

        String text = String.valueOf(number);
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        float y = (size / 2f) - bounds.exactCenterY();
        canvas.drawText(text, size / 2f, y, paint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private BitmapDescriptor getIconMarkerIcon(int iconResId, int color) {
        int size = 90;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Circle background
        paint.setColor(color);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // White border
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(4);
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2, paint);

        // Draw icon
        Drawable drawable = ContextCompat.getDrawable(fragment.requireContext(), iconResId);
        if (drawable != null) {
            Drawable wrappedDrawable = DrawableCompat.wrap(drawable).mutate();
            DrawableCompat.setTint(wrappedDrawable, Color.WHITE);
            int padding = 20;
            wrappedDrawable.setBounds(padding, padding, size - padding, size - padding);
            wrappedDrawable.draw(canvas);
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }



    private int getMovementIconRes(String type) {
        int iconRes = R.drawable.ic_walk;
        if (type == null) return iconRes;

        String t = type.toLowerCase();
        if (t.contains("driving") || t.contains("vehicle")) {
            iconRes = R.drawable.ic_car;
        } else if (t.contains("running")) {
            iconRes = R.drawable.ic_walk;
        } else if (t.contains("cycling") || t.contains("bicycle")) {
            iconRes = R.drawable.ic_bike;
        } else if (t.contains("walking") || t.contains("foot")) {
            iconRes = R.drawable.ic_walk;
        }
        return iconRes;
    }

    private void addStillToMap(StillLocation still, int number, boolean useIcon) {
        if (still.lat != null && still.lng != null) {
            LatLng pos = new LatLng(still.lat, still.lng);
            String title = (still.placeName != null) ? still.placeName : "Still Location";

            // Determine the color for the still location
            int color = getStillColor(still, fragment.requireContext());

            if (still.isStop) {
                title = "Stop: " + title;
            }

            BitmapDescriptor icon;
            if (useIcon) {
                icon = getIconMarkerIcon(getStillIconRes(still), color);
            } else {
                icon = getNumberedMarkerIcon(number, color);
            }

            mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title((useIcon ? "" : (number + ". ")) + title)
                    .icon(icon));
        }
    }

    /**
     * Shows all routes and markers for the entire day.
     */
    public void showFullDay(List<TimelineItem> items) {
        if (mMap == null || items == null || items.isEmpty()) return;
        mMap.clear();

        LatLngBounds.Builder totalBounds = new LatLngBounds.Builder();

        io.execute(() -> {
            ActivityDao dao = ActivityDatabase.getDatabase(fragment.requireContext()).activityDao();

            for (int i = 0; i < items.size(); i++) {
                TimelineItem item = items.get(i);
                final int number = i + 1;

                if (item instanceof StillLocation) {
                    StillLocation still = (StillLocation) item;
                    fragment.requireActivity().runOnUiThread(() -> {
                        addStillToMap(still, number, false);
                    });
                    if (still.lat != null && still.lng != null) {
                        totalBounds.include(new LatLng(still.lat, still.lng));
                    }
                } else if (item instanceof MovementActivity) {
                    MovementActivity movement = (MovementActivity) item;
                    List<RoutePoint> points = dao.getRoutePointsForMovement(movement.id);

                    fragment.requireActivity().runOnUiThread(() -> {
                        drawMovementOnMap(movement, points, number, false);
                    });

                    if (movement.startLat != null && movement.startLng != null) {
                        totalBounds.include(new LatLng(movement.startLat, movement.startLng));
                    }
                    if (movement.endLat != null && movement.endLng != null) {
                        totalBounds.include(new LatLng(movement.endLat, movement.endLng));
                    }
                    if (points != null) {
                        for (RoutePoint p : points) {
                            totalBounds.include(new LatLng(p.lat, p.lng));
                        }
                    }
                    if (movement.stops != null) {
                        for (StillLocation stop : movement.stops) {
                            if (stop.lat != null && stop.lng != null) {
                                totalBounds.include(new LatLng(stop.lat, stop.lng));
                            }
                        }
                    }
                }
            }

            fragment.requireActivity().runOnUiThread(() -> {
                try {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(totalBounds.build(), 100));
                } catch (Exception ignored) {}
            });
        });
    }

    private LatLngBounds drawMovementOnMap(MovementActivity movement, List<RoutePoint> routePoints, int number, boolean useIcon) {
        if (mMap == null) return null;

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        boolean hasPoints = false;
        int color = getMovementColor(movement.activityTypeName);

        // Add marker for start position
        if (movement.startLat != null && movement.startLng != null) {
            LatLng start = new LatLng(movement.startLat, movement.startLng);

            BitmapDescriptor icon;
            if (useIcon) {
                icon = getIconMarkerIcon(getMovementIconRes(movement.activityTypeName), color);
            } else {
                icon = getNumberedMarkerIcon(number, color);
            }

            mMap.addMarker(new MarkerOptions()
                    .position(start)
                    .title((useIcon ? "" : (number + ". ")) + "Start: " + movement.activityTypeName)
                    .icon(icon));
            builder.include(start);
            hasPoints = true;
        }

        // Draw Polyline
        PolylineOptions polylineOptions = new PolylineOptions()
                .color(color)
                .width(10)
                .geodesic(true);

        if (routePoints != null && !routePoints.isEmpty()) {
            for (RoutePoint p : routePoints) {
                LatLng latLng = new LatLng(p.lat, p.lng);
                polylineOptions.add(latLng);
                builder.include(latLng);
                hasPoints = true;
            }
        } else if (movement.startLat != null && movement.startLng != null && movement.endLat != null && movement.endLng != null) {
            // Straight line if no route points
            polylineOptions.add(new LatLng(movement.startLat, movement.startLng));
            polylineOptions.add(new LatLng(movement.endLat, movement.endLng));
            hasPoints = true;
        }

        if (hasPoints) {
            mMap.addPolyline(polylineOptions);
        }

        // Add markers for nested stops
        if (movement.stops != null && !movement.stops.isEmpty()) {
            for (StillLocation stop : movement.stops) {
                if (stop.lat != null && stop.lng != null) {
                    LatLng stopPos = new LatLng(stop.lat, stop.lng);
                    String stopTitle = (stop.placeName != null) ? stop.placeName : "Stop";

                    // Determine the color for the stop location
                    int stopColor = getStillColor(stop, fragment.requireContext());

                    BitmapDescriptor stopIcon = getIconMarkerIcon(getStillIconRes(stop), stopColor);

                    mMap.addMarker(new MarkerOptions()
                            .position(stopPos)
                            .title("Stop: " + stopTitle)
                            .icon(stopIcon));
                    builder.include(stopPos);
                    hasPoints = true;
                }
            }
        }

        // Add marker for end position
        if (movement.endLat != null && movement.endLng != null) {
            LatLng end = new LatLng(movement.endLat, movement.endLng);

            BitmapDescriptor icon;
            if (useIcon) {
                icon = getIconMarkerIcon(getMovementIconRes(movement.activityTypeName), color);
            } else {
                icon = getNumberedMarkerIcon(number, color);
            }

            mMap.addMarker(new MarkerOptions()
                    .position(end)
                    .title((useIcon ? "" : (number + ". ")) + "End: " + movement.activityTypeName)
                    .icon(icon));
            builder.include(end);
            hasPoints = true;
        }

        return hasPoints ? builder.build() : null;
    }

    private int getMovementColor(String type) {
        if (type == null) return Color.BLUE;
        String t = type.toLowerCase();
        if (t.contains("driving") || t.contains("vehicle")) return Color.parseColor("#4285F4"); // Google Blue
        if (t.contains("walking") || t.contains("foot")) return Color.parseColor("#0F9D58"); // Google Green
        if (t.contains("running")) return Color.parseColor("#DB4437"); // Google Red
        if (t.contains("cycling") || t.contains("bicycle")) return Color.parseColor("#F4B400"); // Google Yellow
        return Color.GRAY;
    }



    private void updateMyLocationEnabled() {
        // code taken from google
        if (mMap != null && ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                mMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException setting my location enabled", e);
            }
        }
    }
}
