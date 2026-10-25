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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.identity.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.fineract.consumer.identity.query.data.IdentityVerificationQuery;
import org.apache.fineract.consumer.identity.query.data.IdentityVerificationQueryData;
import org.apache.fineract.consumer.identity.query.exception.IdentityVerificationException;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.ClientIdentifierData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.CodeValueData;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.IdentityFineractClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityQueryServiceImplTest {

    @Mock
    private IdentityFineractClient identifiersClient;

    @InjectMocks
    private IdentityQueryServiceImpl service;

    @Test
    void verifyIdentityReturnsVerifiedWithMaskedLastFourWhenNormalizedKeyMatches() {
        ClientIdentifierData identifier = new ClientIdentifierData()
                .documentType(new CodeValueData().name("Passport"))
                .documentKey("ab-1234 5678");
        when(identifiersClient.retrieveAllClientIdentifiers(10L)).thenReturn(List.of(identifier));

        IdentityVerificationQuery query = IdentityVerificationQuery.builder()
                .fineractClientId(10L)
                .documentTypeName("Passport")
                .documentKey("AB12345678")
                .build();

        IdentityVerificationQueryData result = service.verifyIdentity(query);

        assertThat(result).isEqualTo(IdentityVerificationQueryData.verified("5678"));
    }

    @Test
    void verifyIdentityReturnsDeniedWhenClientNotFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/v1/clients/10/identifiers",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        when(identifiersClient.retrieveAllClientIdentifiers(10L))
                .thenThrow(new FeignException.NotFound("Not found", request, null, Map.of()));

        IdentityVerificationQuery query = IdentityVerificationQuery.builder()
                .fineractClientId(10L)
                .documentTypeName("Passport")
                .documentKey("AB12345678")
                .build();

        assertThat(service.verifyIdentity(query)).isEqualTo(IdentityVerificationQueryData.denied());
    }

    @Test
    void verifyIdentityThrowsIdentityVerificationExceptionOnUpstreamFeignError() {
        Request request = Request.create(Request.HttpMethod.GET, "/v1/clients/10/identifiers",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        when(identifiersClient.retrieveAllClientIdentifiers(10L))
                .thenThrow(new FeignException.InternalServerError("Server error", request, null, Map.of()));

        IdentityVerificationQuery query = IdentityVerificationQuery.builder()
                .fineractClientId(10L)
                .documentTypeName("Passport")
                .documentKey("AB12345678")
                .build();

        assertThatThrownBy(() -> service.verifyIdentity(query))
                .isInstanceOf(IdentityVerificationException.class);
    }
}