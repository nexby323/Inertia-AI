package com.example.myapplication.engine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.myapplication.analytics.WorkoutAnalyzer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The primary execution engine of the application, running as an isolated Foreground Service.
 * This class encapsulates the Digital Signal Processing (DSP) pipeline and manages low-level
 * hardware hooks for the Accelerometer and Gyroscope sensors.
 * * Technical Design:
 * 1. Executes continuous background tracking independent of the UI thread to prevent frames dropping.
 * 2. Implements a Complementary Filter for real-time sensor fusion and posture analysis.
 * 3. Utilizes an optimized Discrete Fourier Transform (DFT) window for step frequency detection.
 * all explain in depth on the project book
 */
public class TrackingService extends Service implements SensorEventListener, TextToSpeech.OnInitListener {

    // Identifiers required by the Android OS to maintain a Foreground Service state
    private static final String CHANNEL_ID = "InertiaTrackingChannel";
    private static final int NOTIFICATION_ID = 1;

    // Broadcast Action Strings - The "Radio Frequencies" used to communicate with the Activity
    public static final String ACTION_WORKOUT_UPDATE = "com.example.myapplication.WORKOUT_UPDATE";
    public static final String ACTION_STOP_AND_SAVE = "com.example.myapplication.STOP_AND_SAVE";
    public static final String ACTION_SAVE_COMPLETE = "com.example.myapplication.SAVE_COMPLETE";

    // --- Sensors and Hardware Access ---
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private TextToSpeech tts;

    // --- Thread-Safe Data Structures ---
    private ArrayBlockingQueue<SensorSample> sensorQueue;
    private float[] gyroscopeDataForSynchronize;
    private java.util.Deque<Double> dequeMagnitudes;

    // --- Algorithm & State Variables ---
    private int stepCount = 0;
    private int lastKnownCadence = 0;
    private long lastStepTime = 0;
    private long lastSensorTime = 0;
    private double currentAnglePitch = 0;

    private ScheduledExecutorService backgroundProcessor;

    // Biomechanical thresholds
    private static final double STEP_MOVMENT_TRESHHOLD = 3.5 + 9.8;
    private static final double STEP_TIME_TRESHHOLD = 300;
    private static final int MAX_ARRAY_SIZE = 1000;

    private class SensorSample {
        float XLinearAcc, YLinearAcc, ZLinearAcc, gyroX, gyroY, gyroZ;
        long timeStamp;

        public SensorSample(float XLinearAcc, float YLinearAcc, float ZLinearAcc, float gyroX, float gyroY, float gyroZ, long timeStamp) {
            this.XLinearAcc = XLinearAcc; this.YLinearAcc = YLinearAcc; this.ZLinearAcc = ZLinearAcc;
            this.gyroX = gyroX; this.gyroY = gyroY; this.gyroZ = gyroZ; this.timeStamp = timeStamp;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        this.tts = new TextToSpeech(this, this);
        this.sensorQueue = new ArrayBlockingQueue<>(MAX_ARRAY_SIZE);
        this.gyroscopeDataForSynchronize = new float[3];
        this.dequeMagnitudes = new ArrayDeque<>(256);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (this.sensorManager != null) {
            this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            this.gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // 1. SAFE SHUTDOWN ROUTE
        if (intent != null && ACTION_STOP_AND_SAVE.equals(intent.getAction())) {
            stopTrackingAndSave();
            return START_NOT_STICKY;
        }

        // 2. NORMAL STARTUP ROUTE: Establish VIP Foreground status
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Inertia Tracking")
                .setContentText("Workout in progress...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        if (accelerometer != null && gyroscope != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }

        backgroundProcessor = Executors.newSingleThreadScheduledExecutor();
        backgroundProcessor.scheduleAtFixedRate(this::processSensorData, 0, 500, TimeUnit.MILLISECONDS);

        return START_STICKY;
    }

    /*
        The Consumer: Polls the queue safely, runs DFT (with Rest Detection),
        applies the Complementary Filter, and broadcasts the processed results.
     */
    /*
        The Consumer: Polls the queue safely, runs DFT (with Rest Detection),
        applies the Complementary Filter, and broadcasts the processed results.
     */
    private void processSensorData() {
        if (this.sensorQueue.isEmpty()) return;

        int numberOfStepsDetected = 0, numberOfMagnitudeSamples = 256;
        SensorSample currentSample;
        ArrayList<Double> newMagnitudes = new ArrayList<>();

        while ((currentSample = this.sensorQueue.poll()) != null) {
            float x = currentSample.XLinearAcc;
            float y = currentSample.YLinearAcc;
            float z = currentSample.ZLinearAcc;
            double magnitude = Math.sqrt((x * x) + (y * y) + (z * z));

            this.dequeMagnitudes.addLast(magnitude);
            if (this.dequeMagnitudes.size() > numberOfMagnitudeSamples) {
                this.dequeMagnitudes.removeFirst();
            }

            // Execute Cadence Analysis when the window is full
            if (this.dequeMagnitudes.size() == numberOfMagnitudeSamples) {
                Double[] magnitudeArray = this.dequeMagnitudes.toArray(new Double[0]);

                // --- Rest Detection (Prevents Phantom Cadence) ---
                double minMag = magnitudeArray[0];
                double maxMag = magnitudeArray[0];
                for (Double val : magnitudeArray) {
                    if (val < minMag) minMag = val;
                    if (val > maxMag) maxMag = val;
                }

                this.normalizeArray(magnitudeArray);

                // If there's barely any physical force difference, the user is standing still
                if ((maxMag - minMag) < 2.0) {
                    this.lastKnownCadence = 0;
                } else {
                    this.lastKnownCadence = this.getCadence(magnitudeArray, numberOfMagnitudeSamples, 50);
                }
            }

            // Complementary Filter
            double accelAngle = Math.atan2(y, Math.sqrt(z * z + x * x)) * (180.0 / Math.PI);
            double alpha = 0.98;

            if (lastSensorTime == 0) lastSensorTime = currentSample.timeStamp;
            double dt = (currentSample.timeStamp - lastSensorTime) / 1000.0;
            lastSensorTime = currentSample.timeStamp;

            this.currentAnglePitch = (this.currentAnglePitch + currentSample.gyroX * dt * (180.0 / Math.PI)) * alpha + (1 - alpha) * (accelAngle);

            newMagnitudes.add(magnitude);

            // Basic Step Detection
            if (magnitude > STEP_MOVMENT_TRESHHOLD && (currentSample.timeStamp - lastStepTime) > STEP_TIME_TRESHHOLD) {
                numberOfStepsDetected++;
                lastStepTime = currentSample.timeStamp;
            }
        }

        if (numberOfStepsDetected > 0) {
            stepCount += numberOfStepsDetected;
            if (stepCount % 10 == 0) {
                speak(stepCount + " steps completed");
            }
        }

        /*
            ANTI-GHOSTING SYSTEM:
            If more than 2 seconds (2000ms) have passed since the last confirmed step,
            the user has likely stopped moving. We force the cadence to 0 and clear
            the magnitude history to prevent the DFT algorithm from analyzing old echoes.
         */
        if (System.currentTimeMillis() - lastStepTime > 2000) {
            this.lastKnownCadence = 0;
            this.dequeMagnitudes.clear();
        }

        // Broadcast to UI
        Intent updateIntent = new Intent(ACTION_WORKOUT_UPDATE);
        updateIntent.setPackage(getPackageName());
        updateIntent.putExtra("steps", stepCount);
        updateIntent.putExtra("cadence", lastKnownCadence);
        updateIntent.putExtra("pitch", Math.round(currentAnglePitch));

        double[] magsPrimitive = new double[newMagnitudes.size()];
        for (int i = 0; i < newMagnitudes.size(); i++) magsPrimitive[i] = newMagnitudes.get(i);
        updateIntent.putExtra("magnitudes", magsPrimitive);

        sendBroadcast(updateIntent);
    }

    /*
        Clean, single-execution Firebase save method.
   */
    private void stopTrackingAndSave() {

        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (backgroundProcessor != null && !backgroundProcessor.isShutdown()) backgroundProcessor.shutdown();

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            Log.e("TrackingService", "CRITICAL ERROR: No user logged in.");
            broadcastSaveComplete(false);
            stopSelf();
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> workoutData = new HashMap<>();
        workoutData.put("timestamp", System.currentTimeMillis());
        workoutData.put("total_steps", this.stepCount);
        workoutData.put("final_cadence_spm", this.lastKnownCadence);
        workoutData.put("final_pitch_angle", Math.round(this.currentAnglePitch));
        String intensity = WorkoutAnalyzer.getIntensityLevel(lastKnownCadence);
        int finalScore = WorkoutAnalyzer.calculateWorkoutScore(this.stepCount, this.lastKnownCadence, this.currentAnglePitch);
        workoutData.put("workout_score", finalScore);
        workoutData.put("intensity_level", intensity);
        Log.d("TrackingService", "Starting Firebase save process...");

        // A single, clean network call using addOnCompleteListener
        db.collection("users").document(uid).collection("workouts")
                .add(workoutData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        speak("Workout saved successfully");
                        Log.d("TrackingService", "Saved to cloud successfully!");
                        broadcastSaveComplete(true);
                        stopSelf();
                    } else {
                        Exception e = task.getException();
                        String errorMsg = (e != null) ? e.getMessage() : "Unknown Error";
                        Log.e("TrackingService", "Firebase Task Failed! Reason: " + errorMsg);
                        broadcastSaveComplete(false);
                        stopSelf();
                    }
                });
    }

    private void broadcastSaveComplete(boolean isSuccess) {
        Intent completeIntent = new Intent(ACTION_SAVE_COMPLETE);
        completeIntent.putExtra("success", isSuccess);
        completeIntent.setPackage(getPackageName());
        sendBroadcast(completeIntent);
    }

    private void normalizeArray(Double[] arr) {
        double avg = 0.0;
        for (Double val : arr) avg += val;
        avg /= arr.length;
        for (int i = 0; i < arr.length; i++) arr[i] -= avg;
    }

    /**
     * DSP Implementation: Extracts the dominant cadence frequency from time-domain acceleration arrays.
     * Transforms raw magnitude blocks into frequency-domain spectral coefficients using an
     * optimized O(N*K) Discrete Fourier Transform (DFT) tuned explicitly for human locomotion frequencies (0.5Hz - 4.0Hz).
     *
     * @param arr                       The array of raw magnitude scalars.
     * @param numberOfMagnitudeSamples  The exact window frame size (N).
     * @param numberOfSamplesPerSeconds The hardware sampling rate (Hz).
     * @return Calculated cadence value in Steps Per Minute (SPM).
     */
    private int getCadence(Double[] arr, int numberOfMagnitudeSamples, int numberOfSamplesPerSeconds) {
        double maxCoefficient = 0.0, currentMagnitude;
        double maxK = 0.0;

        // We use a predefined factor to eliminate redundant division operations inside the heavy O(N*K) loop
        double preCalculatedFactor = (2.0 * Math.PI) / numberOfMagnitudeSamples;

        // Try to get the most accurate k value (amount of steps for about 5 seconds)
        //algorithm explained in depth on the project book
        for (double k = 5.0; k <= 25.0; k += 0.5) {
            double an = 0.0;
            double bn = 0.0;
            double kFactor = k * preCalculatedFactor;

            for (int n = 0; n < numberOfMagnitudeSamples; n++) {
                double angle = kFactor * n;
                an += arr[n] * Math.cos(angle);
                bn += arr[n] * Math.sin(angle);
            }
            currentMagnitude = Math.sqrt((an * an) + (bn * bn));
            if (currentMagnitude > maxCoefficient) {
                maxCoefficient = currentMagnitude;
                maxK = k;
            }
        }
        return (int) Math.round((60.0 * maxK * numberOfSamplesPerSeconds) / numberOfMagnitudeSamples);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        /*
            RESOURCE CLEANUP (Critical for avoiding Memory & Battery Leaks):
            If the Android OS brutally kills this Service (e.g., user swipes app away),
            we MUST release the hardware sensors and shutdown the background thread,
            otherwise the phone's CPU will continue executing ghost tasks.
         */
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (backgroundProcessor != null && !backgroundProcessor.isShutdown()) {
            backgroundProcessor.shutdownNow();
        }


        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
    /**
     * Callback interface triggered by the hardware sensor manager when a new kinetic event occurs.
     * Processes high-frequency raw data vectors, applies coordinate transformations,
     * and streams them through the internal mathematical filters.
     * pack the data on a custom class and sends it to processing.
     * @param event The SensorEvent object containing raw three-axis vectors (X, Y, Z).
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            this.gyroscopeDataForSynchronize[0] = event.values[0];
            this.gyroscopeDataForSynchronize[1] = event.values[1];
            this.gyroscopeDataForSynchronize[2] = event.values[2];
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            sensorQueue.offer(new SensorSample(
                    event.values[0], event.values[1], event.values[2],
                    this.gyroscopeDataForSynchronize[0], this.gyroscopeDataForSynchronize[1], this.gyroscopeDataForSynchronize[2],
                    System.currentTimeMillis()
            ));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            speak("Tracking Started");
        }
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Workout Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}