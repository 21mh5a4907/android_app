package com.example.myapp;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import com.android.volley.DefaultRetryPolicy;
import java.util.Map;
import java.nio.charset.StandardCharsets;

public class SuperuserDashboardActivity extends AppCompatActivity {

    private EditText searchEditText;
    private Button searchButton;
    private Button createUserButton;
    private ListView usersListView;
    private ListView tasksListView;
    private RequestQueue requestQueue;
    private UserAdapter userAdapter;
    private ArrayList<User> users;
    private ArrayList<Task> tasks;
    private String authToken;
    private TextView userCountTextView;
    private TextView taskCountTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.super_user_dashboard);

        // Initialize RequestQueue first
        requestQueue = Volley.newRequestQueue(this);

        // Initialize views
        initializeViews();

        // Initialize collections
        initializeCollections();

        // Get and verify auth token
        authToken = getIntent().getStringExtra("auth_token");
        Log.d("SuperuserDashboard", "Received token: " + authToken);

        if (authToken == null || authToken.isEmpty()) {
            Log.e("SuperuserDashboard", "No auth token received");
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set up click listeners
        setupClickListeners();

        // Fetch initial data
        fetchUsers();
        fetchTasks();

        // Initialize count views
        userCountTextView = findViewById(R.id.userCountTextView);
        taskCountTextView = findViewById(R.id.taskCountTextView);
    }

    private void initializeViews() {
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        createUserButton = findViewById(R.id.createUserButton);
        usersListView = findViewById(R.id.usersListView);
        tasksListView = findViewById(R.id.tasksListView);
    }

    private void initializeCollections() {
        users = new ArrayList<>();
        tasks = new ArrayList<>();
        userAdapter = new UserAdapter();
        usersListView.setAdapter(userAdapter);
    }

    private void setupClickListeners() {
        searchButton.setOnClickListener(v -> {
            String query = searchEditText.getText().toString().trim();
            if (query.isEmpty()) {
                // If search is empty, show all users
                fetchUsers();
            } else {
                searchUsers(query);
            }
        });

        // Also add search on text change (optional)
        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    fetchUsers();
                } else if (query.length() >= 2) { // Search after 2 characters
                    searchUsers(query);
                }
            }
        });

        createUserButton.setOnClickListener(v -> showCreateUserDialog());
    }

    private void searchUsers(String query) {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(this);
        }

        // URL encode the query parameter
        String encodedQuery;
        try {
            encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            Log.e("SuperuserDashboard", "Error encoding query", e);
            encodedQuery = query;
        }

        String url = "http://10.0.2.2:8000/api/users/search/?username=" + encodedQuery;
        Log.d("SuperuserDashboard", "Search URL: " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    Log.d("SuperuserDashboard", "Search response: " + response.toString());
                    users.clear();
                    for (int i = 0; i < response.length(); i++) {
                        try {
                            JSONObject user = response.getJSONObject(i);
                            users.add(new User(user.getInt("id"), user.getString("username")));
                        } catch (JSONException e) {
                            Log.e("SuperuserDashboard", "JSON parsing error", e);
                        }
                    }
                    userAdapter.notifyDataSetChanged();
                },
                error -> {
                    Log.e("SuperuserDashboard", "Search error", error);
                    if (error.networkResponse != null) {
                        String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        Log.e("SuperuserDashboard", "Error body: " + responseBody);
                        Log.e("SuperuserDashboard", "Status Code: " + error.networkResponse.statusCode);
                    }
                    Toast.makeText(this, "Error searching users", Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + authToken.trim());
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    private void fetchUsers() {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(this);
        }

        String url = "http://10.0.2.2:8000/api/users/";
        Log.d("SuperuserDashboard", "Fetching users with token: " + authToken);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    Log.d("SuperuserDashboard", "Users response: " + response.toString());
                    processUsersResponse(response);
                },
                error -> {
                    Log.e("SuperuserDashboard", "Error fetching users", error);
                    if (error.networkResponse != null) {
                        String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        Log.e("SuperuserDashboard", "Error body: " + responseBody);
                        Log.e("SuperuserDashboard", "Status Code: " + error.networkResponse.statusCode);
                    }
                    String message = "Error fetching users";
                    if (error instanceof TimeoutError) {
                        message = "Connection timed out";
                    }
                    Toast.makeText(SuperuserDashboardActivity.this, message, Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + authToken.trim());
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                15000,  // 15 seconds timeout
                2,      // Max retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    private void fetchTasks() {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(this);
        }

        String url = "http://10.0.2.2:8000/api/all-tasks/";

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    Log.d("SuperuserDashboard", "Tasks response: " + response.toString());
                    processTasksResponse(response);
                },
                error -> {
                    Log.e("SuperuserDashboard", "Error fetching tasks", error);
                    if (error.networkResponse != null) {
                        String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        Log.e("SuperuserDashboard", "Error body: " + responseBody);
                        Log.e("SuperuserDashboard", "Status Code: " + error.networkResponse.statusCode);
                    }
                    String message = "Error fetching tasks";
                    if (error instanceof TimeoutError) {
                        message = "Connection timed out";
                    }
                    Toast.makeText(SuperuserDashboardActivity.this, message, Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + authToken.trim());
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }

    private void showCreateUserDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_user, null);
        EditText usernameEditText = dialogView.findViewById(R.id.newUsernameEditText);
        EditText passwordEditText = dialogView.findViewById(R.id.newPasswordEditText);
        EditText emailEditText = dialogView.findViewById(R.id.newEmailEditText);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Create New User")
                .setView(dialogView)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Override the positive button click to prevent dialog from closing on error
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String email = emailEditText.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            createNewUser(username, password, email, dialog);
        });
    }

    private void createNewUser(String username, String password, String email, AlertDialog dialog) {
        String url = "http://10.0.2.2:8000/api/register/";
        JSONObject userObject = new JSONObject();
        try {
            userObject.put("username", username);
            userObject.put("password", password);
            userObject.put("email", email);
        } catch (JSONException e) {
            Log.e("SuperuserDashboard", "JSON creation error", e);
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, userObject,
                response -> {
                    Toast.makeText(this, "User created successfully", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    fetchUsers();  // Refresh the list
                },
                error -> {
                    handleNetworkError(error, "Creating user");
                    if (error.networkResponse != null && error.networkResponse.statusCode == 400) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            JSONObject errorObj = new JSONObject(responseBody);
                            String errorMessage = errorObj.optString("detail", "Username may already exist");
                            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                        } catch (JSONException e) {
                            Log.e("SuperuserDashboard", "Error parsing error response", e);
                        }
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                return createAuthHeaders();
            }
        };

        configureRequest(request);
        requestQueue.add(request);
    }

    private void configureRequest(JsonArrayRequest request) {
        request.setRetryPolicy(new DefaultRetryPolicy(
                15000,  // 15 seconds timeout
                2,      // Max retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));
    }

    private void configureRequest(JsonObjectRequest request) {
        request.setRetryPolicy(new DefaultRetryPolicy(
                15000,  // 15 seconds timeout
                2,      // Max retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));
    }

    private Map<String, String> createAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + authToken.trim());
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private void handleNetworkError(VolleyError error, String operation) {
        Log.e("SuperuserDashboard", "Error in " + operation, error);
        if (error.networkResponse != null) {
            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
            Log.e("SuperuserDashboard", "Error body: " + responseBody);
            Log.e("SuperuserDashboard", "Status Code: " + error.networkResponse.statusCode);
        }

        final String message;
        if (error instanceof TimeoutError) {
            message = "Request timed out. Please try again.";
        } else {
            message = error.getMessage() != null ? error.getMessage() : "Unknown error occurred";
        }

        final String finalOperation = operation;
        runOnUiThread(() ->
                Toast.makeText(SuperuserDashboardActivity.this,
                        finalOperation + " failed: " + message,
                        Toast.LENGTH_SHORT).show()
        );
    }

    private void processUsersResponse(JSONArray response) {
        users.clear();
        for (int i = 0; i < response.length(); i++) {
            try {
                JSONObject user = response.getJSONObject(i);
                users.add(new User(user.getInt("id"), user.getString("username")));
            } catch (JSONException e) {
                Log.e("SuperuserDashboard", "JSON parsing error", e);
            }
        }
        runOnUiThread(() -> {
            userAdapter.notifyDataSetChanged();
            userCountTextView.setText(String.valueOf(users.size()));
        });
    }

    private void processTasksResponse(JSONArray response) {
        tasks.clear();
        for (int i = 0; i < response.length(); i++) {
            try {
                JSONObject taskObj = response.getJSONObject(i);
                String title = taskObj.getString("title");
                String creator = taskObj.optString("creator", "Unknown");
                String priority = taskObj.optString("priority", "Not set");
                String status = taskObj.optString("status", "Not set");
                String deadline = taskObj.has("deadline") ? taskObj.getString("deadline") : "No deadline";

                tasks.add(new Task(title, creator, priority, status, deadline));
            } catch (JSONException e) {
                Log.e("SuperuserDashboard", "JSON parsing error", e);
            }
        }

        runOnUiThread(() -> {
            TaskAdapter adapter = new TaskAdapter();
            tasksListView.setAdapter(adapter);
            taskCountTextView.setText(String.valueOf(tasks.size()));
        });
    }

    private class UserAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return users.size();
        }

        @Override
        public Object getItem(int position) {
            return users.get(position);
        }

        @Override
        public long getItemId(int position) {
            return users.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.user_list_item, parent, false);
                holder = new ViewHolder();
                holder.usernameTextView = convertView.findViewById(R.id.usernameTextView);
                holder.deleteButton = convertView.findViewById(R.id.deleteUserButton);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final User user = users.get(position);
            holder.usernameTextView.setText(user.getUsername());

            // Set click listener on the entire view
            convertView.setOnClickListener(v -> fetchUserTasks(user));

            if (holder.deleteButton != null) {
                if (user.getUsername().equals(getIntent().getStringExtra("username"))) {
                    holder.deleteButton.setVisibility(View.GONE);
                } else {
                    holder.deleteButton.setVisibility(View.VISIBLE);
                    holder.deleteButton.setOnClickListener(v -> showDeleteConfirmationDialog(user));
                }
            }

            return convertView;
        }
    }

    private static class ViewHolder {
        TextView usernameTextView;
        Button deleteButton;
    }

    private void showDeleteConfirmationDialog(User user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete user: " + user.getUsername() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteUser(user.getId());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteUser(int userId) {
        String url = "http://10.0.2.2:8000/api/users/" + userId + "/delete/";
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.DELETE, url, null,
                response -> {
                    Toast.makeText(this, "User deleted successfully", Toast.LENGTH_SHORT).show();
                    fetchUsers();  // Refresh the list
                },
                error -> handleNetworkError(error, "Deleting user")) {
            @Override
            public Map<String, String> getHeaders() {
                return createAuthHeaders();
            }
        };
        configureRequest(request);
        requestQueue.add(request);
    }

    private static class User {
        private final int id;
        private final String username;

        User(int id, String username) {
            this.id = id;
            this.username = username;
        }

        int getId() {
            return id;
        }

        String getUsername() {
            return username;
        }
    }

    private class Task {
        private final String title;
        private final String creator;
        private final String priority;
        private final String status;
        private final String deadline;

        Task(String title, String creator, String priority, String status, String deadline) {
            this.title = title;
            this.creator = creator;
            this.priority = priority;
            this.status = status;
            this.deadline = deadline;
        }

        // Add getters
        String getTitle() { return title; }
        String getCreator() { return creator; }
        String getPriority() { return priority; }
        String getStatus() { return status; }
        String getDeadline() { return deadline; }
    }

    private class TaskAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return tasks.size();
        }

        @Override
        public Object getItem(int position) {
            return tasks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TaskViewHolder holder;

            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.task_list_item, parent, false);
                holder = new TaskViewHolder();
                holder.titleTextView = convertView.findViewById(R.id.taskTitleTextView);
                holder.creatorTextView = convertView.findViewById(R.id.taskCreatorTextView);
                holder.priorityTextView = convertView.findViewById(R.id.taskPriorityTextView);
                holder.statusTextView = convertView.findViewById(R.id.taskStatusTextView);
                holder.deadlineTextView = convertView.findViewById(R.id.taskDeadlineTextView);
                convertView.setTag(holder);
            } else {
                holder = (TaskViewHolder) convertView.getTag();
            }

            Task task = tasks.get(position);
            holder.titleTextView.setText(task.getTitle() != null ? task.getTitle() : "No Title");
            holder.creatorTextView.setText("Created by: " + (task.getCreator() != null ? task.getCreator() : "Unknown"));
            holder.priorityTextView.setText("Priority: " + (task.getPriority() != null ? task.getPriority() : "Not set"));
            holder.statusTextView.setText("Status: " + (task.getStatus() != null ? task.getStatus() : "Not set"));
            holder.deadlineTextView.setText("Due: " + (task.getDeadline() != null ? task.getDeadline() : "No deadline"));

            return convertView;
        }
    }

    private class TaskViewHolder {
        TextView titleTextView;
        TextView creatorTextView;
        TextView priorityTextView;
        TextView statusTextView;
        TextView deadlineTextView;
    }

    // Add this method to fetch tasks for a specific user
    private void fetchUserTasks(User user) {
        String url = "http://10.0.2.2:8000/api/users/" + user.getId() + "/tasks/";

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    Log.d("SuperuserDashboard", "User tasks response: " + response.toString());
                    processTasksResponse(response);
                    Toast.makeText(this, "Showing tasks for " + user.getUsername(), Toast.LENGTH_SHORT).show();
                },
                error -> handleNetworkError(error, "Fetching user tasks")) {
            @Override
            public Map<String, String> getHeaders() {
                return createAuthHeaders();
            }
        };

        configureRequest(request);
        requestQueue.add(request);
    }
}