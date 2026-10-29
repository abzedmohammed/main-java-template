package com.abzed.template.template;

import com.abzed.template.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;

import java.util.UUID;

/**
 * A reusable email design shell (e.g. {@code OTP}, {@code GENERAL}). The HTML holds
 * {@code {{placeholder}}} slots filled at send time with the resolved message content.
 */
@Getter
@Setter
@Entity
@Table(name = "email_templates")
public class EmailTemplate extends BaseEntity {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID emlId;

    @Column(nullable = false, unique = true, length = 100)
    private String emlCode;

    @Column(nullable = false)
    private String emlName;

    @Column(nullable = false, columnDefinition = "text")
    private String emlHtml;

    @Column(nullable = false)
    private boolean emlActive = true;
}
