package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.CommandStatus;
import com.greeniot.greensense.entity.enums.CommandType;
import com.greeniot.greensense.entity.enums.TriggerSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * ENTITY — an outbound instruction to a device, tracked until the node acks it.
 * Without this the UI could show "pump running" while the relay never fired.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "device_commands")
@CompoundIndex(name = "ix_command_status", def = "{'status':1,'issuedAt':1}")
public class DeviceCommand extends BaseDocument {

    @Indexed
    private String gardenId;

    private String actuatorId;

    private String deviceCode;

    private String channel;

    private CommandType command;

    private Integer durationMinutes;

    @Indexed(unique = true)
    private String correlationId;

    @Builder.Default
    private CommandStatus status = CommandStatus.PENDING;

    private TriggerSource issuedBy;

    /** User id when {@code issuedBy == USER}, rule id when {@code RULE}, etc. */
    private String issuedByRef;

    private Instant issuedAt;

    private Instant sentAt;

    private Instant ackedAt;

    @Builder.Default
    private int retryCount = 0;

    private String errorMessage;
}
