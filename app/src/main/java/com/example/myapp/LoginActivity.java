package com.example.myapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
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
        setContentView(R.layout.activity_login); // Make sure you have this layout for login

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
                // Prepare the connection to the server
                URL url = new URL(LOGIN_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json"); // Content type is JSON
                connection.setDoOutput(true);

                // Prepare the JSON data for the POST request (changed "email" to "username")
                JSONObject loginData = new JSONObject();
                loginData.put("username", username);  // Use "username" here
                loginData.put("password", password);

                // Send the JSON data
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(loginData.toString().getBytes());
                    os.flush();
                }

                // Get the response from the server
                int responseCode = connection.getResponseCode();
                reader = new BufferedReader(new InputStreamReader(
                        responseCode == 200 ? connection.getInputStream() : connection.getErrorStream()
                ));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                // Log the raw response from the server
                Log.d("LoginResponse", "Response: " + response.toString());

                // Check for successful login (200 OK)
                runOnUiThread(() -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.toString());

                        // Check if the response contains access and refresh tokens
                        if (jsonResponse.has("access") && jsonResponse.has("refresh")) {
                            String accessToken = jsonResponse.getString("access");
                            String refreshToken = jsonResponse.getString("refresh");

                            // Store the tokens securely (e.g., in SharedPreferences)
                            storeTokens(accessToken, refreshToken);

                            // Show login successful message
                            Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();

                            // Navigate to the DashBoard activity after successful login
                            navigateToDashBoard();
                        } else {
                            // Handle the case where the tokens are missing
                            Toast.makeText(this, "Unexpected server response", Toast.LENGTH_SHORT).show();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error parsing response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "An error occurred: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                // Close resources safely
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    // Method to store the tokens securely (SharedPreferences in this case)
    private void storeTokens(String accessToken, String refreshToken) {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("access_token", accessToken);
        editor.putString("refresh_token", refreshToken);
        editor.apply();
    }

    // Method to navigate to the home page after successful login
    private void navigateToDashBoard() {
        // Example navigation, you can change it based on your app flow
        Intent intent = new Intent(LoginActivity.this, DashBoardActivity.class);
        startActivity(intent);
    }

    // Method to navigate to the registration page
    private void navigateToRegister() {
        Intent intent = new Intent(LoginActivity.this, RegistrationActivity.class);
        startActivity(intent);
    }
}
