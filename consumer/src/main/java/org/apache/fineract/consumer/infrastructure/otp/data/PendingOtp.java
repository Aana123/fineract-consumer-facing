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

package org.apache.fineract.consumer.infrastructure.otp.data;

import java.time.ZonedDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * A freshly minted code, on its way to delivery.
 *
 * <p>{@code @ToString} includes nothing by default: this is the one place the code exists in
 * plaintext, and the metadata carries the destination address, so a stray log line anywhere on the
 * mint path would print a live credential and a recipient. AGENTS.md requires the explicit-only
 * form for any data class holding a secret.
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
@ToString(onlyExplicitlyIncluded = true)
public final class PendingOtp {

    private final String token;
    private final OtpMetadata metadata;

    public static PendingOtp create(String token, int tokenLiveTimeInSec, OtpDestination destination) {
        OtpMetadata metadata = OtpMetadata.builder()
                .requestTime(ZonedDateTime.now())
                .tokenLiveTimeInSec(tokenLiveTimeInSec)
                .destination(destination)
                .build();
        return new PendingOtp(token, metadata);
    }
}
