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
    private UUID slgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SystemLogLevel slgLevel;

    @Column(nullable = false)
    private String slgCategory;

    @Column(nullable = false, length = 300)
    private String slgTitle;

    @Column(nullable = false, length = 1200)
    private String slgDetails;

    @Column(length = 150)
    private String slgActor;

    @Column(length = 50)
    private String slgStatus;
}
