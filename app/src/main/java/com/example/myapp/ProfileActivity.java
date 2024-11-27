package com.example.myapp;
import com.bumptech.glide.Glide;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ProfileActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST = 1;

    private EditText usernameEdit, emailEdit, passwordEdit;
    private TextView totalTasksCount, completedTasksCount;
    private ImageView profilePicture;
    private Button updateButton, uploadImageButton;
    private Uri selectedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initializeViews();
        fetchUserProfile();
        setupUpdateButton();
        setupUploadImageButton();
    }

    private void initializeViews() {
        usernameEdit = findViewById(R.id.profile_username);
        emailEdit = findViewById(R.id.profile_email);
        passwordEdit = findViewById(R.id.profile_new_password);
        totalTasksCount = findViewById(R.id.total_tasks_count);
        completedTasksCount = findViewById(R.id.completed_tasks_count);
        profilePicture = findViewById(R.id .profile_picture);
        updateButton = findViewById(R.id.update_profile_button);
        uploadImageButton = findViewById(R.id.update_profile_button);
    }

    private void fetchUserProfile() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://10.0.2.2:8000/api/profile/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + getAccessToken());

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject profileData = new JSONObject(response.toString());
                    runOnUiThread(() -> {
                        try {
                            usernameEdit.setText(profileData.getString("username"));
                            emailEdit.setText(profileData.getString("email"));
                            totalTasksCount.setText(String.valueOf(profileData.getInt("total_tasks")));
                            completedTasksCount.setText(String.valueOf(profileData.getInt("completed_tasks")));

                            // Load profile picture (optional, if server provides URL)
                            String profilePictureUrl = profileData.getString("profile_picture_url");
                            if (!profilePictureUrl.isEmpty()) {
                                // Use a library like Glide or Picasso to load the image
                                Glide.with(this).load(profilePictureUrl).into(profilePicture);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error fetching profile", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void setupUpdateButton() {
        updateButton.setOnClickListener(v -> updateProfile());
    }

    private void setupUploadImageButton() {
        uploadImageButton.setOnClickListener(v -> openImagePicker());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            profilePicture.setImageURI(selectedImageUri);
            uploadProfilePicture();
        }
    }

    private void uploadProfilePicture() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://10.0.2.2:8000/api/profile/upload-picture/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + getAccessToken());
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=boundary");
                connection.setDoOutput(true);

                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                String boundary = "--boundary";

                // Add file part
                outputStream.writeBytes(boundary + "\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"profile_picture\"; filename=\"profile.jpg\"\r\n");
                outputStream.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                byte[] imageData = Util.getBytesFromUri(getApplicationContext(), selectedImageUri);
                outputStream.write(imageData);  // This will write the byte array

                outputStream.writeBytes("\r\n");
                outputStream.writeBytes(boundary + "--\r\n");

                outputStream.flush();
                outputStream.close();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Profile picture uploaded successfully", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error uploading profile picture", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    // ... existing code ...

    private void updateProfile() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                // Your existing update profile logic here

                // If update is successful
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);  // Add this line
                        finish();  // Add this line
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error updating profile", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    // Add this method to handle back button press
    @Override
    public void onBackPressed() {
        setResult(RESULT_OK);
        finish();
        super.onBackPressed();
    }

// ... existing code ...

    private String getAccessToken() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        return sharedPreferences.getString("access_token", null);
    }

}
