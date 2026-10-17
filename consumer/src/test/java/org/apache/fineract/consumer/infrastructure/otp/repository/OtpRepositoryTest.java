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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpConstants;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class OtpRepositoryTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final OtpPurpose PURPOSE = OtpPurpose.TRANSFER;
    private static final String EXPECTED_KEY = "otp:pending:" + PURPOSE.getKeySegment() + ":" + PUBLIC_ID;
    private static final String EXPECTED_ATTEMPTS_KEY = "otp:attempts:" + PURPOSE.getKeySegment() + ":" + PUBLIC_ID;
    private static final String TOKEN_HASH = "a1b2c3d4e5f6";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OtpRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OtpRepository(redisTemplate);
    }

    @Test
    void saveWritesNamespacedKeyWithOtpTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository.savePendingOtp(PUBLIC_ID, PURPOSE, TOKEN_HASH);

        verify(valueOperations).set(EXPECTED_KEY, TOKEN_HASH, Duration.ofSeconds(OtpConstants.OTP_TTL_SECONDS));
    }

    @Test
    void saveResetsFailedAttemptCounter() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository.savePendingOtp(PUBLIC_ID, PURPOSE, TOKEN_HASH);

        verify(redisTemplate).delete(EXPECTED_ATTEMPTS_KEY);
    }

    @Test
    void recordFailedValidationAttemptIncrementsCounter() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(EXPECTED_ATTEMPTS_KEY)).thenReturn(2L);

        assertThat(repository.recordFailedValidationAttempt(PUBLIC_ID, PURPOSE)).isEqualTo(2L);
        verify(redisTemplate, never()).expire(EXPECTED_ATTEMPTS_KEY, Duration.ofSeconds(OtpConstants.OTP_TTL_SECONDS));
    }

    @Test
    void recordFailedValidationAttemptSetsTtlOnFirstFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(EXPECTED_ATTEMPTS_KEY)).thenReturn(1L);

        assertThat(repository.recordFailedValidationAttempt(PUBLIC_ID, PURPOSE)).isEqualTo(1L);
        verify(redisTemplate).expire(EXPECTED_ATTEMPTS_KEY, Duration.ofSeconds(OtpConstants.OTP_TTL_SECONDS));
    }

    @Test
    void getReturnsStoredHash() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(TOKEN_HASH);

        assertThat(repository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).isEqualTo(TOKEN_HASH);
    }

    @Test
    void getReturnsNullWhenKeyMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);

        assertThat(repository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).isNull();
    }

    @Test
    void eachPurposeGetsItsOwnSlot() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository.savePendingOtp(PUBLIC_ID, OtpPurpose.PASSWORD_RESET, TOKEN_HASH);

        // The same user, a different ceremony: a code minted for one must not be readable as the
        // code for the other, which is what a shared key would make it.
        String resetKey = "otp:pending:" + OtpPurpose.PASSWORD_RESET.getKeySegment() + ":" + PUBLIC_ID;
        assertThat(resetKey).isNotEqualTo(EXPECTED_KEY);
        verify(valueOperations).set(resetKey, TOKEN_HASH, Duration.ofSeconds(OtpConstants.OTP_TTL_SECONDS));
        verify(valueOperations, never()).set(EXPECTED_KEY, TOKEN_HASH,
                Duration.ofSeconds(OtpConstants.OTP_TTL_SECONDS));
    }

    @Test
    void purposeKeySegmentsAreUniqueAndKeySafe() {
        // A duplicated or ':'-bearing segment would merge or straddle two ceremonies' slots.
        assertThat(java.util.Arrays.stream(OtpPurpose.values()).map(OtpPurpose::getKeySegment).distinct().count())
                .isEqualTo(OtpPurpose.values().length);
        assertThat(OtpPurpose.values()).allSatisfy(purpose ->
                assertThat(purpose.getKeySegment()).isNotBlank().doesNotContain(":"));
    }

    @Test
    void deleteRemovesPendingOtpAndAttemptCounter() {
        repository.deletePendingOtp(PUBLIC_ID, PURPOSE);

        verify(redisTemplate).delete(List.of(EXPECTED_KEY, EXPECTED_ATTEMPTS_KEY));
    }
}
