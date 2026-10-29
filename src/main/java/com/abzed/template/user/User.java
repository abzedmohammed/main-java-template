package com.abzed.template.user;

import com.abzed.template.audit.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID usrId;

    @Column(nullable = false)
    private String usrFullName;

    @Column(nullable = false, unique = true)
    private String usrEmail;

    private String usrMobileNumber;

    @JsonIgnore
    private String usrPasswordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole usrRole = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider usrProvider = AuthProvider.LOCAL;

    private String usrProviderId;

    @Column(nullable = false)
    private boolean usrEmailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus usrStatus = UserStatus.DRAFT;
}
