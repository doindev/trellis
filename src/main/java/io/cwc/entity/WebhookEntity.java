package io.cwc.entity;

import io.cwc.util.JsonObjectConverter;
import io.cwc.util.NanoId;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "webhooks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"method", "path", "is_test"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String workflowId;

    @Column(nullable = false)
    private String nodeId;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String path;

    @Builder.Default
    @Column(nullable = false)
    private String securityChain = "none";

    @Builder.Default
    @Column(nullable = false)
    private boolean isTest = false;

    @Builder.Default
    @Column(nullable = false)
    private String responseMode = "onReceived";

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JsonObjectConverter.class)
    private Object webhookOptions;
}
