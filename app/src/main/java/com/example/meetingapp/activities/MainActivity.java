package com.example.meetingapp.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.gson.Gson;
import com.example.meetingapp.R;
import com.example.meetingapp.adapters.UsersAdapter;
import com.example.meetingapp.listeners.UsersListener;
import com.example.meetingapp.models.User;
import com.example.meetingapp.utilities.Constants;
import com.example.meetingapp.utilities.PreferenceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements UsersListener {

    private static final String TAG = "mainActivity";
    private PreferenceManager preferenceManager;
    private TextView textTitle, errorMessage;
    private ImageView signOut, imageConference;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Runnable refresh;

    private RecyclerView usersRecyclerView;
    private List<User> users = new ArrayList<>();
    private UsersAdapter usersAdapter;

    private int REQUEST_CODE_BATTERY_OPTIMIZATIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textTitle = findViewById(R.id.textTitle);
        signOut = findViewById(R.id.imageSignOut);
        usersRecyclerView = findViewById(R.id.userRecyclerView);
        errorMessage = findViewById(R.id.errorMessage);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        imageConference = findViewById(R.id.imageConference);

        preferenceManager = new PreferenceManager(getApplicationContext());

        textTitle.setText(String.format("%s %s", preferenceManager.getString(Constants.KEY_FIRST_NAME), preferenceManager.getString(Constants.KEY_LAST_NAME)));

        signOut.setOnClickListener(v -> signOut());

        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                sendFCMTokenToDatabase(task.getResult().getToken());
            }
        });

        usersAdapter = new UsersAdapter(users, this);
        usersRecyclerView.setAdapter(usersAdapter);
        getUsers();

        swipeRefreshLayout.setOnRefreshListener(this::getUsers);

        Handler handler = new Handler();

        refresh = () -> {
            //TODO: add online icon on users and refresh using this handler to show online and offline status
            handler.postDelayed(refresh, 5000);
        };
        handler.post(refresh);
        checkForBatteryOptimizations();
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put(Constants.KEY_IS_ONLINE, true);
        db.collection(Constants.KEY_COLLECTION_USERS).document(preferenceManager.getString(Constants.KEY_USER_ID))
                .update(data);
    }

    private void getUsers(){
        swipeRefreshLayout.setRefreshing(true);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(Constants.KEY_COLLECTION_USERS).get()
                .addOnCompleteListener(task -> {
                    swipeRefreshLayout.setRefreshing(false);
                    String myUserId = preferenceManager.getString(Constants.KEY_USER_ID);
                    if (task.isSuccessful() && task.getResult() != null){
                        users.clear();
                        for (QueryDocumentSnapshot documentSnapshot : task.getResult()){
                            if (myUserId.equals(documentSnapshot.getId())){
                                continue;
                            }
                            User user = new User();
                            user.firstName = documentSnapshot.getString(Constants.KEY_FIRST_NAME);
                            user.lastName = documentSnapshot.getString(Constants.KEY_LAST_NAME);
                            user.email = documentSnapshot.getString(Constants.KEY_EMAIL);
                            user.token = documentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                            users.add(user);
                        }
                        if (users.size() > 0){
                            usersAdapter.notifyDataSetChanged();
                        }
                        else{
                            errorMessage.setText(String.format("%s", "No User Available"));
                            errorMessage.setVisibility(View.VISIBLE);
                        }
                    }
                    else{
                        errorMessage.setText(String.format("%s", "No User Available"));
                        errorMessage.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void sendFCMTokenToDatabase(String token){
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference documentReference = db.collection(Constants.KEY_COLLECTION_USERS).document(preferenceManager.getString(Constants.KEY_USER_ID));
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnSuccessListener(aVoid -> {})
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Token failed", Toast.LENGTH_SHORT).show());
    }


    private void signOut(){
        Toast.makeText(this, "Signing out...", Toast.LENGTH_SHORT).show();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference documentReference = db.collection(Constants.KEY_COLLECTION_USERS).document(preferenceManager.getString(Constants.KEY_USER_ID));

        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        updates.put(Constants.KEY_IS_ONLINE, false);
        documentReference.update(updates)
                .addOnSuccessListener(aVoid -> {
                    //sign out
                    //clearing preferences for making KEY_IS_SIGNED_IN = false
                    preferenceManager.clearPreferences();
                    startActivity(new Intent(getApplicationContext(), SignInActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "unable to sign out", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void initiatemeetingapp(User user) {
        if (user.token == null || user.token.trim().isEmpty()){
            //user is offline
            Toast.makeText(this, String.format("%s %s %s",user.firstName ,user.lastName , "is not available for meeting"), Toast.LENGTH_LONG).show();
        }
        else{
            //user is online
            Intent intent = new Intent(getApplicationContext(), OutgoingInvitationActivity.class);
            intent.putExtra("user",user);
            intent.putExtra("type","video");
            startActivity(intent);
        }
    }

    @Override
    public void initiateAudioMeeting(User user) {
        if (user.token == null || user.token.trim().isEmpty()){
            //user is offline
            Toast.makeText(this, String.format("%s %s %s",user.firstName ,user.lastName , "is not available for  meeting"), Toast.LENGTH_LONG).show();
        }
        else{
            //user is online
            Intent intent = new Intent(getApplicationContext(), OutgoingInvitationActivity.class);
            intent.putExtra("user",user);
            intent.putExtra("type", "audio");
            startActivity(intent);

        }
    }

    @Override
    public void onMultipleUsersAction(Boolean isMultipleUserAction) {
        if (isMultipleUserAction){
            imageConference.setVisibility(View.VISIBLE);
            imageConference.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), OutgoingInvitationActivity.class);
                intent.putExtra("selectedUsers",new Gson().toJson(usersAdapter.getSelectedUsers()));
                intent.putExtra("type","video");
                intent.putExtra("isMultiple",true);
                startActivity(intent);
            });
        }
        else{
            imageConference.setVisibility(View.GONE);
        }
    }

    private void checkForBatteryOptimizations(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            assert powerManager != null;
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())){
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Warning");
                builder.setMessage("Battery optimization is enabled. it can interrupt running background services");
                builder.setPositiveButton("Disable", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATIONS);
                    }
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
                builder.create().show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_BATTERY_OPTIMIZATIONS){
            checkForBatteryOptimizations();
        }
    }
}






