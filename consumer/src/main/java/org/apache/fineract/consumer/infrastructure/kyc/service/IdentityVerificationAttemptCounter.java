/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.infrastructure.kyc.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Counts failed identity-verification attempts per Fineract client, so the binding check cannot be
 * ground down.
 *
 * <p>Registration binds a consumer to an existing Fineract client by comparing a government ID
 * (SSN, Aadhaar) the caller supplies against the identifiers Fineract holds. The endpoint is
 * necessarily public — there is no session yet — and the per-user rate limiter keys on an
 * authenticated subject, so it passes anonymous traffic straight through. Without a counter here,
 * the only cost of a wrong guess is one request, which turns a knowledge-based check into an
 * offline-speed oracle against a sequential client id.
 *
 * <p>Five failures inside the window and the client is refused for the rest of it, whether or not
 * the next guess is correct. The caller cannot tell the lockout from a wrong ID, which is the point:
 * a distinguishable "too many attempts" answer would itself confirm that the attacker had found a
 * client worth attacking.
 *
 * <p>A Valkey failure allows the attempt, matching the fail-open choice in
 * {@link org.apache.fineract.consumer.infrastructure.access.service.RateLimitCounter}: the flow cannot complete without Valkey anyway, because the OTP that
 * follows is stored there, so failing closed here would only trade one refusal for another.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityVerificationAttemptCounter {

    /** Public because the Cucumber reset hook and the unit tests key off the production values. */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    public static final String KEY_PREFIX = "identity:attempts:";
    private static final String CLIENT_ID_NULL = "fineractClientId must not be null";

    private final StringRedisTemplate redisTemplate;

    public boolean isLockedOut(Long fineractClientId) {
        Assert.notNull(fineractClientId, CLIENT_ID_NULL);
        try {
            String recorded = redisTemplate.opsForValue().get(key(fineractClientId));
            return recorded != null && Long.parseLong(recorded) >= MAX_FAILED_ATTEMPTS;
        } catch (RuntimeException e) {
            // Includes NumberFormatException: a key holding something other than a count is treated
            // as no count rather than as a lockout.
            log.error("Identity verification attempt lookup failed; allowing the attempt", e);
            return false;
        }
    }

    public void recordFailedAttempt(Long fineractClientId) {
        Assert.notNull(fineractClientId, CLIENT_ID_NULL);
        String key = key(fineractClientId);
        try {
            Long attempts = redisTemplate.opsForValue().increment(key);
            // The window starts at the first failure and is not extended by later ones, so a
            // locked-out client is always released after WINDOW rather than being held by an
            // attacker who keeps knocking.
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
        } catch (RuntimeException e) {
            log.error("Identity verification attempt could not be recorded", e);
        }
    }

    public void clearAttempts(Long fineractClientId) {
        Assert.notNull(fineractClientId, CLIENT_ID_NULL);
        try {
            redisTemplate.delete(key(fineractClientId));
        } catch (RuntimeException e) {
            log.error("Identity verification attempts could not be cleared", e);
        }
    }

    private static String key(Long fineractClientId) {
        return KEY_PREFIX + fineractClientId;
    }
}
