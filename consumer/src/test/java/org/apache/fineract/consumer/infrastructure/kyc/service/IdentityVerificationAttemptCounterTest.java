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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class IdentityVerificationAttemptCounterTest {

    private static final Long CLIENT_ID = 4711L;
    private static final String EXPECTED_KEY = IdentityVerificationAttemptCounter.KEY_PREFIX + CLIENT_ID;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdentityVerificationAttemptCounter repository;

    @BeforeEach
    void setUp() {
        repository = new IdentityVerificationAttemptCounter(redisTemplate);
    }

    @Test
    void noRecordedAttemptsIsNotLockedOut() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);

        assertThat(repository.isLockedOut(CLIENT_ID)).isFalse();
    }

    @Test
    void oneAttemptShortOfTheCapIsNotLockedOut() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY))
                .thenReturn(String.valueOf(IdentityVerificationAttemptCounter.MAX_FAILED_ATTEMPTS - 1));

        assertThat(repository.isLockedOut(CLIENT_ID)).isFalse();
    }

    @Test
    void theCapLocksTheClientOut() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY))
                .thenReturn(String.valueOf(IdentityVerificationAttemptCounter.MAX_FAILED_ATTEMPTS));

        assertThat(repository.isLockedOut(CLIENT_ID)).isTrue();
    }

    @Test
    void theFirstFailureStartsTheWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn(1L);

        repository.recordFailedAttempt(CLIENT_ID);

        verify(redisTemplate).expire(EXPECTED_KEY, IdentityVerificationAttemptCounter.WINDOW);
    }

    @Test
    void laterFailuresDoNotExtendTheWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn(2L);

        repository.recordFailedAttempt(CLIENT_ID);

        // Otherwise an attacker who keeps knocking holds the real owner out indefinitely.
        verify(redisTemplate, never()).expire(EXPECTED_KEY, IdentityVerificationAttemptCounter.WINDOW);
    }

    @Test
    void aSuccessfulVerificationClearsTheCount() {
        repository.clearAttempts(CLIENT_ID);

        verify(redisTemplate).delete(EXPECTED_KEY);
    }

    @Test
    void aValkeyFailureAllowsTheAttemptRatherThanRefusingEveryone() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("valkey is down"));

        assertThat(repository.isLockedOut(CLIENT_ID)).isFalse();
    }

    @Test
    void aCorruptCounterValueIsTreatedAsNoCount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn("not-a-number");

        assertThat(repository.isLockedOut(CLIENT_ID)).isFalse();
    }
}
