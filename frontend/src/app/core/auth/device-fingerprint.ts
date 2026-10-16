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

import { inject, Injectable } from '@angular/core';
import { STORAGE } from '../adapters';

@Injectable({ providedIn: 'root' })
export class DeviceFingerprint {
  private readonly storage = inject(STORAGE);
  private deviceId: string | null = null;
  get(): string {
    if (this.deviceId) {
      return this.deviceId;
    }
    try {
      let stored = this.storage.read('deviceId');
      if (!stored) {
        stored = crypto.randomUUID();
        this.storage.write('deviceId', stored);
      }
      this.deviceId = stored;
    } catch {
      this.deviceId = crypto.randomUUID();
    }
    return this.deviceId;
  }
}
