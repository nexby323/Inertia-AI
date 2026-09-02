package com.example.myapplication.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.databinding.ActivityLoginBinding;
import com.example.myapplication.utils.ThemeManager;
import com.example.myapplication.ui.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

//inherit from AppCompatActivity to get modern screen abilities
public class LoginActivity extends AppCompatActivity {
    //connected to the XML of this file, instead of using the findViewById method.
    /*

        binding on depth:
        according to https://medium.com/@sandeepkella23/what-is-view-binding-in-android-and-how-it-works-internally-74c9ce0e5422
        and according to https://medium.com/@omersenturk.dev/3-ways-to-access-ui-components-in-android-pros-and-cons-85364391b483
        both binding and findViewById are ways to reference to a UI component
        findViewById- do not support Null safety, means when a component does not exist and we
        reference to it, this will cause crash on runtime.
        also not support Type safety, if there is a mismatch between the Type of the component
        will cause ClassCastException (another runtime).
        every time when findViewById it scans all the full list of components.

        each Layout file get a new class Named [LayoutFileName]Binding because of the  gradle settings
        hence, all the RuntimeException have no chance to occur, because it is an actual class and not
        and if something does not exist or the Type is not correct, it will be on compile time.
        also the search for the view will not occur during runtime but on compile time (on java way).

     */
    ActivityLoginBinding binding;


    private FirebaseAuth mAuth;


    private ThemeManager themeManager;

    @Override
    //called when the screen is loaded
    protected void onCreate(Bundle savedInstanceState) {
        //need for the operating system of android to initialize the screen
        super.onCreate(savedInstanceState);


        themeManager = new ThemeManager(this);
        themeManager.applyTheme(themeManager.isNightMode());

        //make the XML file from const and static on the UI to dynamic with the binding
        this.binding = ActivityLoginBinding.inflate(getLayoutInflater());

        //when doing UI operations the binding is the way to do that
        setContentView(binding.getRoot());

        // check for dark mode or light mode
        if (themeManager.isNightMode()) {
            //we are on dark mode so put the sun
            binding.btnThemeToggle.setImageResource(R.drawable.ic_sun);
        } else {
            // we are on light mode so put the moon
            binding.btnThemeToggle.setImageResource(R.drawable.ic_moon);
        }

        // explained on the SignupActivity
        this.mAuth = FirebaseAuth.getInstance();

        //when the login button is pressed do:
        this.binding.loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = binding.loginEmail.getText().toString();
                String password  = binding.loginPassword.getText().toString();

                //if the email or the password field is empty make a Toast.makeText (bubble notification) to the user
                if(email.isEmpty() || password.isEmpty())
                {
                    Toast.makeText(LoginActivity.this, "All fields are mandatory", Toast.LENGTH_SHORT).show();
                }
                else
                {

                    mAuth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(LoginActivity.this, task -> {
                                if (task.isSuccessful()) {
                                    // success Login
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    Toast.makeText(LoginActivity.this, "Login Successfully!", Toast.LENGTH_SHORT).show();

                                    //make an Intent to pass the user to the MainActivity
                                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                    //pass the user to the MainActivity
                                    startActivity(intent);

                                    //close the screen and not give the user the option to go back
                                    finish();
                                } else {
                                    // failure
                                    // task.getException().getMessage()
                                    Toast.makeText(LoginActivity.this, "Invalid password or email", Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            }
        });

        binding.btnThemeToggle.setOnClickListener(v -> {

            boolean newMode = !themeManager.isNightMode();


            themeManager.setNightMode(newMode);


            Intent intent = new Intent(LoginActivity.this, LoginActivity.class);
            startActivity(intent);


            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);


            finish();
        });

        //when the Register an account button is pressed pass the user to the SignUp activity
        binding.signupRedirectText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), SignupActivity.class);

                startActivity(intent);
                // called UI shit
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            }
        });


        binding.tvLoginTitle.setAlpha(0f);
        binding.tvLoginTitle.setTranslationY(50);

        binding.tvLoginSubtitle.setAlpha(0f);
        binding.tvLoginSubtitle.setTranslationY(50);

        binding.tilLoginEmail.setAlpha(0f);
        binding.tilLoginEmail.setTranslationY(50);

        binding.tilLoginPassword.setAlpha(0f);
        binding.tilLoginPassword.setTranslationY(50);

        binding.loginButton.setAlpha(0f);
        binding.loginButton.setTranslationY(50);

        binding.signupRedirectText.setAlpha(0f);
        binding.signupRedirectText.setTranslationY(50);


        int animationDuration = 500;

        binding.tvLoginTitle.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(100).start();
        binding.tvLoginSubtitle.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(200).start();
        binding.tilLoginEmail.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(300).start();
        binding.tilLoginPassword.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(400).start();
        binding.loginButton.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(500).start();
        binding.signupRedirectText.animate().alpha(1f).translationY(0).setDuration(animationDuration).setStartDelay(600).start();
    }
}
