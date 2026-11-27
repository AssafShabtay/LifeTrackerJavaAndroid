package com.trace.memory.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.trace.R;

/**
 * Categories understood by the Experience Engine. Stored by {@link #name()}.
 * Labels are deliberately human and non-judgemental.
 */
public enum ExperienceCategory {
    VISIT("Visit", R.drawable.ic_tr_place),
    DINING("Dining", R.drawable.ic_tr_dining),
    CAFE("Café", R.drawable.ic_tr_cafe),
    CULTURE("Culture", R.drawable.ic_tr_museum),
    NIGHTLIFE("Evening out", R.drawable.ic_tr_nightlife),
    SHOPPING("Shopping", R.drawable.ic_tr_shopping),
    OUTDOORS("Outdoors", R.drawable.ic_tr_mountain),
    WALK("Walk", R.drawable.ic_tr_walk),
    RUN("Run", R.drawable.ic_tr_run),
    RIDE("Ride", R.drawable.ic_tr_bike),
    JOURNEY("Journey", R.drawable.ic_tr_route),
    EXPLORING("Exploring", R.drawable.ic_tr_explore),
    STAY("Stay", R.drawable.ic_tr_stay),
    ROUTINE("Everyday", R.drawable.ic_tr_routine),
    HOME("At home", R.drawable.ic_tr_home),
    SOCIAL("With people", R.drawable.ic_tr_people),
    CELEBRATION("Celebration", R.drawable.ic_tr_celebration),
    CUSTOM("Moment", R.drawable.ic_tr_spark);

    public final String label;
    @DrawableRes
    public final int icon;

    ExperienceCategory(String label, int icon) {
        this.label = label;
        this.icon = icon;
    }

    @NonNull
    public static ExperienceCategory from(String name) {
        if (name == null) return VISIT;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return VISIT;
        }
    }

    /** Categories a user may choose when creating or correcting an experience. */
    public static ExperienceCategory[] userSelectable() {
        return new ExperienceCategory[]{
                CUSTOM, VISIT, DINING, CAFE, CULTURE, NIGHTLIFE, SHOPPING, OUTDOORS,
                WALK, RUN, RIDE, JOURNEY, EXPLORING, STAY, SOCIAL, CELEBRATION, HOME, ROUTINE
        };
    }
}
