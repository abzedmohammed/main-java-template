package com.abzed.template.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsrEmail(String usrEmail);
    boolean existsByUsrEmail(String usrEmail);
    boolean existsByUsrMobileNumberAndUsrStatusNot(String usrMobileNumber, UserStatus usrStatus);
}
