package com.example.oncallagent.service;

import com.example.oncallagent.model.RepositoryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CodeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(CodeRetrievalService.class);

    public String fetchCodeContext(RepositoryContext context, String errorMessage) {
        log.info("Fetching code context. repoName={}, baseBranch={}",
                context.repoName(), context.baseBranch());

        return """
                // Stub code context
                public class ExampleService {
                    public void process(Order order) {
                        System.out.println(order.getCustomer().getName());
                    }
                }
                """;
    }
}