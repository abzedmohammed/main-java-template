package com.abzed.template.common;

import com.abzed.template.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "system_logs")
public class SystemLog extends BaseEntity {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SystemLogLevel level;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 1200)
    private String details;

    @Column(length = 150)
    private String actor;

    @Column(length = 50)
    private String status;
}
