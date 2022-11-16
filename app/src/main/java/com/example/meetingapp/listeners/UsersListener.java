package com.example.meetingapp.listeners;

import com.example.meetingapp.models.User;

public interface UsersListener {

    //To start video meeting
    void initiatemeetingapp(User user);

    //To start audio meeting
    void initiateAudioMeeting(User user);

    //To start Multiple User Action
    void onMultipleUsersAction(Boolean isMultipleUserAction);
}
