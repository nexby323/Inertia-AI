package com.example.myapplication.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.engine.TrackingService;
import com.example.myapplication.databinding.ActivitySensorTrackingBinding;

/**
 * The View layer of the sensor tracking system.
 * Designed strictly under the Separation of Concerns (SoC) principle, this class operates
 * as a passive, dumb terminal interface.
 * * Architectural Responsibilities:
 * 1. Dispatches lifecycle controls and commands to the background execution service.
 * 2. Implements the Observer Pattern via an Inter-Process Communication (IPC) BroadcastReceiver.
 * 3. Safely manipulates the UI thread to display real-time telemetry using compiled ViewBinding mappings.
 */
public class SensorTrackingActivity extends AppCompatActivity {

    private ActivitySensorTrackingBinding binding;
    /**
     * Asynchronous Inter-Process Communication (IPC) Gateway.
     * Registers an explicit broadcast antenna on the OS level, capturing calculated physical metrics
     * emitted by the physics engine and pushing them safely into the main UI render queue.
     */
    private final BroadcastReceiver workoutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // EVENT 1: A live data update arrived from the physics engine
            if (TrackingService.ACTION_WORKOUT_UPDATE.equals(action)) {

                int steps = intent.getIntExtra("steps", 0);
                int cadence = intent.getIntExtra("cadence", 0);
                int pitch = intent.getIntExtra("pitch", 0);

                // Update the User Interface (TextViews)
                binding.tvStepCount.setText("Steps: " + steps);

                if (cadence > 0) {
                    binding.tvCadence.setText("Cadence: " + cadence + " SPM");
                }

                binding.tvPosture.setText("Pitch Angle: " + pitch + "°");

                // הגרף הוסר - ביצועי ה-UI שופרו משמעותית
            }
            // EVENT 2: The Service successfully uploaded the workout to Firebase
            else if (TrackingService.ACTION_SAVE_COMPLETE.equals(action)) {
                boolean isSuccess = intent.getBooleanExtra("success", false);
                if (isSuccess) {
                    Toast.makeText(context, "Workout saved securely to the cloud!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "ERROR: Not logged in or network failed. Data lost.", Toast.LENGTH_LONG).show();
                }
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySensorTrackingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnCalibrate.setOnClickListener(v -> {
            Toast.makeText(this, "Calibrated", Toast.LENGTH_SHORT).show();
        });

        binding.btnFinishWorkout.setOnClickListener(v -> {
            binding.btnFinishWorkout.setEnabled(false);
            binding.btnFinishWorkout.setText("SAVING...");

            Intent stopIntent = new Intent(this, TrackingService.class);
            stopIntent.setAction(TrackingService.ACTION_STOP_AND_SAVE);
            startService(stopIntent);
        });

        startTrackingService();
    }

    private void startTrackingService() {
        Intent serviceIntent = new Intent(this, TrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(TrackingService.ACTION_WORKOUT_UPDATE);
        filter.addAction(TrackingService.ACTION_SAVE_COMPLETE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(workoutReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(workoutReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(workoutReceiver);
    }
}