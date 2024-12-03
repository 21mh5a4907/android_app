package com.example.myapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class AdminLoginActivity extends AppCompatActivity {

    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        usernameEditText = findViewById(R.id.admin_username);
        passwordEditText = findViewById(R.id.admin_password);
        loginButton = findViewById(R.id.admin_login_submit);

        requestQueue = Volley.newRequestQueue(this);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = usernameEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(AdminLoginActivity.this, "Please fill in both fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                loginSuperuser(username, password);
            }
        });
    }

    private void loginSuperuser(String username, String password) {
        String url = "http://10.0.2.2:8000/api/superuser-login/";

        Map<String, String> params = new HashMap<>();
        params.put("username", username);
        params.put("password", password);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, new JSONObject(params),
                response -> {
                    try {
                        Log.d("AdminLoginActivity", "Full response: " + response.toString());
                        String message = response.getString("message");
                        String accessToken = response.getString("access");

                        if (message.equals("Superuser logged in successfully!")) {
                            String loggedInUsername = response.getString("username");
                            Log.d("AdminLoginActivity", "Token received: " + accessToken);
                            navigateToSuperuserDashboard(accessToken, loggedInUsername);
                        } else {
                            Toast.makeText(AdminLoginActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e("AdminLoginActivity", "JSON parsing error", e);
                        Toast.makeText(AdminLoginActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e("AdminLoginActivity", "Volley error", error);
                    handleNetworkError(error);
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        // Configure request with retry policy
        configureRequest(request);
        requestQueue.add(request);
    }

    private void navigateToSuperuserDashboard(String token, String username) {
        Intent intent = new Intent(this, SuperuserDashboardActivity.class);
        intent.putExtra("auth_token", token);
        intent.putExtra("username", username);
        startActivity(intent);
        finish();
    }

    private void handleNetworkError(VolleyError error) {
        if (error.networkResponse != null) {
            Log.e("AdminLoginActivity", "Error code: " + error.networkResponse.statusCode);
            String errorMessage = new String(error.networkResponse.data);
            Log.e("AdminLoginActivity", "Error response: " + errorMessage);
        }

        String message = "Network error. Please check your connection.";
        if (error instanceof TimeoutError) {
            message = "Request timed out. Please try again.";
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void configureRequest(JsonObjectRequest request) {
        request.setRetryPolicy(new DefaultRetryPolicy(
                15000,  // 15 seconds timeout
                2,      // Max retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));
    }
}
