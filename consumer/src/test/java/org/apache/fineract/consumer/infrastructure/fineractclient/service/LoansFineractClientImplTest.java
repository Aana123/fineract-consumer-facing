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

package org.apache.fineract.consumer.infrastructure.fineractclient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutLoansLoanIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutLoansLoanIdResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoansFineractClientImplTest {

    @Mock
    private LoansApi loansApi;

    @InjectMocks
    private LoansFineractClientImpl loansFineractClient;

    @Test
    void calculateOrSubmitLoanApplicationDelegatesToGeneratedApi() {
        PostLoansRequest request = new PostLoansRequest();
        PostLoansResponse expected = new PostLoansResponse();
        when(loansApi.calculateOrSubmitLoanApplication(request, null)).thenReturn(expected);

        PostLoansResponse result = loansFineractClient.calculateOrSubmitLoanApplication(request, null);

        assertThat(result).isSameAs(expected);
        verify(loansApi).calculateOrSubmitLoanApplication(request, null);
    }

    @Test
    void updateLoanApplicationDelegatesToGeneratedApi() {
        PutLoansLoanIdRequest request = new PutLoansLoanIdRequest();
        PutLoansLoanIdResponse expected = new PutLoansLoanIdResponse();
        when(loansApi.updateLoanApplication(7L, request, null)).thenReturn(expected);

        PutLoansLoanIdResponse result = loansFineractClient.updateLoanApplication(7L, request, null);

        assertThat(result).isSameAs(expected);
        verify(loansApi).updateLoanApplication(7L, request, null);
    }

    @Test
    void handleCommandsLoanDelegatesToGeneratedApi() {
        PostLoansLoanIdRequest request = new PostLoansLoanIdRequest();
        PostLoansLoanIdResponse expected = new PostLoansLoanIdResponse();
        when(loansApi.handleCommandsLoan(7L, request, "withdrawByApplicant")).thenReturn(expected);

        PostLoansLoanIdResponse result = loansFineractClient.handleCommandsLoan(7L, request, "withdrawByApplicant");

        assertThat(result).isSameAs(expected);
        verify(loansApi).handleCommandsLoan(7L, request, "withdrawByApplicant");
    }
}