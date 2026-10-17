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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.apache.fineract.consumer.infrastructure.otp.exception.OtpDeliveryFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpEmailDeliveryService {

    private final JavaMailSender javaMailSender;

    @Value("${otp.email.from}")
    private String fromAddress;

    @Value("${otp.email.subject}")
    private String subject;

    /**
     * The purpose is named in the body because a code that does not say what it authorises is a code
     * the recipient can be talked into typing somewhere else.
     */
    public void deliver(String target, String token, OtpPurpose purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(target);
        message.setSubject(subject);
        message.setText("Your verification code is: " + token
                + "\n\nUse it to " + purpose.getEmailDescription() + "."
                + "\n\nThis code expires in 5 minutes. It works for nothing else, so if you were asked"
                + " for it to do anything other than the above, do not share it.");
        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new OtpDeliveryFailedException(e);
        }
    }
}
