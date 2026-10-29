package com.abzed.template.template;

import com.abzed.template.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;

import java.util.UUID;

/**
 * Reusable, per-channel message content interpolated with runtime variables at
 * send time. For {@link MessageChannel#EMAIL}, {@code mtpLayoutCode} selects the
 * {@link EmailTemplate} design the rendered content is wrapped in.
 */
@Getter
@Setter
@Entity
@Table(name = "message_templates")
public class MessageTemplate extends BaseEntity {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID mtpId;

    @Column(nullable = false, unique = true, length = 100)
    private String mtpCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageChannel mtpChannel;

    private String mtpSubject;

    private String mtpTitle;

    @Column(nullable = false, columnDefinition = "text")
    private String mtpBody;

    @Column(length = 100)
    private String mtpLayoutCode;

    @Column(nullable = false)
    private boolean mtpActive = true;
}
