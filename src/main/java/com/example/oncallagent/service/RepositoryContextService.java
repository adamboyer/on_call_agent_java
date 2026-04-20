package com.example.oncallagent.service;

import com.example.oncallagent.model.RepositoryContext;
import org.springframework.stereotype.Service;

@Service
public class RepositoryContextService {

    public RepositoryContext resolveContext(String targetSystem) {
        return new RepositoryContext(
                targetSystem,
                "master",
                "/tmp/" + targetSystem
        );
    }
}