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

import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.kyc.service.IdentityVerificationAttemptCounter;
import org.apache.fineract.consumer.infrastructure.web.service.EmailMaskingService;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpDestination;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.apache.fineract.consumer.infrastructure.otp.data.PendingOtp;
import org.apache.fineract.consumer.infrastructure.otp.service.OtpService;
import org.apache.fineract.consumer.registration.command.data.SendOtpCommand;
import org.apache.fineract.consumer.registration.command.data.SendOtpCommandData;
import org.apache.fineract.consumer.registration.command.data.SubmitRegistrationCommand;
import org.apache.fineract.consumer.registration.command.data.SubmitRegistrationCommandData;
import org.apache.fineract.consumer.registration.command.data.VerifyOtpCommand;
import org.apache.fineract.consumer.registration.command.data.VerifyOtpCommandData;
import org.apache.fineract.consumer.registration.command.exception.IdentityNotVerifiedException;
import org.apache.fineract.consumer.registration.command.exception.RegistrationNotPendingException;
import org.apache.fineract.consumer.identity.query.data.IdentityVerificationQueryData;
import org.apache.fineract.consumer.identity.query.data.IdentityVerificationQuery;
import org.apache.fineract.consumer.identity.query.service.IdentityQueryService;
import org.apache.fineract.consumer.user.command.data.CreateUserCommand;
import org.apache.fineract.consumer.user.command.data.UserCreatedCommandData;
import org.apache.fineract.consumer.user.command.data.UserStatus;
import org.apache.fineract.consumer.user.command.service.UserCommandService;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationCommandServiceImpl implements RegistrationCommandService {

    private final IdentityQueryService identityQueryService;
    private final IdentityVerificationAttemptCounter identityVerificationAttempts;
    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Binds a new consumer to the Fineract client whose government ID they can produce.
     *
     * <p>The endpoint is public, so the order of the three checks below is load-bearing. Taking the
     * already-registered answer first means a client that is spoken for is refused identically
     * whether or not the ID supplied was correct — previously a correct ID returned 409 and a wrong
     * one 403, which confirmed the ID without the attacker ever completing a registration. The
     * attempt counter then bounds how many guesses a single client is worth, and a locked-out
     * client is refused with the same error a wrong ID gets.
     */
    @Override
    @Transactional
    public SubmitRegistrationCommandData submit(SubmitRegistrationCommand command) {
        userCommandService.requireRegistrable(command.getFineractClientId(), command.getEmail());

        if (identityVerificationAttempts.isLockedOut(command.getFineractClientId())) {
            throw new IdentityNotVerifiedException();
        }

        IdentityVerificationQuery verification = IdentityVerificationQuery.builder()
                .fineractClientId(command.getFineractClientId())
                .documentTypeName(command.getDocumentTypeName())
                .documentKey(command.getDocumentKey())
                .build();

        IdentityVerificationQueryData verificationResult = identityQueryService.verifyIdentity(verification);
        if (!verificationResult.isVerified()) {
            identityVerificationAttempts.recordFailedAttempt(command.getFineractClientId());
            throw new IdentityNotVerifiedException();
        }
        identityVerificationAttempts.clearAttempts(command.getFineractClientId());

        CreateUserCommand createUser = CreateUserCommand.builder()
                .email(command.getEmail())
                .passwordHash(passwordEncoder.encode(command.getPassword()))
                .fineractClientId(command.getFineractClientId())
                .build();
        UserCreatedCommandData createdUser = userCommandService.create(createUser);

        return SubmitRegistrationCommandData.builder()
                .registrationId(createdUser.getPublicId())
                .status(UserStatus.PENDING_OTP)
                .maskedLastFour(verificationResult.getMaskedLastFour())
                .build();
    }

    /**
     * Sends the registration code.
     *
     * <p>Public, like the rest of the flow, so the status guard is what stops it being a
     * code-on-demand service for accounts that finished registering long ago: any identifier
     * presented here used to get a fresh code emailed to the account's own address.
     */
    @Override
    public SendOtpCommandData sendOtp(SendOtpCommand command) {
        UserQueryData user = requirePendingOtp(command.getRegistrationId());
        OtpDestination destination = OtpDestination.builder()
                .deliveryMethod(command.getDeliveryMethod())
                .target(user.getEmail())
                .build();
        PendingOtp request = otpService.createOtp(user.getPublicId(), OtpPurpose.REGISTRATION, destination);
        ZonedDateTime expiresAt = request.getMetadata().getRequestTime()
                .plusSeconds(request.getMetadata().getTokenLiveTimeInSec());
        return SendOtpCommandData.builder()
                .sentTo(EmailMaskingService.mask(user.getEmail()))
                .expiresAt(expiresAt)
                .tokenLiveTimeInSec(request.getMetadata().getTokenLiveTimeInSec())
                .build();
    }

    @Override
    @Transactional
    public VerifyOtpCommandData verifyOtp(VerifyOtpCommand command) {
        UserQueryData user = requirePendingOtp(command.getRegistrationId());
        otpService.validateOtp(user.getPublicId(), OtpPurpose.REGISTRATION, command.getToken());
        userCommandService.markOtpVerified(user.getId());
        return VerifyOtpCommandData.builder()
                .status(UserStatus.BOUND)
                .build();
    }

    private UserQueryData requirePendingOtp(UUID registrationId) {
        UserQueryData user = userQueryService.findByPublicId(registrationId);
        // The query side keeps its own copy of the status enum — CQRS here forbids sharing types
        // across sides — and the command-side copy is the one imported, for the response below.
        if (user.getStatus() != org.apache.fineract.consumer.user.query.data.UserStatus.PENDING_OTP) {
            throw new RegistrationNotPendingException();
        }
        return user;
    }

}
