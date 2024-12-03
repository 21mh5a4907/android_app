from django.urls import path
from .views import UserListView, SuperuserLoginView, TaskListView

urlpatterns = [
    # ... your other URLs ...
    path('api/users/', UserListView.as_view(), name='user-list'),
    path('api/superuser-login/', SuperuserLoginView.as_view(), name='superuser-login'),
    path('api/tasks/', TaskListView.as_view(), name='task-list'),
] 