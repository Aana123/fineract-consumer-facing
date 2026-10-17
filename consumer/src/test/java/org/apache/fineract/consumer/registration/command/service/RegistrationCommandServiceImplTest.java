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

package org.apache.fineract.consumer.registration.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.apache.fineract.consumer.identity.query.data.IdentityVerificationQueryData;
import org.apache.fineract.consumer.identity.query.service.IdentityQueryService;
import org.apache.fineract.consumer.infrastructure.kyc.service.IdentityVerificationAttemptCounter;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpConstants;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.apache.fineract.consumer.infrastructure.otp.data.PendingOtp;
import org.apache.fineract.consumer.infrastructure.otp.service.OtpService;
import org.apache.fineract.consumer.registration.command.data.SendOtpCommand;
import org.apache.fineract.consumer.registration.command.data.SubmitRegistrationCommand;
import org.apache.fineract.consumer.registration.command.data.VerifyOtpCommand;
import org.apache.fineract.consumer.registration.command.exception.IdentityNotVerifiedException;
import org.apache.fineract.consumer.registration.command.exception.RegistrationNotPendingException;
import org.apache.fineract.consumer.user.command.data.UserCreatedCommandData;
import org.apache.fineract.consumer.user.command.exception.UserAlreadyExistsException;
import org.apache.fineract.consumer.user.command.service.UserCommandService;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.data.UserStatus;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The registration flow is reachable without any credential, so these tests are mostly about what
 * it refuses to tell an anonymous caller and how many times it will let them ask.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationCommandServiceImplTest {

    private static final Long CLIENT_ID = 4711L;
    private static final Long USER_ID = 17L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String EMAIL = "applicant@example.com";
    private static final String PASSWORD = "Cucumber-password1";
    private static final String DOCUMENT_TYPE = "Passport";
    private static final String DOCUMENT_KEY = "P1234567";
    private static final String OTP = "AB12CD";

    @Mock
    private IdentityQueryService identityQueryService;

    @Mock
    private IdentityVerificationAttemptCounter identityVerificationAttempts;

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private OtpService otpService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegistrationCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegistrationCommandServiceImpl(identityQueryService, identityVerificationAttempts,
                userCommandService, userQueryService, otpService, passwordEncoder);
    }

    @Nested
    class Submit {

        @Test
        void acceptsAMatchingIdentifierAndClearsTheAttemptCount() {
            when(identityQueryService.verifyIdentity(any())).thenReturn(IdentityVerificationQueryData.verified("1234"));
            when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
            when(userCommandService.create(any())).thenReturn(UserCreatedCommandData.builder()
                    .userId(USER_ID)
                    .publicId(PUBLIC_ID)
                    .build());

            assertThat(service.submit(submitCommand(DOCUMENT_KEY)).getRegistrationId()).isEqualTo(PUBLIC_ID);
            verify(identityVerificationAttempts).clearAttempts(CLIENT_ID);
            verify(identityVerificationAttempts, never()).recordFailedAttempt(CLIENT_ID);
        }

        @Test
        void refusesAnAlreadyRegisteredClientBeforeLookingAtTheIdentifier() {
            // The order is the fix: checking the identifier first made 403-versus-409 an answer to
            // "is this the right government ID for client N", on a public endpoint.
            doThrowAlreadyExists();

            assertThatThrownBy(() -> service.submit(submitCommand(DOCUMENT_KEY)))
                    .isInstanceOf(UserAlreadyExistsException.class);
            verify(identityQueryService, never()).verifyIdentity(any());
            verify(identityVerificationAttempts, never()).isLockedOut(any());
        }

        @Test
        void refusesAnAlreadyRegisteredClientTheSameWayForAWrongIdentifier() {
            doThrowAlreadyExists();

            assertThatThrownBy(() -> service.submit(submitCommand("WRONG")))
                    .isInstanceOf(UserAlreadyExistsException.class);
            verify(identityQueryService, never()).verifyIdentity(any());
        }

        @Test
        void countsAWrongIdentifierAgainstTheClient() {
            when(identityQueryService.verifyIdentity(any())).thenReturn(IdentityVerificationQueryData.denied());

            assertThatThrownBy(() -> service.submit(submitCommand("WRONG")))
                    .isInstanceOf(IdentityNotVerifiedException.class);
            verify(identityVerificationAttempts).recordFailedAttempt(CLIENT_ID);
            verify(userCommandService, never()).create(any());
        }

        @Test
        void refusesALockedOutClientEvenWhenTheIdentifierIsRight() {
            when(identityVerificationAttempts.isLockedOut(CLIENT_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.submit(submitCommand(DOCUMENT_KEY)))
                    .isInstanceOf(IdentityNotVerifiedException.class);
            // Never asked: a lockout that still consulted Fineract would leak through timing, and a
            // distinct error would tell the caller they had found a client worth attacking.
            verify(identityQueryService, never()).verifyIdentity(any());
        }

        @Test
        void checksRegistrabilityThenLockoutThenTheIdentifier() {
            when(identityQueryService.verifyIdentity(any())).thenReturn(IdentityVerificationQueryData.denied());

            assertThatThrownBy(() -> service.submit(submitCommand("WRONG")))
                    .isInstanceOf(IdentityNotVerifiedException.class);

            InOrder order = inOrder(userCommandService, identityVerificationAttempts, identityQueryService);
            order.verify(userCommandService).requireRegistrable(CLIENT_ID, EMAIL);
            order.verify(identityVerificationAttempts).isLockedOut(CLIENT_ID);
            order.verify(identityQueryService).verifyIdentity(any());
        }

        private void doThrowAlreadyExists() {
            org.mockito.Mockito.doThrow(new UserAlreadyExistsException())
                    .when(userCommandService).requireRegistrable(CLIENT_ID, EMAIL);
        }
    }

    @Nested
    class SendOtp {

        @Test
        void sendsARegistrationPurposeCodeToAPendingApplicant() {
            when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user(UserStatus.PENDING_OTP));
            when(otpService.createOtp(eq(PUBLIC_ID), eq(OtpPurpose.REGISTRATION), any()))
                    .thenReturn(PendingOtp.create(OTP, OtpConstants.OTP_TTL_SECONDS, null));

            assertThat(service.sendOtp(sendOtpCommand()).getSentTo()).contains("***");
        }

        @Test
        void refusesToMintACodeForAnAccountThatHasFinishedRegistering() {
            when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user(UserStatus.BOUND));

            assertThatThrownBy(() -> service.sendOtp(sendOtpCommand()))
                    .isInstanceOf(RegistrationNotPendingException.class);
            verify(otpService, never()).createOtp(any(), any(), any());
        }
    }

    @Nested
    class VerifyOtp {

        @Test
        void bindsAPendingApplicantOnACorrectCode() {
            when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user(UserStatus.PENDING_OTP));

            assertThat(service.verifyOtp(verifyOtpCommand()).getStatus()).isNotNull();
            verify(otpService).validateOtp(PUBLIC_ID, OtpPurpose.REGISTRATION, OTP);
            verify(userCommandService).markOtpVerified(USER_ID);
        }

        @Test
        void refusesAnAccountThatHasFinishedRegistering() {
            when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user(UserStatus.BOUND));

            assertThatThrownBy(() -> service.verifyOtp(verifyOtpCommand()))
                    .isInstanceOf(RegistrationNotPendingException.class);
            verify(otpService, never()).validateOtp(any(), any(), any());
            verify(userCommandService, never()).markOtpVerified(any());
        }
    }

    private static SubmitRegistrationCommand submitCommand(String documentKey) {
        return SubmitRegistrationCommand.builder()
                .fineractClientId(CLIENT_ID)
                .email(EMAIL)
                .password(PASSWORD)
                .documentTypeName(DOCUMENT_TYPE)
                .documentKey(documentKey)
                .build();
    }

    private static SendOtpCommand sendOtpCommand() {
        return SendOtpCommand.builder()
                .registrationId(PUBLIC_ID)
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .build();
    }

    private static VerifyOtpCommand verifyOtpCommand() {
        return VerifyOtpCommand.builder()
                .registrationId(PUBLIC_ID)
                .token(OTP)
                .build();
    }

    private static UserQueryData user(UserStatus status) {
        return UserQueryData.builder()
                .id(USER_ID)
                .publicId(PUBLIC_ID)
                .fineractClientId(CLIENT_ID)
                .email(EMAIL)
                .status(status)
                .build();
    }
}
