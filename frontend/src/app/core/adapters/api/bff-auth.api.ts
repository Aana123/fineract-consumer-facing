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
import { AuthenticationCommandControllerService } from '@bff/client';
import { DeviceFingerprint } from '../../auth/device-fingerprint';
import type {
  AuthApi,
  LoginChallengeCommandData,
  LoginCommandRequest,
  SessionCommandData,
  VerifyTwoFactorCommandRequest,
} from './auth.api';

@Injectable({ providedIn: 'root' })
export class BffAuthApi implements AuthApi {
  private readonly api = inject(AuthenticationCommandControllerService);
  private readonly fingerprint = inject(DeviceFingerprint);

  login(request: LoginCommandRequest): Observable<LoginChallengeCommandData> {
    return this.api.login(this.fingerprint.get(), request);
  }

  verifyTwoFactor(request: VerifyTwoFactorCommandRequest): Observable<SessionCommandData> {
    return this.api.verifyTwoFactor(this.fingerprint.get(), request);
  }

  refreshSession(): Observable<SessionCommandData> {
    return this.api.refreshSession(this.fingerprint.get());
  }

  logout(): Observable<unknown> {
    return this.api.logout();
  }
}
