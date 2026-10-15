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

import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BeneficiariesCommandControllerService,
  BeneficiariesQueryControllerService,
} from '@bff/client';
import { DeviceFingerprint } from '../../auth/device-fingerprint';
import type {
  BeneficiaryApi,
  BeneficiaryChallengeCommandData,
  BeneficiaryCommandData,
  BeneficiaryQueryData,
  ConfirmAddBeneficiaryCommandRequest,
  ConfirmUpdateBeneficiaryCommandRequest,
  InitiateAddBeneficiaryCommandRequest,
  InitiateUpdateBeneficiaryCommandRequest,
} from './beneficiary.api';

@Injectable({ providedIn: 'root' })
export class BffBeneficiaryApi implements BeneficiaryApi {
  private readonly query = inject(BeneficiariesQueryControllerService);
  private readonly command = inject(BeneficiariesCommandControllerService);
  private readonly fingerprint = inject(DeviceFingerprint);

  list(): Observable<BeneficiaryQueryData[]> {
    return this.query.listBeneficiaries();
  }

  initiateAdd(
    request: InitiateAddBeneficiaryCommandRequest,
  ): Observable<BeneficiaryChallengeCommandData> {
    return this.command.initiateAddBeneficiary(this.fingerprint.get(), request);
  }

  confirmAdd(request: ConfirmAddBeneficiaryCommandRequest): Observable<BeneficiaryCommandData> {
    return this.command.confirmAddBeneficiary(this.fingerprint.get(), request);
  }

  initiateUpdate(
    publicId: string,
    request: InitiateUpdateBeneficiaryCommandRequest,
  ): Observable<BeneficiaryChallengeCommandData> {
    return this.command.initiateUpdateBeneficiary(this.fingerprint.get(), publicId, request);
  }

  confirmUpdate(
    publicId: string,
    request: ConfirmUpdateBeneficiaryCommandRequest,
  ): Observable<BeneficiaryCommandData> {
    return this.command.confirmUpdateBeneficiary(this.fingerprint.get(), publicId, request);
  }

  remove(publicId: string): Observable<unknown> {
    return this.command.deleteBeneficiary(publicId);
  }
}
