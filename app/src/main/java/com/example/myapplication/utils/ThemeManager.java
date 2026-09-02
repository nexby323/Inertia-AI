package com.example.myapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
/**
 * Global Configuration Utility (Helper Pattern) responsible for theme management.
 * This class centralizes the control and application of the application's visual layout,
 * specifically enforcing the high-contrast Dark Theme across all decoupled Activities
 * (e.g., MainActivity, SensorTrackingActivity, and HistoryActivity).
 * * * Architectural Principle: Single Responsibility Principle (SRP) - by isolating theme
 * switching logic from individual UI controllers, it guarantees a strict global visual
 * baseline and prevents UI-state bugs during runtime configuration changes.
 */
public class ThemeManager {
    private static final String PREF_NAME = "theme_prefs";
    private static final String KEY_IS_NIGHT_MODE = "isNightMode";
    private final SharedPreferences prefs;
    /**
     * Programmatically enforces the global visual styling constraints onto a specified Context.
     * Dynamically injects color assets, window attributes, and component styles (such as the
     * InertiaAI signature Cyan and Dark backgrounds-open for changes) before the layout inflation phase.
     *
     * @param context The active Android Context (usually the calling Activity) where the
     * visual theme properties must be applied.
     */

    public ThemeManager(Context context) {
        /*
            connect to the memory of the application
            this method get the name of the Preference in this case theme_prefs
            to get the data for the theme (this is a file name)
            and the mode MODE_PRIVATE is the default for this use case (just) get access with
            the user ID of this application (No other application access this preference) to the file
            also this object is follow the singleton pattern design and it is safe-thread

        */
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // set the night mode to on or off and call other method applyTheme
    //the apply method must be called to commit the changes.
    public void setNightMode(boolean isNight) {
        prefs.edit().putBoolean(KEY_IS_NIGHT_MODE, isNight).apply();
        applyTheme(isNight);
    }

    /**
     * Persists or evaluates the current user preference state regarding the system's look and feel.
     * Acts as a gateway to check whether the application should render in Dark Mode configuration,
     * maintaining consistency between device system settings and cloud-native profile overrides.
     *
     * @return true if the system or user override mandates a dark visual configuration; false otherwise.
     */
    public boolean isNightMode() {
        return prefs.getBoolean(KEY_IS_NIGHT_MODE, true);
    }

    //this method just make all the application on night mode
    /*
    AppCompatDelegate like the name , AppCompat means support on the older versions of android
    AppCompat take new features and translate them into older versions
    Delegate means, instead that all the activities will manage their UI/UX (in this case)
    they call an outside object to manage all this
    this is like a stage manager, when the app is running it call to colors.xml their night color are defined
     */
    public void applyTheme(boolean isNight) {
        if (isNight) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}