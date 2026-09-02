package com.example.myapplication.analytics;

/**
 * The core analytics engine of the InertiaAI system.
 * This component acts as an Expert System within the Analytics Layer,
 * responsible for transforming raw, filtered physical variables into
 * actionable physiological insights and computational metrics.
 * * Architecture Principle: High Cohesion & Low Coupling - it contains zero
 * Android framework dependencies, making it pure, testable mathematical logic.
 */
public class WorkoutAnalyzer {

    /**
     * Classifies the biometric physical strain and workout intensity based on step frequency.
     * Implements a deterministic rule-based classification model to map raw Cadence
     * (Steps Per Minute) into validated physiological activity zones.
     * Open to updates for more professional classification or to get more data and then classify.
     *
     * @param cadence The calculated cadence in Steps Per Minute (SPM).
     * @return A standardized string representing the user's current intensity zone.
     */
    public static String getIntensityLevel(int cadence) {
        if (cadence == 0) return "Resting";
        if (cadence < 100) return "Light Walk";
        if (cadence <= 130) return "Brisk Walk / Jogging";
        if (cadence <= 160) return "Running";
        return "Sprint / High Intensity";
    }

    /**
     * Computational scoring algorithm that rates the mathematical "quality" of a workout session.
     * Formulates a specialized fitness metric by normalizing total workload (steps),
     * factoring in effort multipliers (cadence), and applying explicit execution penalties
     * based on kinetic alignment and biomechanical posture errors (pitch drift).
     *
     * @param steps   Total accumulated steps throughout the execution window.
     * @param cadence The calculated dominant step frequency (SPM).
     * @param pitch   The average skeletal tilt/posture angle computed via sensor fusion.
     * @return An integer representing the normalized workout quality score (0 to 100+).
     */
    public static int calculateWorkoutScore(int steps, int cadence, double pitch) {
        int score = 0;


        int posturePenalty = Math.abs(pitch) > 15 ? 15 : 0;

        // give bonus to more cadence (greater pace)
        double cadenceMultiplier = cadence > 120 ? 1.5 : 1.0;

        // give a score according to the number of steps and the cadence and the posture
        score = (int) ((steps / 10.0) * cadenceMultiplier) - posturePenalty;

        return Math.max(0, score); // grade cannot be less then zero
    }
}