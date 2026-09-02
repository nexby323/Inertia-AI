package com.example.myapplication.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.myapplication.model.Workout;
import com.example.myapplication.databinding.ActivityHistoryBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
    *HistoryActivity: The "Data Retrieval & Presentation" layer.
    *This Activity queries Cloud Firestore to retrieve all past workouts
    *for the currently authenticated user and prepares them for the RecyclerView.
 */
public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding binding; // ViewBinding for null-safety
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // The internal memory list that will hold the downloaded cloud data
    private List<Workout> workoutList;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase singletons
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        workoutList = new ArrayList<>();

        // Setup the RecyclerView structure (Vertical scrolling list)
        binding.rvWorkouts.setLayoutManager(new LinearLayoutManager(this));

        // Trigger the asynchronous network call
        fetchWorkoutHistory();
    }

    /*
        Executes an asynchronous query to Firestore.
        Fetches documents from the user's specific sub-collection,
        ordered chronologically (newest first).
     */
    private void fetchWorkoutHistory() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        Log.d("HistoryActivity", "Fetching data for UID: " + uid);

        /*
            Query Structure:
            Go to users -> specific UID -> workouts collection.
            Sort the results by the "timestamp" field in DESCENDING order
            so the most recent workout appears at the top of the screen.
         */
        db.collection("users").document(uid).collection("workouts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    // Clear the old list to prevent duplicating data if we refresh
                    workoutList.clear();

                    // Loop through every document the cloud returned
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {

                        Long timestamp = document.getLong("timestamp");
                        Long totalSteps = document.getLong("total_steps");
                        Long finalCadence = document.getLong("final_cadence_spm");
                        Long finalPitch = document.getLong("final_pitch_angle");

                        // Safety check: Prevent app crash if a field is missing in the database
                        if (timestamp != null && totalSteps != null && finalCadence != null && finalPitch != null) {

                            Workout workout = new Workout(
                                    timestamp,
                                    totalSteps.intValue(),
                                    finalCadence.intValue(),
                                    finalPitch.intValue()
                            );
                            workoutList.add(workout);
                        }
                    }

                    Log.d("HistoryActivity", "Successfully fetched " + workoutList.size() + " workouts.");

                    // --- UI UPDATE LOGIC ---
                    if (workoutList.isEmpty()) {
                        // Empty State: Show the message, hide the list
                        binding.rvWorkouts.setVisibility(android.view.View.GONE);
                        binding.tvEmptyState.setVisibility(android.view.View.VISIBLE);
                    } else {
                        // Data exists: Show the list, hide the message
                        binding.rvWorkouts.setVisibility(android.view.View.VISIBLE);
                        binding.tvEmptyState.setVisibility(android.view.View.GONE);

                        // Initialize the adapter with the downloaded data and attach to RecyclerView
                        WorkoutAdapter adapter = new WorkoutAdapter(workoutList);
                        binding.rvWorkouts.setAdapter(adapter);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("HistoryActivity", "Failed to fetch history: " + e.getMessage());
                    Toast.makeText(this, "Failed to load workout history.", Toast.LENGTH_SHORT).show();
                });
    }
}