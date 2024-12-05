package com.example.myapp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Rect;

import android.content.Intent;
import android.widget.EditText;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.app.AlertDialog;
import android.widget.ImageButton;
import android.widget.SearchView;

public class DashBoardActivity extends AppCompatActivity implements TaskAdapter.OnTaskActionListener {
    private TextView totalTasksTextView;
    private TextView completedTasksTextView;
    private TextView pendingTasksTextView;
    private TextView lowPriorityCountTextView;
    private TextView mediumPriorityCountTextView;
    private TextView highPriorityCountTextView;

    private List<Task> allTasks;
    private static final String DATE_FORMAT_DISPLAY = "MMM dd, yyyy";
    private static final String DATE_FORMAT_API = "yyyy-MM-dd";
    private SimpleDateFormat displayDateFormat;
    private SimpleDateFormat apiDateFormat;
    private RecyclerView tasksRecyclerView;
    private TaskAdapter taskAdapter;
    private FloatingActionButton fabAddTask;
    private ImageButton logoutButton;
    private SearchView searchView;

    private Spinner priorityFilter;
    private Spinner progressFilter;
    private Button btnApplyFilters;
    private ImageButton profileButton;
    private Button btnClearFilters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        logoutButton = findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(v -> showLogoutConfirmation());

        displayDateFormat = new SimpleDateFormat(DATE_FORMAT_DISPLAY, Locale.getDefault());
        apiDateFormat = new SimpleDateFormat(DATE_FORMAT_API, Locale.getDefault());

        initializeViews();
        setupFab();
        setupSearchView();
        setupFilters();
        fetchTasksFromServer();

        // Check if user is authenticated
        if (getAccessToken() == null) {
            // Redirect to login if not authenticated
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // Refresh tasks after profile update
            fetchTasksFromServer();
        }
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> performLogout())
                .setNegativeButton("No", null)
                .show();
    }

    private void performLogout() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://10.0.2.2:8000/api/logout/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + getAccessToken());

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK ||
                        responseCode == HttpURLConnection.HTTP_NO_CONTENT) {

                    // Clear stored tokens
                    SharedPreferences sharedPreferences =
                            getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.remove("access_token");
                    editor.remove("refresh_token");
                    editor.apply();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                        // Navigate to MainActivity
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Logout failed. Please try again.",
                                    Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error during logout: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void setupFab() {
        Button btnCreateTask = findViewById(R.id.btn_create_task);
        btnCreateTask.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTaskActivity.class);
            startActivity(intent);
        });
    }

    private void initializeViews() {
        totalTasksTextView = findViewById(R.id.total_task_count);
        completedTasksTextView = findViewById(R.id.completed_task_count);
        pendingTasksTextView = findViewById(R.id.pending_task_count);
        lowPriorityCountTextView = findViewById(R.id.low_priority_count);
        mediumPriorityCountTextView = findViewById(R.id.medium_priority_count);
        highPriorityCountTextView = findViewById(R.id.high_priority_count);
        tasksRecyclerView = findViewById(R.id.tasks_recycler_view);

        // Add profile button initialization
        profileButton = findViewById(R.id.profile_button);
        profileButton.setOnClickListener(v -> openProfile());

        setupRecyclerView();
    }

    private void openProfile() {
        Intent intent = new Intent(DashBoardActivity.this, ProfileActivity.class);
        startActivity(intent);
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter(this);
        tasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tasksRecyclerView.setAdapter(taskAdapter);

        // Add item decoration for spacing
        int spacing = getResources().getDimensionPixelSize(R.dimen.task_item_spacing);
        tasksRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.bottom = spacing;
            }
        });
    }

    private void setupSearchView() {
        searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterTasks(newText);
                return true;
            }
        });
    }

    private void filterTasks(String query) {
        if (allTasks == null || allTasks.isEmpty()) {
            return;
        }

        List<Task> filteredTasks = new ArrayList<>();
        for (Task task : allTasks) {
            if (task.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                    task.getDescription().toLowerCase().contains(query.toLowerCase())) {
                filteredTasks.add(task);
            }
        }

        updateUIWithTasks(filteredTasks);
    }

    private String getAccessToken() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        return sharedPreferences.getString("access_token", null);
    }

    private void fetchTasksFromServer() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL("http://10.0.2.2:8000/api/tasks/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoInput(true);

                String accessToken = getAccessToken();
                if (accessToken == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Access token not found", Toast.LENGTH_SHORT).show());
                    return;
                }

                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                int responseCode = connection.getResponseCode();

                if (responseCode != 200) {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    String finalErrorMessage = errorResponse.toString();
                    runOnUiThread(() -> Toast.makeText(this, "Error  : " + finalErrorMessage, Toast.LENGTH_SHORT).show());
                    return;
                }

                // Replace the JSON parsing section in fetchTasksFromServer()
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                String rawResponse = response.toString();
                Log.d("API_RESPONSE", "Raw response: " + rawResponse);

                List<Task> tasks = new ArrayList<>();
                JSONArray tasksArray = new JSONArray(rawResponse);  // Parse directly as JSONArray

                for (int i = 0; i < tasksArray.length(); i++) {
                    JSONObject taskObject = tasksArray.getJSONObject(i);
                    Log.d("TASK_PARSING", "Processing task: " + taskObject.toString());

                    try {
                        Task task = new Task(
                                taskObject.getInt("id"),
                                taskObject.getString("status"),
                                taskObject.getString("priority"),
                                !taskObject.isNull("deadline") ? apiDateFormat.parse(taskObject.getString("deadline")) : null,
                                taskObject.getString("title"),
                                taskObject.getString("description")
                        );
                        tasks.add(task);
                        Log.d("TaskCreation", "Successfully created task: " + task.getTitle());
                    } catch (Exception e) {
                        Log.e("TaskCreation", "Error creating task from JSON: " + taskObject.toString(), e);
                    }
                }

                List<Task> finalTasks = tasks;
                runOnUiThread(() -> {
                    allTasks = new ArrayList<>(finalTasks);
                    Log.d("TaskCreation", "Total tasks loaded: " + allTasks.size());
                    updateUIWithTasks(finalTasks);
                });

            } catch (Exception e) {
                Log.e("FetchTasksError", "Error fetching tasks", e);
                String errorMessage = e.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show());

            } finally {
                try {
                    if (reader != null) reader.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    Log.e("Cleanup", "Error during cleanup", e);
                }
            }
        }).start();
    }

    @Override
    public void onDeleteTask(Task task) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://10.0.2.2:8000/api/tasks/delete/" + task.getId() + "/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("Authorization", "Bearer " + getAccessToken());

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_NO_CONTENT ||
                        responseCode == HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() -> {
                        allTasks.remove(task);
                        updateUIWithTasks(allTasks);
                        Toast.makeText(this, "Task deleted successfully", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    // Read error response
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    final String errorMessage = response.toString();
                    runOnUiThread(() ->
                            Toast.makeText(this, "Failed to delete task: " + errorMessage,
                                    Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchTasksFromServer(); // Refresh tasks when returning from EditTaskActivity
    }

    @Override
    public void onEditTask(Task task) {
        Intent intent = new Intent(this, EditTaskActivity.class);
        intent.putExtra("task_id", task.getId());
        intent.putExtra("task_title", task.getTitle());
        intent.putExtra("task_description", task.getDescription());
        intent.putExtra("task_status", task.getStatus());
        intent.putExtra("task_priority", task.getPriority());
        if (task.getDueDate() != null) {
            intent.putExtra("task_due_date", apiDateFormat.format(task.getDueDate()));
        }
        startActivity(intent);
    }

    private void updateUIWithTasks(List<Task> tasks) {
        int totalTasks = tasks.size();
        int completedTasks = 0;
        int pendingTasks = 0;
        int lowPriorityCount = 0;
        int mediumPriorityCount = 0;
        int highPriorityCount = 0;

        for (Task task : tasks) {
            switch (task.getStatus().toLowerCase()) {
                case "completed":
                    completedTasks++;
                    break;
                case "yet-to-start":
                case "in-progress":
                    pendingTasks++;
                    break;
            }

            switch (task.getPriority().toLowerCase()) {
                case "low":
                    lowPriorityCount++;
                    break;
                case "medium":
                    mediumPriorityCount++;
                    break;
                case "high":
                    highPriorityCount++;
                    break;
            }
        }

        totalTasksTextView.setText(String.valueOf(totalTasks));
        completedTasksTextView.setText(String.valueOf(completedTasks));
        pendingTasksTextView.setText(String.valueOf(pendingTasks));
        lowPriorityCountTextView.setText(String.valueOf(lowPriorityCount));
        mediumPriorityCountTextView.setText(String.valueOf(mediumPriorityCount));
        highPriorityCountTextView.setText(String.valueOf(highPriorityCount));

        taskAdapter.setTasks(tasks);
    }

    private void setupFilters() {
        priorityFilter = findViewById(R.id.priority_filter);
        progressFilter = findViewById(R.id.progress_filter);
        btnApplyFilters = findViewById(R.id.btn_apply_filters);
        btnClearFilters = findViewById(R.id.btn_clear_filters);

        ArrayAdapter<CharSequence> priorityAdapter = ArrayAdapter.createFromResource(this, R.array.priority_options, android.R.layout.simple_spinner_item);
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        priorityFilter.setAdapter(priorityAdapter);

        ArrayAdapter<CharSequence> progressAdapter = ArrayAdapter.createFromResource(this, R.array.progress_options, android.R.layout.simple_spinner_item);
        progressAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        progressFilter.setAdapter(progressAdapter);

        btnApplyFilters.setOnClickListener(v -> applyFilters());
        btnClearFilters.setOnClickListener(v -> clearFilters());
    }

    private void applyFilters() {
        String selectedPriority = priorityFilter.getSelectedItem().toString();
        String selectedProgress = progressFilter.getSelectedItem().toString();

        List<Task> filteredTasks = new ArrayList<>();
        for (Task task : allTasks) {
            boolean priorityMatch = selectedPriority.equals("All") || task.getPriority().equalsIgnoreCase(selectedPriority);
            boolean progressMatch = selectedProgress.equals("All") || task.getStatus().equalsIgnoreCase(selectedProgress);

            if (priorityMatch && progressMatch) {
                filteredTasks.add(task);
            }
        }

        updateUIWithTasks(filteredTasks);
    }

    private void clearFilters() {
        priorityFilter.setSelection(0); // Reset to the first item (All)
        progressFilter.setSelection(0); // Reset to the first item (All)
        updateUIWithTasks(allTasks);
    }
}