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
import { I18N, STORAGE } from '../adapters';

@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly i18n = inject(I18N);
  private readonly storage = inject(STORAGE);

  constructor() {
    const stored = this.storage.read('language');
    if (stored) {
      this.i18n.use(stored);
    }
  }

  translate(key: string, params?: Record<string, unknown>): string {
    return this.i18n.translate(key, params);
  }

  use(languageCode: string): void {
    this.i18n.use(languageCode);
    this.storage.write('language', languageCode);
  }
}
