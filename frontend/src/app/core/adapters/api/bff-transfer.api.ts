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
import { TransfersCommandControllerService, TransfersQueryControllerService } from '@bff/client';
import { DeviceFingerprint } from '../../auth/device-fingerprint';
import { mapPage } from './bff-page';
import type { Page } from './page';
import type {
  ConfirmTransferCommandRequest,
  InitiateTransferCommandRequest,
  TransferApi,
  TransferChallengeCommandData,
  TransferCommandData,
  TransferQueryData,
} from './transfer.api';

@Injectable({ providedIn: 'root' })
export class BffTransferApi implements TransferApi {
  private readonly command = inject(TransfersCommandControllerService);
  private readonly query = inject(TransfersQueryControllerService);
  private readonly fingerprint = inject(DeviceFingerprint);
  initiate(request: InitiateTransferCommandRequest): Observable<TransferChallengeCommandData> {
    return this.command.initiateTransfer(this.fingerprint.get(), request);
  }

  confirm(
    idempotencyKey: string,
    request: ConfirmTransferCommandRequest,
  ): Observable<TransferCommandData> {
    return this.command.confirmTransfer(this.fingerprint.get(), idempotencyKey, request);
  }

  list(page: number, size: number): Observable<Page<TransferQueryData>> {
    return this.query
      .listTransfers(undefined, undefined, page, size)
      .pipe(map((response) => mapPage(response)));
  }
}
