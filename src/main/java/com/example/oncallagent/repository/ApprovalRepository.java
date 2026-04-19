package com.example.oncallagent.repository;

import com.example.oncallagent.entity.ApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {
}
