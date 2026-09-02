package com.example.myapplication.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.databinding.ActivitySignupBinding;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {


    //FirebaseAuth is a class that follow the Singleton pattern design, used to create/check for user on the database with methods.
    //can see on https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseAuth#summary
    private FirebaseAuth mAuth;
    //also follow the Singleton pattern design, the access point to the cloud database FireBase.
    private FirebaseFirestore db;
    //binding see LoginActivity to advantages over the normal way and deep explanation.
    ActivitySignupBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        //the singleton behavior (need to get the only one instance)
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        //normal binding inflation of the xml file to actual java class
        this.binding = ActivitySignupBinding.inflate(getLayoutInflater());
        //get all the information typed on UI component
        setContentView(binding.getRoot());
        binding.btnRegister.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString();
            String password = binding.etPassword.getText().toString();
            String fullName = binding.etFullName.getText().toString();

            // basic validation of the password (more then 6 characters are mandatory)
            if (email.isEmpty() || password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 chars", Toast.LENGTH_SHORT).show();
                return;
            }
            //call the private method defined above the register new account.
            registerNewUser(email, password, fullName);
        });
        binding.tvLoginPrompt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Create an Intent to navigate from SignupActivity to LoginActivity
                Intent intent = new Intent(SignupActivity.this, LoginActivity.class);

                //Start the new activity
                startActivity(intent);
                // on simple words: UI shit
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);

                // This prevents from the user from returning to the Signup screen if they press the "Back" button on their phone
                //close the lifecycle of "this" Activity

                finish();
            }
        });
        binding.tvTitle.setAlpha(0f);
        binding.tvTitle.setTranslationY(50);

        binding.tvSubtitle.setAlpha(0f);
        binding.tvSubtitle.setTranslationY(50);

        binding.tilFullName.setAlpha(0f);
        binding.tilFullName.setTranslationY(50);

        binding.tilEmail.setAlpha(0f);
        binding.tilEmail.setTranslationY(50);

        binding.tilPassword.setAlpha(0f);
        binding.tilPassword.setTranslationY(50);

        binding.btnRegister.setAlpha(0f);
        binding.btnRegister.setTranslationY(50);

        binding.tvLoginPrompt.setAlpha(0f);
        binding.tvLoginPrompt.setTranslationY(50);

//some UI shit, not really important
        int animationDuration = 500;

        binding.tvTitle.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(100).start();
        binding.tvSubtitle.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(200).start();
        binding.tilFullName.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(300).start();
        binding.tilEmail.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(400).start();
        binding.tilPassword.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(500).start();
        binding.btnRegister.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(600).start();
        binding.tvLoginPrompt.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(700).start();
    }

    private void registerNewUser(String email, String password, String fullName) {
        /*
            *create from the FirebaseAuth instance's method user with email and password
            *the addOnCompleteListener method is making asynchronous behavior meaning its doing
            *this process on background process, this like a buzzer on a restaurant its when the
            *food is ready its buzz until then you wait but can do other things.
            *the createUserWithEmailAndPassword method return a Task this represent a asynchronous operation
            *and the addOnCompleteListener catch the task and then divide to cases, if the task did success then
            *it get the User Id that just have been created and call the private method that saves the details on the data
            *base and add more information about the user.
            *else it is notify the user that the operation did not success (and show the reason).
        */
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // get the  UserId that just created
                        String uid = mAuth.getCurrentUser().getUid();

                        // call the method that saves the information and add more
                        saveUserDetailsToDatabase(uid, email, fullName);

                    }
                    //failed to create the user with password from some reason, notify the user about that
                    else
                    {
                        Toast.makeText(this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserDetailsToDatabase(String uid, String email, String fullName) {
        // make a collection (HashMap, similar to json) to store the user information
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put("email", email);
        userDetails.put("full_name", fullName);
        //save the data as Long (time is stored as Long on java)
        userDetails.put("joined_date", System.currentTimeMillis());
        // TODO: add more information about the user (Height, Weight , etc ).

        /*
            FireBase is NoSQL database this means unlike the normal SQL/SQLLite it does not have
            tables and rows it build on collection and documents
            each document is key-value pair (like json) the pairs can be from any type you need
            each document has a unique ID in his collection (in this case set from the one that FireBase gave as default)
            a collection save a bunch of documents, you must put documents inside collections
         */
        /*
            put on the users collection the document with user id, uid
            the set method unlike the add method override the existing data with the same uid
            https://firebase.google.com/docs/firestore/manage-data/add-data#java
            return a task like the above method when the task is finished
        */
        db.collection("users").document(uid)
                .set(userDetails)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Account Created!", Toast.LENGTH_SHORT).show();
                    //make an Intent to pass the user to the LoginActivity
                    Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                    //pass the user to the LoginActivity to sign in with this user that just created
                    startActivity(intent);
                    //not give access to the user to go back to this Activity
                    finish();
                })
                /*
                    CRITICAL DEBUGGING ADDITION:
                    If the database write fails (e.g., due to Security Rules or missing network),
                    this listener will catch the exact reason and output it to the user and Logcat.
                 */
                .addOnFailureListener(e -> {
                    android.util.Log.e("SignupActivity", "CRITICAL DATABASE ERROR: " + e.getMessage());
                    Toast.makeText(this, "Database Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}