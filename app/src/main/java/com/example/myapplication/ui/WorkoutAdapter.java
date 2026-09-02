package com.example.myapplication.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.model.Workout;
import com.example.myapplication.databinding.ItemWorkoutBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The Data-to-UI Bridge (Adapter Pattern) for the Workout History screen.
 * This class maps the raw 'Workout' data objects retrieved from the Cloud (Firestore)
 * into dynamic, memory-efficient visual view components (ViewHolders) within a RecyclerView.
 * * Performance Design: Implements the ViewHolder pattern to avoid costly 'findViewById'
 * operations during rapid scrolling, ensuring smooth 60fps rendering even with hundreds
 * of historical workout records.
 */
public class WorkoutAdapter extends RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder> {

    private List<Workout> workoutList;

    public WorkoutAdapter(List<Workout> workoutList) {
        this.workoutList = workoutList;
    }

    /**
     * ViewHolder Inner Class: Acts as a high-performance cache.
     * It holds direct references to the UI elements within a single workout card (XML),
     * preventing the system from re-parsing the layout files every time a new row is drawn.
     */
    @NonNull
    @Override
    public WorkoutViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the item layout using ViewBinding
        ItemWorkoutBinding binding = ItemWorkoutBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new WorkoutViewHolder(binding);
    }

    /*
        Called to fill an existing card with specific workout data as it scrolls into view.
     */
    @Override
    public void onBindViewHolder(@NonNull WorkoutViewHolder holder, int position) {
        Workout currentWorkout = workoutList.get(position);
        holder.bind(currentWorkout);
    }

    @Override
    public int getItemCount() {
        return workoutList == null ? 0 : workoutList.size();
    }

    /*
        ViewHolder: Caches the references to the UI components to avoid calling findViewById repeatedly.
     */
    static class WorkoutViewHolder extends RecyclerView.ViewHolder {

        private ItemWorkoutBinding binding;

        public WorkoutViewHolder(ItemWorkoutBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        // Binds the Java object data to the XML TextViews
        public void bind(Workout workout) {
            binding.tvDate.setText(formatTimestamp(workout.getTimestamp()));
            binding.tvSteps.setText("Total Steps: " + workout.getTotalSteps());
            binding.tvCadence.setText("Average Cadence: " + workout.getCadence() + " SPM");
            binding.tvPitch.setText("Average Posture Angle: " + workout.getPitch() + "°");
        }

        /*
            Helper Method: Converts raw milliseconds to a human-readable date and time.
         */
        private String formatTimestamp(long timestamp) {
            Date date = new Date(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
            return sdf.format(date);
        }
    }
}