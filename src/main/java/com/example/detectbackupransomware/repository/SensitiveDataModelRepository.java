package com.example.detectbackupransomware.repository;

import com.example.detectbackupransomware.entity.SensitiveDataModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SensitiveDataModelRepository extends JpaRepository<SensitiveDataModel, Long> {

    List<SensitiveDataModel> findByActiveTrue();

    List<SensitiveDataModel> findByDataType(SensitiveDataModel.SensitiveDataType dataType);

    List<SensitiveDataModel> findByTableName(String tableName);
}

