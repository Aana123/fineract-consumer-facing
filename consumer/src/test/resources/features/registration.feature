# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

Feature: Consumer registration

  Background:
    Given a fresh Fineract client exists with a Passport identifier

  Scenario: Successful registration through OTP verification
    When I submit registration with the matching Passport
    Then registration is accepted in PENDING_OTP state
    When I request an email OTP
    Then an OTP is delivered to my email
    When I retrieve the OTP from Mailpit
    And I verify the OTP
    Then my registration advances to BOUND

  Scenario: Submit with mismatched identifier is rejected
    When I submit registration with a non-matching Passport value
    Then registration is rejected
    And the rejection does not reveal which field failed

  # The code itself is single-use — OtpServiceImplTest and login.feature pin that directly. What
  # this asserts is that a replay cannot re-bind the account: since the OTP steps require
  # PENDING_OTP, the state guard refuses the replay before the spent code is even looked at.
  Scenario: Replaying a verified OTP cannot re-bind the account
    When I complete an OTP verification successfully
    And I verify the same OTP a second time
    Then the replay is refused because registration is already complete

  Scenario: Verifying a wrong OTP is rejected
    When I submit registration with the matching Passport
    Then registration is accepted in PENDING_OTP state
    And I request an email OTP
    And I verify a wrong OTP
    Then the OTP is rejected as invalid

  Scenario: Registering the same email twice is rejected
    When I submit registration with the matching Passport
    Then registration is accepted in PENDING_OTP state
    When a second Fineract client submits registration with the same email
    Then registration is rejected as an existing user

  Scenario: A Fineract client that already has a registration cannot register again
    When I submit registration with the matching Passport
    Then registration is accepted in PENDING_OTP state
    When I submit registration again with a different email
    Then registration is rejected as an existing user

  # A client that is already spoken for must be refused before its identifier is ever checked.
  # Checking the identifier first made the pair of answers an oracle: 403 for a wrong government ID,
  # 409 for a right one, on a public endpoint, against a sequential client id.
  Scenario: A client that already has a registration is refused the same way whether the identifier matches or not
    When I submit registration with the matching Passport
    Then registration is accepted in PENDING_OTP state
    When I submit registration again with a different email and a non-matching Passport
    Then registration is rejected as an existing user

  Scenario: Wrong identifiers are capped, so the right one stops working too
    When I submit registration with a non-matching Passport value until the client is locked out
    And I submit registration with the matching Passport
    Then registration is rejected

  Scenario: A completed registration cannot be made to send another code
    When I complete an OTP verification successfully
    And I request an email OTP
    Then the code request is refused because registration is already complete
