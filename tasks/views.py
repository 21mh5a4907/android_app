class SuperuserLoginView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = LoginSerializer(data=request.data)
        if serializer.is_valid():
            user = serializer.validated_data.get('user')  
            if user and user.is_superuser:
                # Generate token
                refresh = RefreshToken.for_user(user)
                return Response({
                    "message": "Superuser logged in successfully!",
                    "access": str(refresh.access_token),
                    "refresh": str(refresh),
                    "username": user.username,
                    "is_superuser": user.is_superuser
                }, status=status.HTTP_200_OK)
            else:
                return Response({"message": "Only superusers can log in here."}, 
                             status=status.HTTP_403_FORBIDDEN)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

class UserListView(APIView):
    permission_classes = [IsAuthenticated, IsAdminUser]

    def get(self, request):
        users = User.objects.all()
        serializer = UserSerializer(users, many=True)
        return Response(serializer.data) 

class TaskListView(generics.ListAPIView):
    serializer_class = TaskSerializer
    permission_classes = [IsAuthenticated]
    filter_backends = (DjangoFilterBackend,)
    filterset_class = TaskFilter
    ordering_fields = ['created_at', 'deadline']
    ordering = ['created_at']

    def get_queryset(self):
        if self.request.user.is_superuser:
            return Task.objects.all()  # Superusers can see all tasks
        return Task.objects.filter(user=self.request.user)  # Regular users see their own tasks

class UserProfileView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        try:
            if request.user.is_superuser:
                # For superusers, return all user profiles
                user_profiles = UserProfile.objects.all()
                profiles_data = []
                for profile in user_profiles:
                    data = UserProfileSerializer(profile).data
                    data['username'] = profile.user.username
                    data['is_superuser'] = profile.user.is_superuser
                    data['email'] = profile.user.email
                    profiles_data.append(data)
                return Response(profiles_data)
            else:
                # For regular users, return only their profile
                user_profile = UserProfile.objects.get(user=request.user)
                data = UserProfileSerializer(user_profile).data
                data['username'] = request.user.username
                data['is_superuser'] = request.user.is_superuser
                data['email'] = request.user.email
                return Response(data)
        except UserProfile.DoesNotExist:
            # Create profile if it doesn't exist
            user_profile = UserProfile.objects.create(user=request.user)
            data = UserProfileSerializer(user_profile).data
            data['username'] = request.user.username
            data['is_superuser'] = request.user.is_superuser
            data['email'] = request.user.email
            return Response(data)
        except Exception as e:
            return Response(
                {"error": f"An error occurred: {str(e)}"}, 
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

    def put(self, request):
        try:
            if request.user.is_superuser:
                # Superusers can update any user's profile
                user_id = request.data.get('user_id')
                if user_id:
                    user_profile = UserProfile.objects.get(user_id=user_id)
                else:
                    return Response(
                        {"error": "user_id is required for superuser profile updates"}, 
                        status=status.HTTP_400_BAD_REQUEST
                    )
            else:
                # Regular users can only update their own profile
                user_profile = UserProfile.objects.get(user=request.user)

            serializer = UserProfileSerializer(
                user_profile, 
                data=request.data, 
                partial=True
            )
            
            if serializer.is_valid():
                serializer.save()
                data = serializer.data
                data['username'] = user_profile.user.username
                data['is_superuser'] = user_profile.user.is_superuser
                data['email'] = user_profile.user.email
                return Response(data)
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
            
        except UserProfile.DoesNotExist:
            return Response(
                {"error": "Profile not found"}, 
                status=status.HTTP_404_NOT_FOUND
            )
        except Exception as e:
            return Response(
                {"error": f"An error occurred: {str(e)}"}, 
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )