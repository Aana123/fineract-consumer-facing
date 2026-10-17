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

package org.apache.fineract.consumer.registration.command.exception;

import org.apache.fineract.consumer.infrastructure.exception.AbstractConsumerException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a registration step is attempted for a user who is past it.
 *
 * <p>The registration endpoints are public, so without this the OTP-send step would mint and email
 * a fresh code for any account whose identifier was presented — including a fully onboarded one,
 * which has no registration left to finish.
 */
public class RegistrationNotPendingException extends AbstractConsumerException {

    public static final String CODE = "error.msg.consumer.registration.not.pending";

    public RegistrationNotPendingException() {
        super(HttpStatus.CONFLICT, CODE, "registration is not awaiting verification");
    }
}
