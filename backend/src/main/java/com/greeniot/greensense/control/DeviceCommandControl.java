package com.greeniot.greensense.control;

import com.greeniot.greensense.common.config.GreenSenseProperties;
import com.greeniot.greensense.entity.DeviceCommand;
import com.greeniot.greensense.entity.enums.CommandStatus;
import com.greeniot.greensense.repository.DeviceCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * CONTROL — closes the loop on outbound commands.
 *
 * <p>A command that was published but never acked leaves the UI showing a state the
 * hardware may not be in. Marking it TIMEOUT makes that visible instead of silent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandControl {

    private final DeviceCommandRepository commandRepository;
    private final GreenSenseProperties properties;

    @Transactional(readOnly = true)
    public List<DeviceCommand> recent(String gardenId) {
        return commandRepository.findTop20ByGardenIdOrderByIssuedAtDesc(gardenId);
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void timeoutSweep() {
        Instant cutoff = Instant.now()
                .minus(Duration.ofSeconds(properties.getAutomation().getCommandTimeoutSeconds()));

        List<DeviceCommand> stale = commandRepository.findByStatusAndIssuedAtBefore(CommandStatus.SENT, cutoff);
        if (stale.isEmpty()) {
            return;
        }

        stale.forEach(command -> {
            command.setStatus(CommandStatus.TIMEOUT);
            command.setErrorMessage("No acknowledgement from device");
        });
        commandRepository.saveAll(stale);
        log.warn("{} device command(s) timed out without an ack", stale.size());
    }
}
