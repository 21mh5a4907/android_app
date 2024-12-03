package com.example.myapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {
    private static final String TAG = "ProfileActivity";
    private static final String BASE_URL = "http://10.0.2.2:8000/api";

    private ImageView imgProfilePicture;
    private Button btnUploadPicture, btnRemovePicture, btnSaveProfile;
    private EditText editUsername, editOldPassword, editNewPassword;
    private ProgressBar progressBar;
    private String authToken;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        uploadProfilePicture(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initializeViews();
        loadAuthToken();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile();
    }

    private void initializeViews() {
        imgProfilePicture = findViewById(R.id.imgProfilePicture);
        btnUploadPicture = findViewById(R.id.btnUploadPicture);
        btnRemovePicture = findViewById(R.id.btnRemovePicture);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        editUsername = findViewById(R.id.editUsername);
        editOldPassword = findViewById(R.id.editOldPassword);
        editNewPassword = findViewById(R.id.editNewPassword);
        progressBar = findViewById(R.id.progressBar);
    }

    private void loadAuthToken() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        authToken = prefs.getString("access_token", "");
        if (authToken == null || authToken.isEmpty()) {
            navigateToLogin();
        }
    }

    private void setupClickListeners() {
        btnUploadPicture.setOnClickListener(v -> openImagePicker());
        btnRemovePicture.setOnClickListener(v -> removeProfilePicture());
        btnSaveProfile.setOnClickListener(v -> validateAndUpdateProfile());
    }

    private void validateAndUpdateProfile() {
        String username = editUsername.getText().toString().trim();
        String oldPassword = editOldPassword.getText().toString().trim();
        String newPassword = editNewPassword.getText().toString().trim();

        if (username.isEmpty()) {
            editUsername.setError("Username is required");
            return;
        }

        if ((!oldPassword.isEmpty() && newPassword.isEmpty()) ||
                (oldPassword.isEmpty() && !newPassword.isEmpty())) {
            Toast.makeText(this, "Both old and new passwords are required for password change",
                    Toast.LENGTH_LONG).show();
            return;
        }

        updateUserProfile(username, oldPassword, newPassword);
    }
    private void updateUserProfile(String username, String oldPassword, String newPassword) {
        showProgress();
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("username", username);

            if (!oldPassword.isEmpty() && !newPassword.isEmpty()) {
                jsonBody.put("old_password", oldPassword);
                jsonBody.put("new_password", newPassword);
            }

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.PUT,
                    BASE_URL + "/user/update/",
                    jsonBody,
                    response -> {
                        hideProgress();
                        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                        editOldPassword.setText("");
                        editNewPassword.setText("");
                        loadUserProfile();
                    },
                    error -> {
                        hideProgress();
                        handleNetworkError(error);
                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    return getAuthHeaders();
                }
            };

            request.setRetryPolicy(new DefaultRetryPolicy(
                    30000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            Volley.newRequestQueue(this).add(request);
        } catch (JSONException e) {
            hideProgress();
            Log.e(TAG, "JSON Error: " + e.getMessage());
            Toast.makeText(this, "Error updating profile", Toast.LENGTH_SHORT).show();
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Picture"));
    }

    private void loadUserProfile() {
        showProgress();
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/profile/",
                null,
                response -> {
                    hideProgress();
                    try {
                        Log.d(TAG, "Profile Response: " + response.toString());
                        editUsername.setText(response.getString("username"));

                        String profilePicUrl = response.optString("profile_picture", "");
                        if (!profilePicUrl.isEmpty()) {
                            // Handle the relative URL format (/media/profile_pictures/profile.jpg)
                            if (profilePicUrl.startsWith("/")) {
                                profilePicUrl = "http://10.0.2.2:8000" + profilePicUrl;
                            }
                            // If it's not a complete URL already, add the base URL
                            else if (!profilePicUrl.startsWith("http")) {
                                profilePicUrl = BASE_URL + profilePicUrl;
                            }

                            Log.d(TAG, "Loading image from URL: " + profilePicUrl);

                            Glide.with(this)
                                    .load(profilePicUrl)
                                    .placeholder(R.drawable.ic_profile)
                                    .error(R.drawable.ic_profile)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)  // Disable caching
                                    .skipMemoryCache(true)                      // Skip memory caching
                                    .into(imgProfilePicture);
                        } else {
                            imgProfilePicture.setImageResource(R.drawable.ic_profile);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage());
                        Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    hideProgress();
                    handleNetworkError(error);
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return getAuthHeaders();
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(this).add(request);
    }

    private void uploadProfilePicture(Uri imageUri) {
        showProgress();
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] imageBytes = baos.toByteArray();

            VolleyMultipartRequest multipartRequest = new VolleyMultipartRequest(
                    Request.Method.PUT,
                    BASE_URL + "/profile/update/",
                    response -> {
                        hideProgress();
                        try {
                            String responseString = new String(response.data);
                            JSONObject jsonResponse = new JSONObject(responseString);
                            Log.d(TAG, "Upload Response: " + responseString);

                            loadUserProfile();
                            Toast.makeText(this, "Profile picture updated successfully",
                                    Toast.LENGTH_SHORT).show();
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing upload response: " + e.getMessage());
                            Toast.makeText(this, "Error updating profile picture",
                                    Toast.LENGTH_SHORT).show();
                        }
                    },
                    error -> {
                        hideProgress();
                        handleNetworkError(error);
                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    return getAuthHeaders();
                }

                @Override
                protected Map<String, DataPart> getByteData() {
                    Map<String, DataPart> params = new HashMap<>();
                    params.put("profile_picture", new DataPart("profile.jpg", imageBytes, "image/jpeg"));
                    return params;
                }
            };

            multipartRequest.setRetryPolicy(new DefaultRetryPolicy(
                    30000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            Volley.newRequestQueue(this).add(multipartRequest);
        } catch (IOException e) {
            hideProgress();
            Log.e(TAG, "Error processing image: " + e.getMessage());
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeProfilePicture() {
        showProgress();
        StringRequest request = new StringRequest(
                Request.Method.DELETE,
                BASE_URL + "/profile/update/",
                response -> {
                    hideProgress();
                    Glide.get(this).clearMemory();
                    new Thread(() -> Glide.get(ProfileActivity.this).clearDiskCache()).start();

                    imgProfilePicture.setImageResource(R.drawable.ic_profile);
                    Toast.makeText(this, "Profile picture removed successfully",
                            Toast.LENGTH_SHORT).show();
                    loadUserProfile();
                },
                error -> {
                    hideProgress();
                    handleNetworkError(error);
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return getAuthHeaders();
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(this).add(request);
    }

    private Map<String, String> getAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + authToken);
        return headers;
    }

    private void handleNetworkError(VolleyError error) {
        if (error.networkResponse != null) {
            String errorMessage = new String(error.networkResponse.data);
            Log.e(TAG, "Network error: " + error.networkResponse.statusCode + " - " + errorMessage);
            Toast.makeText(this, "Network error: " + error.networkResponse.statusCode,
                    Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Unknown network error", error);
            Toast.makeText(this, "Unknown network error", Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgress() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}