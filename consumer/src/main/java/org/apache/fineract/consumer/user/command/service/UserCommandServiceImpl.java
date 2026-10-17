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

package org.apache.fineract.consumer.user.command.service;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.consumer.authentication.command.data.EstablishedSessionCommandData;
import org.apache.fineract.consumer.authentication.command.service.AuthenticationCommandService;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.audit.data.TransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.NonTransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.correlation.service.CorrelationIdHolder;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.data.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.web.service.EmailMaskingService;
import org.apache.fineract.consumer.infrastructure.stepup.service.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpConstants;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpDestination;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.apache.fineract.consumer.infrastructure.otp.service.OtpService;
import org.apache.fineract.consumer.user.command.data.ConfirmPasswordChangeCommand;
import org.apache.fineract.consumer.user.command.data.CreateUserCommand;
import org.apache.fineract.consumer.user.command.data.ForgotPasswordCommand;
import org.apache.fineract.consumer.user.command.data.InitiatePasswordChangeCommand;
import org.apache.fineract.consumer.user.command.data.ResetPasswordCommand;
import org.apache.fineract.consumer.user.command.data.UserCommandConstants;
import org.apache.fineract.consumer.user.command.data.UserCreatedCommandData;
import org.apache.fineract.consumer.user.command.data.UserPasswordChangeChallengeCommandData;
import org.apache.fineract.consumer.user.command.domain.User;
import org.apache.fineract.consumer.user.command.exception.UserAlreadyExistsException;
import org.apache.fineract.consumer.user.command.exception.UserCommandNotFoundException;
import org.apache.fineract.consumer.user.command.exception.UserPasswordInvalidException;
import org.apache.fineract.consumer.user.command.exception.UserPasswordResetInvalidException;
import org.apache.fineract.consumer.user.command.exception.UserStepUpInvalidException;
import org.apache.fineract.consumer.user.command.repository.UserCommandRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandServiceImpl implements UserCommandService {

    private static final String DETAIL_FLOW = "flow";
    private static final String FLOW_CHANGE = "change";
    private static final String FLOW_RESET = "reset";

    private final UserCommandRepository repository;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final StepUpTokenService stepUpTokenService;
    private final AuthenticationCommandService authenticationCommandService;
    private final AsyncTaskExecutor applicationTaskExecutor;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void requireRegistrable(Long fineractClientId, String email) {
        repository.findByEmail(email).ifPresent(u -> {
            throw new UserAlreadyExistsException();
        });
        repository.findByFineractClientId(fineractClientId).ifPresent(u -> {
            throw new UserAlreadyExistsException();
        });
    }

    @Override
    public UserCreatedCommandData create(CreateUserCommand command) {
        requireRegistrable(command.getFineractClientId(), command.getEmail());
        User user = User.createPendingOtp(
                UUID.randomUUID(),
                command.getEmail(),
                command.getPasswordHash(),
                command.getFineractClientId());
        User saved;
        try {
            saved = repository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException();
        }
        return UserCreatedCommandData.builder()
                .userId(saved.getId())
                .publicId(saved.getPublicId())
                .build();
    }

    @Override
    public void markOtpVerified(Long userId) {
        User user = repository.findById(userId).orElseThrow(UserCommandNotFoundException::new);
        user.markOtpVerified();
        repository.save(user);
    }

    @Override
    public UserPasswordChangeChallengeCommandData initiatePasswordChange(
            Jwt jwt, InitiatePasswordChangeCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.USER_PASSWORD_CHANGE);
        User user = repository.findByPublicId(publicId(jwt)).orElseThrow(UserCommandNotFoundException::new);
        if (!passwordEncoder.matches(command.getCurrentPassword(), user.getPasswordHash())) {
            log.warn("password change initiate rejected: current password mismatch for user {}", user.getPublicId());
            throw new UserPasswordInvalidException();
        }

        sendOtp(user, OtpPurpose.PASSWORD_CHANGE);
        IssuedJwt issued = stepUpTokenService.issue(user.getPublicId(), command.getDeviceFingerprint(),
                passwordChangeActionFingerprint(user.getPublicId()), StepUpConstants.STEPUP_TTL);
        eventPublisher.publishEvent(NonTransactionalAuditEvent.of(AuditEventType.PASSWORD_CHANGE_INITIATED,
                user.getId(), false, command.getDeviceFingerprint(), Map.of(DETAIL_FLOW, FLOW_CHANGE)));

        return UserPasswordChangeChallengeCommandData.builder()
                .stepUpToken(issued.getTokenValue())
                .expiresAt(issued.getExpiresAt())
                .sentTo(EmailMaskingService.mask(user.getEmail()))
                .build();
    }

    @Override
    @Transactional
    public EstablishedSessionCommandData confirmPasswordChange(Jwt jwt, ConfirmPasswordChangeCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.USER_PASSWORD_CHANGE);
        UUID publicId = publicId(jwt);
        if (!stepUpTokenService.verify(command.getStepUpToken(), publicId, command.getDeviceFingerprint(),
                passwordChangeActionFingerprint(publicId))) {
            throw new UserStepUpInvalidException();
        }
        otpService.validateOtp(publicId, OtpPurpose.PASSWORD_CHANGE, command.getOtp(), UserStepUpInvalidException::new);

        User user = repository.findByPublicId(publicId).orElseThrow(UserCommandNotFoundException::new);
        user.changePassword(passwordEncoder.encode(command.getNewPassword()));
        repository.save(user);
        eventPublisher.publishEvent(TransactionalAuditEvent.of(AuditEventType.PASSWORD_CHANGE_CONFIRMED,
                user.getId(), false, command.getDeviceFingerprint(), Map.of(DETAIL_FLOW, FLOW_CHANGE)));
        return authenticationCommandService.revokeAllSessionsAndReissue(
                user.getId(), user.getPublicId(), command.getDeviceFingerprint());
    }

    @Override
    public void forgotPassword(ForgotPasswordCommand command) {
        log.info("password.forgot.requested");
        String correlationId = CorrelationIdHolder.getCurrentCorrelationId();
        repository.findByEmail(command.getEmail()).ifPresent(user -> applicationTaskExecutor.execute(() -> {
            try {
                sendOtp(user, OtpPurpose.PASSWORD_RESET);
                eventPublisher.publishEvent(NonTransactionalAuditEvent.of(AuditEventType.PASSWORD_CHANGE_INITIATED,
                        user.getId(), false, null, Map.of(DETAIL_FLOW, FLOW_RESET), correlationId));
            } catch (RuntimeException e) {
                log.error("password.forgot otp delivery failed for user {}", user.getPublicId(), e);
            }
        }));
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        User user = repository.findByEmail(command.getEmail()).orElseThrow(() -> {
            log.warn("password.reset.failed: unknown email");
            return new UserPasswordResetInvalidException();
        });
        otpService.validateOtp(user.getPublicId(), OtpPurpose.PASSWORD_RESET, command.getOtp(), () -> {
            log.warn("password.reset.failed: invalid otp for user {}", user.getPublicId());
            return new UserPasswordResetInvalidException();
        });
        user.changePassword(passwordEncoder.encode(command.getNewPassword()));
        repository.save(user);
        authenticationCommandService.revokeAllSessions(user.getId(), user.getPublicId());
        eventPublisher.publishEvent(TransactionalAuditEvent.of(AuditEventType.PASSWORD_CHANGE_CONFIRMED,
                user.getId(), false, null, Map.of(DETAIL_FLOW, FLOW_RESET)));
        log.info("password.reset.completed for user {}", user.getPublicId());
    }

    private void sendOtp(User user, OtpPurpose purpose) {
        otpService.createOtp(user.getPublicId(), purpose, OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(user.getEmail())
                .build());
    }

    private String passwordChangeActionFingerprint(UUID publicId) {
        return stepUpTokenService.actionFingerprint(
                UserCommandConstants.PASSWORD_CHANGE_ENDPOINT, publicId.toString());
    }

    private static UUID publicId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
