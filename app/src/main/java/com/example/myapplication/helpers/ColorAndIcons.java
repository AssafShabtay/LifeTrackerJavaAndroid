package com.example.myapplication.helpers;

import com.example.myapplication.R;
import com.example.myapplication.database.StillLocation;
import com.example.myapplication.mainScreen.IconPickerDialog;
import com.example.myapplication.mainScreen.IconPickerDialog.IconItem;
import java.util.Arrays;
import java.util.List;

public class ColorAndIcons {

    public static int getStillIconRes(StillLocation still) {
        int iconXml = R.drawable.ic_still;
        if (still.icon != null) {
            String icon = still.icon.toLowerCase();

            // Base / Original Places
            if (icon.contains("home")) iconXml = R.drawable.ic_home;
            else if (icon.contains("work")) iconXml = R.drawable.ic_work;
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
            else if (icon.contains("car")) iconXml = R.drawable.lucide_car_front;
            else if (icon.contains("tram")) iconXml = R.drawable.lucide_tram_front;
            else if (icon.contains("truck")) iconXml = R.drawable.lucide_truck;
            else if (icon.contains("van")) iconXml = R.drawable.lucide_van;
            else if (icon.contains("ship") || icon.contains("boat")) iconXml = R.drawable.lucide_ship;
            else if (icon.contains("kayak")) iconXml = R.drawable.lucide_kayak;

                // Places & Buildings
            else if (icon.contains("hospital") || icon.contains("medical")) iconXml = R.drawable.lucide_hospital;
            else if (icon.contains("hotel")) iconXml = R.drawable.lucide_hotel;
            else if (icon.contains("house")) iconXml = R.drawable.lucide_house;
            else if (icon.contains("apartment")) iconXml = R.drawable.material_icons_apartment;
            else if (icon.contains("villa")) iconXml = R.drawable.material_icons_villa;
            else if (icon.contains("castle")) iconXml = R.drawable.lucide_castle;
            else if (icon.contains("church")) iconXml = R.drawable.lucide_church;
            else if (icon.contains("library")) iconXml = R.drawable.lucide_library_big;
            else if (icon.contains("storefront")) iconXml = R.drawable.material_icons_storefront; // Must be before "store"
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
            else if (icon.contains("briefcase")) iconXml = R.drawable.lucide_briefcase;
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
                new IconItem("Home", R.drawable.ic_home),
                new IconItem("Work", R.drawable.ic_work),
                new IconItem("Gym", R.drawable.ic_gym),
                new IconItem("School", R.drawable.ic_school),
                new IconItem("Restaurant", R.drawable.ic_restaurant),
                new IconItem("Coffee", R.drawable.ic_coffee),
                new IconItem("Walk", R.drawable.ic_walk),
                new IconItem("Still", R.drawable.ic_still),
                new IconItem("Location On", R.drawable.ic_location_on),
                new IconItem("Settings", R.drawable.ic_settings),
                new IconItem("Statistics", R.drawable.ic_statistics),

                // Transportation (Bootstrap & Lucide)
                new IconItem("Airplane", R.drawable.bootstrap_airplane),
                new IconItem("Ambulance", R.drawable.lucide_ambulance),
                new IconItem("Bike", R.drawable.lucide_bike),
                new IconItem("Bus", R.drawable.lucide_bus),
                new IconItem("Car", R.drawable.lucide_car_front),
                new IconItem("Caravan", R.drawable.lucide_caravan),
                new IconItem("Helicopter", R.drawable.lucide_helicopter),
                new IconItem("Kayak", R.drawable.lucide_kayak),
                new IconItem("Plane", R.drawable.lucide_plane),
                new IconItem("Scooter", R.drawable.bootstrap_scooter),
                new IconItem("Ship", R.drawable.lucide_ship),
                new IconItem("Taxi", R.drawable.bootstrap_taxi_front),
                new IconItem("Tram", R.drawable.lucide_tram_front),
                new IconItem("Truck", R.drawable.lucide_truck),
                new IconItem("Van", R.drawable.lucide_van),

                // Places & Buildings (Lucide & Material)
                new IconItem("Apartment", R.drawable.material_icons_apartment),
                new IconItem("Casino", R.drawable.material_icons_casino),
                new IconItem("Castle", R.drawable.lucide_castle),
                new IconItem("Church", R.drawable.lucide_church),
                new IconItem("Fitness Center", R.drawable.material_icons_fitness_center),
                new IconItem("Food Bank", R.drawable.material_icons_food_bank),
                new IconItem("Hospital", R.drawable.lucide_hospital),
                new IconItem("Hotel", R.drawable.lucide_hotel),
                new IconItem("House", R.drawable.lucide_house),
                new IconItem("Library", R.drawable.lucide_library_big),
                new IconItem("School (Lucide)", R.drawable.lucide_school),
                new IconItem("Spa", R.drawable.material_icons_spa),
                new IconItem("Sports Bar", R.drawable.material_icons_sports_bar),
                new IconItem("Store", R.drawable.lucide_store),
                new IconItem("Storefront", R.drawable.material_icons_storefront),
                new IconItem("Villa", R.drawable.material_icons_villa),
                new IconItem("Warehouse", R.drawable.lucide_warehouse),

                // Sports, Outdoors & Recreation
                new IconItem("Basketball", R.drawable.material_icons_sports_basketball),
                new IconItem("Esports", R.drawable.material_icons_sports_esports),
                new IconItem("Ferris Wheel", R.drawable.lucide_ferris_wheel),
                new IconItem("Fishing Hook", R.drawable.lucide_fishing_hook),
                new IconItem("Fishing Rod", R.drawable.lucide_fishing_rod),
                new IconItem("Football", R.drawable.material_symbols_sports_football),
                new IconItem("Golf Course", R.drawable.material_icons_golf_course),
                new IconItem("Martial Arts", R.drawable.material_icons_sports_martial_arts),
                new IconItem("MMA", R.drawable.material_icons_sports_mma),
                new IconItem("Mountain", R.drawable.lucide_mountain),
                new IconItem("Sports & Outdoors", R.drawable.material_symbols_sports_and_outdoors),
                new IconItem("Tennis", R.drawable.material_symbols_sports_tennis),
                new IconItem("Tent", R.drawable.lucide_tent),
                new IconItem("Volleyball", R.drawable.lucide_volleyball),

                // Items & Lifestyle
                new IconItem("Backpack", R.drawable.bootstrap_backpack),
                new IconItem("Bed", R.drawable.lucide_bed_single),
                new IconItem("Books", R.drawable.tabler_books),
                new IconItem("Breakfast", R.drawable.material_icons_free_breakfast),
                new IconItem("Briefcase", R.drawable.lucide_briefcase),
                new IconItem("Cigarette", R.drawable.lucide_cigarette),
                new IconItem("EV Charger", R.drawable.lucide_ev_charger),
                new IconItem("Fuel", R.drawable.lucide_fuel),
                new IconItem("Handbag", R.drawable.lucide_handbag),
                new IconItem("Lamp", R.drawable.lucide_lamp),
                new IconItem("Luggage", R.drawable.lucide_luggage),
                new IconItem("Passport", R.drawable.bootstrap_passport),
                new IconItem("Sofa", R.drawable.lucide_sofa),
                new IconItem("Toilet", R.drawable.lucide_toilet),
                new IconItem("Trophy", R.drawable.lucide_trophy),

                // Map & Nav UI
                new IconItem("Compass", R.drawable.lucide_compass),
                new IconItem("Map", R.drawable.lucide_map),
                new IconItem("Map Pin", R.drawable.lucide_map_pin)
        );
    }
}
