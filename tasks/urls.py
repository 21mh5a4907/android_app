from django.urls import path
from .views import (
    UserProfileView, ProfileUpdateView, UserUpdateView, TaskListView, TaskCreateView,
    TaskUpdateView, TaskDeleteView, RegisterView, LoginView, LogoutView, 
    SuperuserLoginView, SearchUsersView, UserListView
)

urlpatterns = [
    # ... existing URLs ...
    
    # Move this up with other user-related URLs
    path('users/', UserListView.as_view(), name='user-list'),
    path('users/search/', SearchUsersView.as_view(), name='search-users'),
    
    # ... rest of your URLs ...
] 