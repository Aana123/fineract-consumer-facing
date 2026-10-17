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

package org.apache.fineract.consumer.infrastructure.otp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.fineract.consumer.infrastructure.exception.AbstractConsumerException;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpConstants;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpDestination;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.apache.fineract.consumer.infrastructure.otp.data.PendingOtp;
import org.apache.fineract.consumer.infrastructure.otp.exception.OtpDestinationInvalidException;
import org.apache.fineract.consumer.infrastructure.otp.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.infrastructure.otp.repository.OtpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String EMAIL = "user@example.com";
    private static final OtpPurpose PURPOSE = OtpPurpose.TRANSFER;
    private static final OtpPurpose OTHER_PURPOSE = OtpPurpose.PASSWORD_RESET;
    private static final String TOKEN = "AB12CD";
    private static final String WRONG_TOKEN = "XX99XX";

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private OtpEmailDeliveryService otpEmailDeliveryService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OtpServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OtpServiceImpl(otpRepository, otpEmailDeliveryService, eventPublisher);
    }

    @Test
    void createOtpStoresHashNotRawToken() {
        PendingOtp created = service.createOtp(PUBLIC_ID, PURPOSE, emailDestination());

        ArgumentCaptor<String> storedCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpRepository).savePendingOtp(eq(PUBLIC_ID), eq(PURPOSE), storedCaptor.capture());
        assertThat(storedCaptor.getValue())
                .isEqualTo(sha256Hex(PURPOSE, created.getToken()))
                .isNotEqualTo(created.getToken());
    }

    @Test
    void createOtpDeliversRawTokenByEmail() {
        PendingOtp created = service.createOtp(PUBLIC_ID, PURPOSE, emailDestination());

        verify(otpEmailDeliveryService).deliver(EMAIL, created.getToken(), PURPOSE);
    }

    @Test
    void createOtpRejectsUnknownDeliveryMethod() {
        OtpDestination smsDestination = OtpDestination.builder()
                .deliveryMethod(OtpConstants.SMS_DELIVERY_METHOD_NAME)
                .target("+15551234567")
                .build();

        assertThatThrownBy(() -> service.createOtp(PUBLIC_ID, PURPOSE, smsDestination))
                .isInstanceOf(OtpDestinationInvalidException.class);
    }

    @Test
    void validateOtpAcceptsCaseInsensitiveTokenAndDeletes() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(sha256Hex(PURPOSE, TOKEN));

        service.validateOtp(PUBLIC_ID, PURPOSE, TOKEN.toLowerCase(Locale.ROOT));

        verify(otpRepository).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void validateOtpRejectsWrongToken() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(sha256Hex(PURPOSE, TOKEN));

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, PURPOSE, WRONG_TOKEN))
                .isInstanceOf(OtpTokenInvalidException.class);
        verify(otpRepository, never()).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void validateOtpKeepsOtpAliveBelowAttemptCapAndAcceptsCorrectToken() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(sha256Hex(PURPOSE, TOKEN));
        AtomicLong failedAttempts = new AtomicLong();
        when(otpRepository.recordFailedValidationAttempt(PUBLIC_ID, PURPOSE))
                .thenAnswer(invocation -> failedAttempts.incrementAndGet());

        for (int attempt = 1; attempt < OtpConstants.MAX_OTP_VALIDATION_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, PURPOSE, WRONG_TOKEN))
                    .isInstanceOf(OtpTokenInvalidException.class);
        }
        verify(otpRepository, never()).deletePendingOtp(PUBLIC_ID, PURPOSE);

        service.validateOtp(PUBLIC_ID, PURPOSE, TOKEN);

        verify(otpRepository).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void validateOtpInvalidatesPendingOtpOnFinalFailedAttempt() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(sha256Hex(PURPOSE, TOKEN));
        AtomicLong failedAttempts = new AtomicLong();
        when(otpRepository.recordFailedValidationAttempt(PUBLIC_ID, PURPOSE))
                .thenAnswer(invocation -> failedAttempts.incrementAndGet());

        for (int attempt = 1; attempt <= OtpConstants.MAX_OTP_VALIDATION_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, PURPOSE, WRONG_TOKEN))
                    .isInstanceOf(OtpTokenInvalidException.class);
        }

        assertThat(failedAttempts.get()).isEqualTo(OtpConstants.MAX_OTP_VALIDATION_ATTEMPTS);
        verify(otpRepository).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void validateOtpRejectsMissingOtp() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(null);

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, PURPOSE, TOKEN))
                .isInstanceOf(OtpTokenInvalidException.class);
        verify(otpRepository, never()).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void validateOtpWithSupplierThrowsSuppliedExceptionAndCountsFailedAttempt() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(sha256Hex(PURPOSE, TOKEN));
        when(otpRepository.recordFailedValidationAttempt(PUBLIC_ID, PURPOSE)).thenReturn(1L);

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, PURPOSE, WRONG_TOKEN, StepUpTestException::new))
                .isInstanceOf(StepUpTestException.class);
        verify(otpRepository).recordFailedValidationAttempt(PUBLIC_ID, PURPOSE);
        verify(otpRepository, never()).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void validateOtpWithSupplierInvalidatesPendingOtpAtAttemptCap() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(sha256Hex(PURPOSE, TOKEN));
        when(otpRepository.recordFailedValidationAttempt(PUBLIC_ID, PURPOSE))
                .thenReturn((long) OtpConstants.MAX_OTP_VALIDATION_ATTEMPTS);

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, PURPOSE, WRONG_TOKEN, StepUpTestException::new))
                .isInstanceOf(StepUpTestException.class);
        verify(otpRepository).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void validateOtpWithSupplierAcceptsCorrectTokenAndDeletes() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(sha256Hex(PURPOSE, TOKEN));

        service.validateOtp(PUBLIC_ID, PURPOSE, TOKEN, StepUpTestException::new);

        verify(otpRepository).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void validateOtpWithSupplierRejectsMissingOtpWithoutCountingAttempts() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(null);

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, PURPOSE, TOKEN, StepUpTestException::new))
                .isInstanceOf(StepUpTestException.class);
        verify(otpRepository, never()).recordFailedValidationAttempt(PUBLIC_ID, PURPOSE);
        verify(otpRepository, never()).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void validateOtpRejectsNullToken() {
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, PURPOSE)).thenReturn(sha256Hex(PURPOSE, TOKEN));

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, PURPOSE, null))
                .isInstanceOf(OtpTokenInvalidException.class);
        verify(otpRepository, never()).deletePendingOtp(PUBLIC_ID, PURPOSE);
    }

    @Test
    void aCodeMintedForOnePurposeDoesNotValidateForAnother() {
        // The slot the other ceremony would read, holding the hash this ceremony stored: even with
        // the key confusion undone, the hash itself must not carry over.
        when(otpRepository.getPendingTokenHash(PUBLIC_ID, OTHER_PURPOSE)).thenReturn(sha256Hex(PURPOSE, TOKEN));

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, OTHER_PURPOSE, TOKEN))
                .isInstanceOf(OtpTokenInvalidException.class);
        verify(otpRepository, never()).deletePendingOtp(PUBLIC_ID, OTHER_PURPOSE);
    }

    @Test
    void theSameCodeHashesDifferentlyPerPurpose() {
        assertThat(sha256Hex(PURPOSE, TOKEN)).isNotEqualTo(sha256Hex(OTHER_PURPOSE, TOKEN));
    }

    @Test
    void createOtpNamesThePurposeToTheDeliveryService() {
        service.createOtp(PUBLIC_ID, OTHER_PURPOSE, emailDestination());

        // The recipient is told what the code authorises; a purpose-free email is one a victim can
        // be talked into reading out for something else.
        verify(otpEmailDeliveryService).deliver(eq(EMAIL), org.mockito.ArgumentMatchers.anyString(),
                eq(OTHER_PURPOSE));
    }

    private static final class StepUpTestException extends AbstractConsumerException {

        private StepUpTestException() {
            super(HttpStatus.BAD_REQUEST, "error.msg.consumer.test.step.up", "test step-up failure");
        }
    }

    private static OtpDestination emailDestination() {
        return OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(EMAIL)
                .build();
    }

    private static String sha256Hex(OtpPurpose purpose, String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = purpose.getKeySegment() + ":" + token.toUpperCase(Locale.ROOT);
            byte[] hashed = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
