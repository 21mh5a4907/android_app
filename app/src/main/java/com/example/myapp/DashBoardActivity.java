package com.example.myapp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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
import android.view.View;

import android.content.Intent;
import android.widget.EditText;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.app.AlertDialog;
import android.widget.ImageButton;

public class DashBoardActivity extends AppCompatActivity implements TaskAdapter.OnTaskActionListener{
    private TextView totalTasksTextView;
    private TextView completedTasksTextView;
    private TextView pendingTasksTextView;
    private TextView priorityTaskCountTextView;
    private Spinner statusSpinner;
    private Spinner prioritySpinner;

    private TextView startDatePicker;

    private TextView endDatePicker;
    private Button applyFilters;
    private Button clearFilters;
    private Date startDate;
    private Date endDate;
    private List<Task> allTasks;

    private static final String DATE_FORMAT_DISPLAY = "MMM dd, yyyy";
    private static final String DATE_FORMAT_API = "yyyy-MM-dd";
    private SimpleDateFormat displayDateFormat;
    private SimpleDateFormat apiDateFormat;

    private RecyclerView tasksRecyclerView;
    private TaskAdapter taskAdapter;
    private FloatingActionButton fabAddTask;

    private ImageButton logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        logoutButton = findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(v -> showLogoutConfirmation());

        displayDateFormat = new SimpleDateFormat(DATE_FORMAT_DISPLAY, Locale.getDefault());
        apiDateFormat = new SimpleDateFormat(DATE_FORMAT_API, Locale.getDefault());

        initializeViews();
        initializeFilterViews();
        setupSpinners();
        setupDatePickers();
        setupFilterButtons();
        setupFab();
        fetchTasksFromServer();
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
        fabAddTask = findViewById(R.id.fab_add_task);
        fabAddTask.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTaskActivity.class);
            startActivity(intent);
        });
    }

    private void initializeViews() {
        totalTasksTextView = findViewById(R.id.total_task_count);
        completedTasksTextView = findViewById(R.id.completed_task_count);
        pendingTasksTextView = findViewById(R.id.pending_task_count);
        priorityTaskCountTextView = findViewById(R.id.priority_task_count);
        tasksRecyclerView = findViewById(R.id.tasks_recycler_view);
        setupRecyclerView();
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


    private void initializeFilterViews() {
        statusSpinner = findViewById(R.id.status_spinner);
        prioritySpinner = findViewById(R.id.priority_spinner);
        startDatePicker = findViewById(R.id.start_date_picker);
        endDatePicker = findViewById(R.id.end_date_picker);
        applyFilters = findViewById(R.id.apply_filters);
        clearFilters = findViewById(R.id.clear_filters);
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"All", "yet-to-start", "in-progress", "completed"});
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(statusAdapter);

        ArrayAdapter<CharSequence> priorityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"All", "low", "medium", "high"});
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritySpinner.setAdapter(priorityAdapter);
    }

    private void setupDatePickers() {
        startDatePicker.setText("Select Start Date");
        endDatePicker.setText("Select End Date");
        startDatePicker.setOnClickListener(v -> showDatePicker(true));
        endDatePicker.setOnClickListener(v -> showDatePicker(false));
    }

    private void setupFilterButtons() {
        applyFilters.setOnClickListener(v -> applyTaskFilters());
        clearFilters.setOnClickListener(v -> clearFilters());
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = Calendar.getInstance();
        if (isStartDate && startDate != null) {
            calendar.setTime(startDate);
        } else if (!isStartDate && endDate != null) {
            calendar.setTime(endDate);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth);
                    setToStartOfDay(selectedDate);

                    Date selectedDateTime = selectedDate.getTime();
                    TextView dateText = isStartDate ? startDatePicker : endDatePicker;

                    if (isStartDate) {
                        startDate = selectedDateTime;
                        String formattedDate = displayDateFormat.format(startDate);
                        dateText.setText(formattedDate);
                        Log.d("DatePicker", "Set start date: " + formattedDate);
                    } else {
                        endDate = selectedDateTime;
                        String formattedDate = displayDateFormat.format(endDate);
                        dateText.setText(formattedDate);
                        Log.d("DatePicker", "Set end date: " + formattedDate);
                    }

                    applyTaskFilters();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    private void setToStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
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
                URL url = new URL("http://10.0.2.2:8000/api/tasks/list/");
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
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + finalErrorMessage, Toast.LENGTH_SHORT).show());
                    return;
                }

                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                // Parse JSON response
                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray tasksArray = jsonResponse.getJSONArray("results");
                List<Task> tasks = new ArrayList<>();

                // Log the raw JSON response
                Log.d("TaskFetch", "Raw JSON response: " + response.toString());
                Log.d("TaskFetch", "Number of tasks in response: " + tasksArray.length());

                for (int i = 0; i < tasksArray.length(); i++) {
                    JSONObject taskObject = tasksArray.getJSONObject(i);
                    // Log the individual task JSON
                    Log.d("TaskCreation", "Processing task JSON: " + taskObject.toString());

                    int id = taskObject.getInt("id");
                    String status = taskObject.getString("status");
                    String priority = taskObject.getString("priority");
                    String title = taskObject.getString("title");
                    String description = taskObject.getString("description");

                    Date dueDate = null;
                    if (!taskObject.isNull("deadline")) {
                        String deadlineStr = taskObject.getString("deadline");
                        try {
                            dueDate = apiDateFormat.parse(deadlineStr);
                        } catch (ParseException e) {
                            Log.e("DateParse", "Error parsing deadline: " + deadlineStr, e);
                        }
                    }

                    Task task = new Task(id, status, priority, dueDate, title, description);
                    tasks.add(task);
                    Log.d("TaskCreation", String.format("Created task - Title: %s, Status: %s, Priority: %s, Deadline: %s",
                            title, status, priority,
                            dueDate != null ? apiDateFormat.format(dueDate) : "null"));
                }

                // Update UI on main thread
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

    private void applyTaskFilters() {
        if (allTasks == null || allTasks.isEmpty()) {
            Toast.makeText(this, "No tasks to filter", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String selectedStatus = statusSpinner.getSelectedItem().toString();
            String selectedPriority = prioritySpinner.getSelectedItem().toString();

            // Log all tasks before filtering
            Log.d("TaskFilter", "Before filtering - Total tasks: " + allTasks.size());
            for (Task task : allTasks) {
                Log.d("TaskFilter", String.format("Task - Title: %s, Status: %s, Priority: %s, Due Date: %s",
                        task.getTitle(),
                        task.getStatus(),
                        task.getPriority(),
                        task.getDueDate() != null ? apiDateFormat.format(task.getDueDate()) : "null"));
            }

            TaskFilter filter = new TaskFilter.Builder()
                    .setStatus(selectedStatus)
                    .setPriority(selectedPriority)
                    .setDateRange(startDate, endDate)
                    .build();

            List<Task> filteredTasks = filter.apply(allTasks);

            // Log filter parameters
            Log.d("TaskFilter", String.format("Filter parameters - Status: %s, Priority: %s",
                    selectedStatus, selectedPriority));
            if (startDate != null || endDate != null) {
                Log.d("TaskFilter", String.format("Date range: %s to %s",
                        startDate != null ? apiDateFormat.format(startDate) : "none",
                        endDate != null ? apiDateFormat.format(endDate) : "none"));
            }

            // Log filtered results
            Log.d("TaskFilter", "After filtering - Tasks: " + filteredTasks.size());
            for (Task task : filteredTasks) {
                Log.d("TaskFilter", String.format("Filtered Task - Title: %s, Status: %s, Priority: %s, Due Date: %s",
                        task.getTitle(),
                        task.getStatus(),
                        task.getPriority(),
                        task.getDueDate() != null ? apiDateFormat.format(task.getDueDate()) : "null"));
            }

            updateUIWithTasks(filteredTasks);

        } catch (IllegalArgumentException e) {
            Log.e("TaskFilter", "Filter error: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
    private void clearFilters() {
        statusSpinner.setSelection(0);
        prioritySpinner.setSelection(0);
        startDate = null;
        endDate = null;
        startDatePicker.setText("Select Start Date");
        endDatePicker.setText("Select End Date");

        if (allTasks != null) {
            updateUIWithTasks(allTasks);
            Toast.makeText(this, "Filters cleared", Toast.LENGTH_SHORT).show();
        }
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
        priorityTaskCountTextView.setText(String.format("Low: %d, Medium: %d, High: %d",
                lowPriorityCount, mediumPriorityCount, highPriorityCount));

        taskAdapter.setTasks(tasks);
    }
}