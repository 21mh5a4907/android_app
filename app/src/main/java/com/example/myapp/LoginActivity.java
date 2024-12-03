package com.example.myapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameInput, passwordInput;
    private String username, password;
    private final String LOGIN_URL = "http://10.0.2.2:8000/api/login/"; // Replace with your backend URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);

        // Set the login button listener
        findViewById(R.id.login_btn).setOnClickListener(v -> login());

        // Set the register button listener to navigate to the registration page
        findViewById(R.id.register_redirect_btn).setOnClickListener(v -> navigateToRegister());
    }

    // Login method
    public void login() {
        username = usernameInput.getText().toString().trim();
        password = passwordInput.getText().toString().trim();

        // Validate inputs
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Perform login on a background thread
        new Thread(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                // Use superuser login endpoint for admin users
                String loginEndpoint = username.equals("admin") ? 
                    "http://10.0.2.2:8000/api/superuser-login/" : 
                    "http://10.0.2.2:8000/api/login/";

                URL url = new URL(loginEndpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Prepare the JSON data
                JSONObject loginData = new JSONObject();
                loginData.put("username", username);
                loginData.put("password", password);

                // Send the request
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(loginData.toString().getBytes());
                    os.flush();
                }

                // Get the response
                int responseCode = connection.getResponseCode();
                reader = new BufferedReader(new InputStreamReader(
                        responseCode == 200 ? connection.getInputStream() : connection.getErrorStream()
                ));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                final JSONObject jsonResponse = new JSONObject(response.toString());

                runOnUiThread(() -> {
                    try {
                        if (responseCode == 200) {
                            if (username.equals("admin")) {
                                // Handle superuser login
                                Intent intent = new Intent(LoginActivity.this, SuperuserDashboardActivity.class);
                                intent.putExtra("auth_token", jsonResponse.getString("access"));
                                startActivity(intent);
                                finish();
                            } else {
                                // Handle regular user login
                                handleRegularUserLogin(jsonResponse);
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, 
                                jsonResponse.optString("message", "Login failed"), 
                                Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(LoginActivity.this, 
                            "Error processing response", 
                            Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(LoginActivity.this, 
                    "Error: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show());
            } finally {
                // Close resources
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void handleRegularUserLogin(JSONObject response) throws JSONException {
        String accessToken = response.getString("access");
        String refreshToken = response.getString("refresh");
        storeTokens(accessToken, refreshToken);
        navigateToDashBoard();
    }

    // Method to store the tokens securely
    private void storeTokens(String accessToken, String refreshToken) {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("access_token", accessToken);
        editor.putString("refresh_token", refreshToken);
        editor.apply();
    }

    // Method to navigate to the home page after successful login
    private void navigateToDashBoard() {
        Intent intent = new Intent(LoginActivity.this, DashBoardActivity.class);
        startActivity(intent);
    }

    // Method to navigate to the registration page
    private void navigateToRegister() {
        Intent intent = new Intent(LoginActivity.this, RegistrationActivity.class);
        startActivity(intent);
    }

    // In your login activity where you handle the successful login response
    private void handleLoginSuccess(JSONObject response) {
        try {
            String token = response.getString("access"); // Get the access token
            
            // Create intent for SuperuserDashboardActivity
            Intent intent = new Intent(LoginActivity.this, SuperuserDashboardActivity.class);
            
            // Add the token as an extra to the intent
            intent.putExtra("auth_token", token);
            
            startActivity(intent);
            finish(); // Close the login activity
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing login response", Toast.LENGTH_SHORT).show();
        }
    }
}
