package com.example.myapplication.helpers;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.mainScreen.IconPickerDialog;
import com.example.myapplication.mainScreen.IconPickerDialog.IconItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ColorAndIcons {



    public static final int[] DEFAULT_COLORS = {
            R.color.green,
            R.color.blue,
            R.color.orange,
            R.color.yellow,
            R.color.red,
            R.color.mint,
            R.color.pink,
            R.color.purple,
    };

    public static int getStillColor(StillLocation still, Context context) {
        if (still.color != null) {
            return still.color;
        } else if (still.isStop) { // If it's a stop, use activity_stop
            return ContextCompat.getColor(context, R.color.activity_stop); //TODO CHANGE ACTIVITY STOP
        } else if("gym".equals(still.category)){
            return ContextCompat.getColor(context, R.color.gym);
        } else {
            // If there is no color assigned to still, use the default colors
            int index = (int) (Math.abs(still.id) % DEFAULT_COLORS.length);
            return ContextCompat.getColor(context, DEFAULT_COLORS[index]);
        }
    }
    public static int getStillIconRes(StillLocation still) {
        int iconXml = R.drawable.ic_still;
        if (still.icon != null) {
            String icon = still.icon.toLowerCase();

            // Base / Original Places
            if (icon.contains("home")) iconXml = R.drawable.ic_home;

            else if (icon.contains("briefcase")) iconXml = R.drawable.lucide_briefcase;
            else if (icon.contains("gym") || icon.contains("fitness")) iconXml = R.drawable.ic_gym; // Alternatively: material_icons_fitness_center
            else if (icon.contains("school")) iconXml = R.drawable.ic_school; // Alternatively: lucide_school
            else if (icon.contains("restaurant") || icon.contains("food")) iconXml = R.drawable.ic_restaurant; // Alternatively: material_icons_food_bank
            else if (icon.contains("cafe") || icon.contains("coffee")) iconXml = R.drawable.ic_coffee;

                // Vehicles & Transportation
            else if (icon.contains("airplane")) iconXml = R.drawable.bootstrap_airplane;
            else if (icon.contains("plane")) iconXml = R.drawable.lucide_plane;
            else if (icon.contains("helicopter")) iconXml = R.drawable.lucide_helicopter;
            else if (icon.contains("scooter")) iconXml = R.drawable.bootstrap_scooter;
            else if (icon.contains("taxi")) iconXml = R.drawable.bootstrap_taxi_front;
            else if (icon.contains("ambulance")) iconXml = R.drawable.lucide_ambulance;
            else if (icon.contains("bike") || icon.contains("bicycle")) iconXml = R.drawable.lucide_bike;
            else if (icon.contains("bus")) iconXml = R.drawable.lucide_bus;
            else if (icon.contains("caravan")) iconXml = R.drawable.lucide_caravan; // Must be checked before "car"
            else if (icon.contains("car")) iconXml = R.drawable.ic_car;
            else if (icon.contains("tram")) iconXml = R.drawable.material_symbols_tram;
            else if (icon.contains("truck")) iconXml = R.drawable.lucide_truck;
            else if (icon.contains("van")) iconXml = R.drawable.lucide_van;
            else if (icon.contains("ship") || icon.contains("boat")) iconXml = R.drawable.lucide_ship;
            else if (icon.contains("kayak")) iconXml = R.drawable.lucide_kayak;

                // Places & Buildings
            else if (icon.contains("hospital") || icon.contains("medical")) iconXml = R.drawable.lucide_hospital;
            else if (icon.contains("house")) iconXml = R.drawable.lucide_house;
            else if (icon.contains("apartment")) iconXml = R.drawable.material_icons_apartment;
            else if (icon.contains("villa")) iconXml = R.drawable.material_icons_villa;
            else if (icon.contains("castle")) iconXml = R.drawable.lucide_castle;
            else if (icon.contains("church")) iconXml = R.drawable.lucide_church;
            else if (icon.contains("library")) iconXml = R.drawable.lucide_library_big;
            else if (icon.contains("store")) iconXml = R.drawable.lucide_store;
            else if (icon.contains("warehouse")) iconXml = R.drawable.lucide_warehouse;
            else if (icon.contains("casino")) iconXml = R.drawable.material_icons_casino;
            else if (icon.contains("spa")) iconXml = R.drawable.material_icons_spa;
            else if (icon.contains("bar") || icon.contains("pub")) iconXml = R.drawable.material_icons_sports_bar;

                // Sports, Outdoors & Recreation
            else if (icon.contains("golf")) iconXml = R.drawable.material_icons_golf_course;
            else if (icon.contains("basketball")) iconXml = R.drawable.material_icons_sports_basketball;
            else if (icon.contains("esport") || icon.contains("gaming")) iconXml = R.drawable.material_icons_sports_esports;
            else if (icon.contains("martial")) iconXml = R.drawable.material_icons_sports_martial_arts;
            else if (icon.contains("mma")) iconXml = R.drawable.material_icons_sports_mma;
            else if (icon.contains("football") || icon.contains("soccer")) iconXml = R.drawable.material_symbols_sports_football;
            else if (icon.contains("tennis")) iconXml = R.drawable.material_symbols_sports_tennis;
            else if (icon.contains("volleyball")) iconXml = R.drawable.lucide_volleyball;
            else if (icon.contains("outdoor")) iconXml = R.drawable.material_symbols_sports_and_outdoors;
            else if (icon.contains("fishing")) iconXml = R.drawable.lucide_fishing_rod;
            else if (icon.contains("hook")) iconXml = R.drawable.lucide_fishing_hook;
            else if (icon.contains("ferris")) iconXml = R.drawable.lucide_ferris_wheel;
            else if (icon.contains("mountain")) iconXml = R.drawable.lucide_mountain;
            else if (icon.contains("tent") || icon.contains("camping")) iconXml = R.drawable.lucide_tent;
            else if (icon.contains("walk")) iconXml = R.drawable.ic_walk;

                // Objects, Lifestyle & Utility
            else if (icon.contains("backpack")) iconXml = R.drawable.bootstrap_backpack;
            else if (icon.contains("passport")) iconXml = R.drawable.bootstrap_passport;

            else if (icon.contains("handbag") || icon.contains("bag")) iconXml = R.drawable.lucide_handbag;
            else if (icon.contains("luggage")) iconXml = R.drawable.lucide_luggage;
            else if (icon.contains("bed")) iconXml = R.drawable.lucide_bed_single;
            else if (icon.contains("sofa") || icon.contains("couch")) iconXml = R.drawable.lucide_sofa;
            else if (icon.contains("lamp")) iconXml = R.drawable.lucide_lamp;
            else if (icon.contains("toilet") || icon.contains("restroom")) iconXml = R.drawable.lucide_toilet;
            else if (icon.contains("cigarette") || icon.contains("smoke")) iconXml = R.drawable.lucide_cigarette;
            else if (icon.contains("trophy")) iconXml = R.drawable.lucide_trophy;
            else if (icon.contains("breakfast")) iconXml = R.drawable.material_icons_free_breakfast;
            else if (icon.contains("book")) iconXml = R.drawable.tabler_books;
            else if (icon.contains("ev") || icon.contains("charger")) iconXml = R.drawable.lucide_ev_charger;
            else if (icon.contains("fuel") || icon.contains("gas")) iconXml = R.drawable.lucide_fuel;

                // UI, Map & Nav Elements
            else if (icon.contains("location")) iconXml = R.drawable.ic_location_on;
            else if (icon.contains("pin")) iconXml = R.drawable.lucide_map_pin;
            else if (icon.contains("map")) iconXml = R.drawable.lucide_map;
            else if (icon.contains("compass")) iconXml = R.drawable.lucide_compass;
            else if (icon.contains("setting")) iconXml = R.drawable.ic_settings;
            else if (icon.contains("statistic")) iconXml = R.drawable.ic_statistics;
        }
        return iconXml;
    }

    public static List<IconItem> getIconList() {
        return Arrays.asList(
                // Basic & Original
                new IconItem("Home", R.drawable.ic_home, Arrays.asList("house", "residence", "dwelling")),
                new IconItem("Briefcase", R.drawable.lucide_briefcase, Arrays.asList("work", "job", "business", "office")),
                new IconItem("Gym", R.drawable.ic_gym, Arrays.asList("fitness", "workout", "exercise", "weights")),
                new IconItem("School", R.drawable.ic_school, Arrays.asList("education", "college", "university", "study")),
                new IconItem("Restaurant", R.drawable.ic_restaurant, Arrays.asList("food", "eat", "dining", "cafe")),
                new IconItem("Coffee", R.drawable.ic_coffee, Arrays.asList("cafe", "drink", "tea", "break")),
                new IconItem("Walk", R.drawable.ic_walk, Arrays.asList("walking", "stroll", "hike")),
                new IconItem("Still", R.drawable.ic_still, Arrays.asList("stationary", "idle", "pause")),
                // Transportation (Bootstrap & Lucide)
                new IconItem("Airplane", R.drawable.bootstrap_airplane, Arrays.asList("plane", "flight", "airport", "travel")),
                new IconItem("Ambulance", R.drawable.lucide_ambulance, Arrays.asList("medical", "emergency", "hospital")),
                new IconItem("Bike", R.drawable.lucide_bike, Arrays.asList("bicycle", "cycling", "ride")),
                new IconItem("Bus", R.drawable.lucide_bus, Arrays.asList("public transport", "coach")),
                new IconItem("Car", R.drawable.ic_car, Arrays.asList("automobile", "vehicle", "drive")),
                new IconItem("Caravan", R.drawable.lucide_caravan, Arrays.asList("rv", "camper", "trailer")),
                new IconItem("Helicopter", R.drawable.lucide_helicopter, Collections.emptyList()),
                new IconItem("Kayak", R.drawable.lucide_kayak, Arrays.asList("boat", "paddle", "water")),
                new IconItem("Plane", R.drawable.lucide_plane, Arrays.asList("airplane", "flight", "airport", "travel")),
                new IconItem("Scooter", R.drawable.bootstrap_scooter, Arrays.asList("motorcycle", "moped")),
                new IconItem("Ship", R.drawable.lucide_ship, Arrays.asList("boat", "ocean", "cruise")),
                new IconItem("Taxi", R.drawable.bootstrap_taxi_front, Arrays.asList("cab", "ride", "transport")),
                new IconItem("Tram", R.drawable.material_symbols_tram, Arrays.asList("streetcar", "trolley")),
                new IconItem("Truck", R.drawable.lucide_truck, Arrays.asList("lorry", "delivery", "freight")),
                new IconItem("Van", R.drawable.lucide_van, Arrays.asList("minivan", "panel van")),

                // Places & Buildings (Lucide & Material)
                new IconItem("Apartment", R.drawable.material_icons_apartment, Arrays.asList("flat", "condo", "building")),
                new IconItem("Casino", R.drawable.material_icons_casino, Arrays.asList("gambling", "gaming", "resort")),
                new IconItem("Castle", R.drawable.lucide_castle, Arrays.asList("fortress", "palace")),
                new IconItem("Church", R.drawable.lucide_church, Arrays.asList("temple", "mosque", "synagogue", "religion")),
                new IconItem("Hospital", R.drawable.lucide_hospital, Arrays.asList("medical", "clinic", "doctor")),

                new IconItem("House", R.drawable.lucide_house, Arrays.asList("home", "residence", "dwelling")),
                new IconItem("Library", R.drawable.lucide_library_big, Arrays.asList("books", "study", "read")),
                // Note: School already defined with keywords above. Duplicates may cause issues, but for keyword searching it's okay
                // For now, I'll assume the user wants both the `ic_school` and `lucide_school` to be searchable as "School"
                // I will add another one for lucide_school as well, just in case there's a different asset.
                // For now, keeping the `School` icon reference as is and just adding keywords. If there are two distinct R.drawable.ic_school and R.drawable.lucide_school, I should make two IconItems.
                // Based on the code, `R.drawable.ic_school` is used for "School". `lucide_school` is commented as alternative. So, I will stick to `R.drawable.ic_school`

                new IconItem("Spa", R.drawable.material_icons_spa, Arrays.asList("massage", "wellness", "relax")),
                new IconItem("Sports Bar", R.drawable.material_icons_sports_bar, Arrays.asList("bar", "pub", "drinks", "sports")),
                new IconItem("Store", R.drawable.lucide_store, Arrays.asList("shop", "market", "retail")),
                new IconItem("Villa", R.drawable.material_icons_villa, Arrays.asList("house", "mansion", "estate")),
                new IconItem("Warehouse", R.drawable.lucide_warehouse, Arrays.asList("storage", "factory", "depot")),

                // Sports, Outdoors & Recreation
                new IconItem("Basketball", R.drawable.material_icons_sports_basketball, Arrays.asList("sport", "hoops", "ball")),
                new IconItem("Esports", R.drawable.material_icons_sports_esports, Arrays.asList("gaming", "video games", "competition")),
                new IconItem("Ferris Wheel", R.drawable.lucide_ferris_wheel, Arrays.asList("amusement park", "fair", "ride")),
                new IconItem("Fishing Hook", R.drawable.lucide_fishing_hook, Arrays.asList("fishing", "bait")),
                new IconItem("Fishing Rod", R.drawable.lucide_fishing_rod, Arrays.asList("fishing", "angler")),
                new IconItem("Football", R.drawable.material_symbols_sports_football, Arrays.asList("soccer", "sport", "ball")),
                new IconItem("Golf Course", R.drawable.material_icons_golf_course, Arrays.asList("golf", "sport", "club")),
                new IconItem("Martial Arts", R.drawable.material_icons_sports_martial_arts, Arrays.asList("karate", "judo", "kung fu", "combat")),
                new IconItem("MMA", R.drawable.material_icons_sports_mma, Arrays.asList("fighting", "combat", "ufc")),
                new IconItem("Mountain", R.drawable.lucide_mountain, Arrays.asList("hike", "climb", "nature")),
                new IconItem("Sports & Outdoors", R.drawable.material_symbols_sports_and_outdoors, Arrays.asList("sport", "activity", "park")),
                new IconItem("Tennis", R.drawable.material_symbols_sports_tennis, Arrays.asList("sport", "racket", "court")),
                new IconItem("Tent", R.drawable.lucide_tent, Arrays.asList("camping", "outdoors", "shelter")),
                new IconItem("Volleyball", R.drawable.lucide_volleyball, Arrays.asList("sport", "beach")),

                // Items & Lifestyle
                new IconItem("Backpack", R.drawable.bootstrap_backpack, Arrays.asList("bag", "school bag", "travel bag")),
                new IconItem("Bed", R.drawable.lucide_bed_single, Arrays.asList("sleep", "bedroom", "rest")),
                new IconItem("Books", R.drawable.tabler_books, Arrays.asList("reading", "library", "study")),
                new IconItem("Breakfast", R.drawable.material_icons_free_breakfast, Arrays.asList("food", "morning", "meal")),
                new IconItem("Cigarette", R.drawable.lucide_cigarette, Arrays.asList("smoke", "tobacco")),
                new IconItem("EV Charger", R.drawable.lucide_ev_charger, Arrays.asList("electric vehicle", "charging")),
                new IconItem("Fuel", R.drawable.lucide_fuel, Arrays.asList("gas", "petrol", "gas station")),
                new IconItem("Handbag", R.drawable.lucide_handbag, Arrays.asList("purse", "bag", "fashion")),
                new IconItem("Lamp", R.drawable.lucide_lamp, Arrays.asList("light", "illumination")),
                new IconItem("Luggage", R.drawable.lucide_luggage, Arrays.asList("suitcase", "travel", "bag")),
                new IconItem("Passport", R.drawable.bootstrap_passport, Arrays.asList("id", "travel document")),
                new IconItem("Sofa", R.drawable.lucide_sofa, Arrays.asList("couch", "furniture", "living room")),
                new IconItem("Toilet", R.drawable.lucide_toilet, Arrays.asList("restroom", "bathroom", "WC")),
                new IconItem("Trophy", R.drawable.lucide_trophy, Arrays.asList("award", "prize", "winner")),

                // Map & Nav UI
                new IconItem("Compass", R.drawable.lucide_compass, Arrays.asList("direction", "navigate")),
                new IconItem("Map", R.drawable.lucide_map, Arrays.asList("navigate", "location", "directions")),
                new IconItem("Map Pin", R.drawable.lucide_map_pin, Arrays.asList("location", "marker", "pin")),
                new IconItem("Settings", R.drawable.ic_settings, Arrays.asList("gear", "preferences", "options")),
                new IconItem("Statistics", R.drawable.ic_statistics, Arrays.asList("charts", "data", "analytics"))

        );
    }
}