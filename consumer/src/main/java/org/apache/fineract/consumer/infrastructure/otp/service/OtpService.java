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

import java.util.UUID;
import java.util.function.Supplier;
import org.apache.fineract.consumer.infrastructure.exception.AbstractConsumerException;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpDestination;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.apache.fineract.consumer.infrastructure.otp.data.PendingOtp;

/**
 * Mints and redeems one-time codes.
 *
 * <p>Every method takes the {@link OtpPurpose} the code is for. A code minted for one purpose does
 * not validate for another, so the holder of a code obtained from a low-stakes challenge cannot
 * spend it on a high-stakes one.
 */
public interface OtpService {

    PendingOtp createOtp(UUID publicId, OtpPurpose purpose, OtpDestination destination);

    void validateOtp(UUID publicId, OtpPurpose purpose, String token);

    void validateOtp(UUID publicId, OtpPurpose purpose, String token,
            Supplier<? extends AbstractConsumerException> onInvalid);
}
