package com.greeniot.greensense.entity;

import com.greeniot.greensense.entity.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** ENTITY — an account that owns gardens. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User extends BaseDocument {

    @Indexed(unique = true)
    private String email;

    @Field("password_hash")
    private String passwordHash;

    private String fullName;

    private String phone;

    @Builder.Default
    private UserRole role = UserRole.OWNER;

    @Builder.Default
    private boolean enabled = true;

    /** FCM/APNs tokens for push notifications. */
    @Builder.Default
    private List<String> pushTokens = new ArrayList<>();

    @Builder.Default
    private boolean notifyByPush = true;

    @Builder.Default
    private boolean notifyByEmail = false;

    /** Non-critical alerts are held back inside this window. */
    private LocalTime quietHoursStart;

    private LocalTime quietHoursEnd;
}
