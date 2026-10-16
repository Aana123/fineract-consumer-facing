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
import { Observable, map } from 'rxjs';
import { UserCommandControllerService, UserQueryControllerService } from '@bff/client';
import type { UserChargesQueryResponse } from '@bff/client';
import { DeviceFingerprint } from '../../auth/device-fingerprint';
import type {
  ChargesFilter,
  ConfirmPasswordChangeCommandRequest,
  ForgotPasswordCommandRequest,
  InitiatePasswordChangeCommandRequest,
  ResetPasswordCommandRequest,
  UserApi,
  UserChargesPage,
  UserImageQueryData,
  UserPasswordChangeChallengeCommandData,
  UserProfileQueryData,
} from './user.api';

export function mapUserCharges(response: UserChargesQueryResponse): UserChargesPage {
  return {
    charges: response.charges ?? [],
    totalFilteredRecords: response.totalFilteredRecords ?? 0,
  };
}

@Injectable({ providedIn: 'root' })
export class BffUserApi implements UserApi {
  private readonly query = inject(UserQueryControllerService);
  private readonly command = inject(UserCommandControllerService);
  private readonly fingerprint = inject(DeviceFingerprint);

  getProfile(): Observable<UserProfileQueryData> {
    return this.query.getUserProfile();
  }

  listCharges(filter: ChargesFilter): Observable<UserChargesPage> {
    return this.query
      .getUserCharges(filter.status, filter.page, filter.size)
      .pipe(map((response) => mapUserCharges(response)));
  }

  getImage(maxWidth: number, maxHeight: number): Observable<UserImageQueryData> {
    return this.query.getUserImage(maxWidth, maxHeight);
  }

  forgotPassword(request: ForgotPasswordCommandRequest): Observable<unknown> {
    return this.command.forgotPassword(this.fingerprint.get(), request);
  }

  resetPassword(request: ResetPasswordCommandRequest): Observable<unknown> {
    return this.command.resetPassword(this.fingerprint.get(), request);
  }

  initiatePasswordChange(
    request: InitiatePasswordChangeCommandRequest,
  ): Observable<UserPasswordChangeChallengeCommandData> {
    return this.command.initiatePasswordChange(this.fingerprint.get(), request);
  }

  confirmPasswordChange(request: ConfirmPasswordChangeCommandRequest): Observable<unknown> {
    return this.command.confirmPasswordChange(this.fingerprint.get(), request);
  }
}
