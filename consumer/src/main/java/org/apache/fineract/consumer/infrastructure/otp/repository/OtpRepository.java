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

package org.apache.fineract.consumer.infrastructure.otp.repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpConstants;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

/**
 * The pending-code slots in Valkey.
 *
 * <p>Both keys are namespaced by {@link OtpPurpose}: one slot per user per purpose, so a code for
 * one flow neither satisfies nor destroys the code for another.
 */
@Repository
@RequiredArgsConstructor
public class OtpRepository {

    private static final String KEY_PREFIX = "otp:pending:";
    private static final String ATTEMPTS_KEY_PREFIX = "otp:attempts:";
    private static final String PUBLIC_ID_NULL = "publicId must not be null";
    private static final String TOKEN_HASH_NULL = "tokenHash must not be null";
    private static final String PURPOSE_NULL = "purpose must not be null";
    private static final long FIRST_ATTEMPT = 1L;

    private final StringRedisTemplate redisTemplate;

    public void savePendingOtp(UUID publicId, OtpPurpose purpose, String tokenHash) {
        Assert.notNull(publicId, PUBLIC_ID_NULL);
        Assert.notNull(purpose, PURPOSE_NULL);
        Assert.notNull(tokenHash, TOKEN_HASH_NULL);
        redisTemplate.delete(attemptsKey(publicId, purpose));
        redisTemplate.opsForValue().set(key(publicId, purpose), tokenHash,
                Duration.ofSeconds(OtpConstants.OTP_TTL_SECONDS));
    }

    public String getPendingTokenHash(UUID publicId, OtpPurpose purpose) {
        Assert.notNull(publicId, PUBLIC_ID_NULL);
        Assert.notNull(purpose, PURPOSE_NULL);
        return redisTemplate.opsForValue().get(key(publicId, purpose));
    }

    public long recordFailedValidationAttempt(UUID publicId, OtpPurpose purpose) {
        Assert.notNull(publicId, PUBLIC_ID_NULL);
        Assert.notNull(purpose, PURPOSE_NULL);
        String attemptsKey = attemptsKey(publicId, purpose);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        long count = attempts == null ? 0L : attempts;
        if (count == FIRST_ATTEMPT) {
            redisTemplate.expire(attemptsKey, Duration.ofSeconds(OtpConstants.OTP_TTL_SECONDS));
        }
        return count;
    }

    public void deletePendingOtp(UUID publicId, OtpPurpose purpose) {
        Assert.notNull(publicId, PUBLIC_ID_NULL);
        Assert.notNull(purpose, PURPOSE_NULL);
        redisTemplate.delete(List.of(key(publicId, purpose), attemptsKey(publicId, purpose)));
    }

    private static String key(UUID publicId, OtpPurpose purpose) {
        return KEY_PREFIX + purpose.getKeySegment() + ':' + publicId;
    }

    private static String attemptsKey(UUID publicId, OtpPurpose purpose) {
        return ATTEMPTS_KEY_PREFIX + purpose.getKeySegment() + ':' + publicId;
    }
}
