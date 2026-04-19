package com.example.oncallagent.service;

import com.example.oncallagent.model.OnCallUser;
import org.springframework.stereotype.Service;

@Service
public class OnCallService {

    public OnCallUser getCurrentOnCall() {
        return new OnCallUser(
                "Alex Oncall",
                "U12345678",
                "platform"
        );
    }
}
