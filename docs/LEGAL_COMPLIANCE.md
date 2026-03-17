# Legal & Compliance Reference

**Document version:** 2026-03-17
**App version scope:** BankSMSTracker (all builds up to and including the `feature/create-pov` branch)

---

## Introduction

BankSMSTracker is an Android application that:

1. **Reads incoming bank SMS messages** via the `RECEIVE_SMS` broadcast intent and the `READ_SMS` content provider.
2. **Parses payment, income, and transactional data** from those messages using user-configured regex rules (sender address matching + named capture groups for `amount`, `currency`, `card`, `merchant`, `date`, `balance`).
3. **Stores parsed results on-device** in a Room/SQLite database (`bank_sms_tracker.db`) — including payment records, income records, and configuration (senders, rules, categories).
4. **Exports data** in CSV/JSON format via Android's share intent (user-initiated, no automatic network transmission).
5. **Optionally runs a foreground service** (`SmsProcessingService`) to process SMS in real time in the background.

The data it creates from parsed messages includes: transaction amounts and currencies, card tail digits (last 4), merchant names, transaction timestamps, account balance at time of transaction, and the sender address (bank short code or number). It also stores SHA-256 hashes of `sender::message_body` for deduplication, meaning the original SMS body content is implicitly available for regeneration given the hash.

**Why this document matters:** The app is currently designed for TBC Bank (Georgia) but shares no inherent geographic restriction. A developer who publishes it on the Google Play Store makes it available globally. Processing financial SMS content triggers obligations under data protection laws in every jurisdiction where users download the app. Failure to comply before distribution can result in app removal, fines, and civil liability. This document is a practical reference for understanding those obligations.

**Disclaimer:** This document is a technical and legal landscape overview prepared to assist developers. It is not legal advice. For binding guidance, engage qualified lawyers in each relevant jurisdiction before distribution.

---

## 1. European Union

### 1.1 GDPR (General Data Protection Regulation)

**Regulation:** Regulation (EU) 2016/679, fully effective 25 May 2018. Enforced by national Data Protection Authorities (DPAs). Significant fines: up to 4% of global annual turnover or €20 million (whichever is higher).

#### What data the app processes that qualifies as personal data

Under GDPR Article 4(1), personal data is "any information relating to an identified or identifiable natural person." The app collects:

| Data element | GDPR classification |
|---|---|
| SMS body text (parsed) | Personal data (financial context) |
| Transaction amount and currency | Personal financial data |
| Card tail digits (last 4) | Personal data — partial payment card identifier |
| Merchant name | Personal data (reveals purchasing behavior) |
| Transaction timestamp | Personal data (behavioral profile element) |
| Account balance | Personal financial data |
| Bank sender address (short code) | Personal data (identifies banking relationship) |
| SHA-256 hash of `sender::body` | Pseudonymised personal data (GDPR Recital 26: pseudonymisation does not exclude personal data status if re-identification is possible) |
| SharedPreferences `user_agreed_to_terms` | Consent record — itself personal data |

**Collectively**, these elements form a detailed financial behavioral profile of the user. Even without a name or email, this profile is almost certainly linkable to an identified individual (the phone owner). GDPR applies in full.

#### Which GDPR articles apply

**Article 5 — Principles of processing (storage limitation):** Personal data must be "kept in a form which permits identification of data subjects for no longer than is necessary for the purposes for which the personal data are processed." The app currently stores all payment records indefinitely with no retention policy or automatic deletion.

**Article 6 — Lawfulness of processing:** Processing must have a legal basis. For a personal budgeting tool, the most appropriate basis is Article 6(1)(a) (consent) or 6(1)(b) (performance of a contract with the user). The app asks users to agree to Terms of Service before use, but the Terms text is not reviewed in this document; they must specify the legal basis and processing purpose.

**Article 9 — Special categories (financial data note):** Financial data is not an Article 9 special category. However, payment patterns can indirectly reveal health conditions, religious practice, or political affiliation (e.g., payments to a mosque, a pharmacy, or a political party). Developers should be aware that payment records can become indirectly sensitive.

**Article 13/14 — Transparency (information to data subjects):** At the time of collection, the app must inform the user of: the identity of the data controller, the purposes and legal basis of processing, the retention period, their rights (access, erasure, portability, objection), and whether data is transferred internationally. The current Terms dialog (`app_terms` SharedPreferences) appears to be a terms acceptance gate, but its content is not verified to contain all Article 13 mandatory elements.

**Article 17 — Right to erasure ("right to be forgotten"):** Users have the right to request deletion of their personal data. The app provides no erasure feature. There is no "Delete all payments" or "Delete my data" function visible in the codebase.

**Article 20 — Right to data portability:** Users have the right to receive their data in a machine-readable format. The CSV and JSON export features (`PaymentsActivity.exportToCsv()`, `SmsExportActivity`) partially satisfy this, but they export app-formatted data, not a structured data schema. A full portability implementation would export all stored data (payments, incomes, categories, senders, rules) in a documented JSON schema.

**Article 25 — Privacy by design and by default:** The app must implement appropriate technical measures. Relevant gaps: no retention policy (data accumulates indefinitely); backup to Google servers on API 31+ devices due to empty `data_extraction_rules.xml` (see REM-1 in the code review); production log emission of SMS body and sender content in `SmsProcessingService` (see NEW-1 in the code review).

**Article 32 — Security of processing:** "Appropriate technical and organisational measures" to ensure security. Relevant findings:
- The database is stored in app-private storage (good — inaccessible to other apps without root).
- No encryption at rest (Room database is unencrypted SQLite).
- Auto Backup on API 31+ devices sends the database to Google Drive (gap, see REM-1).
- Code obfuscation is disabled (`isMinifyEnabled = false` — REM-2), making reverse engineering easier.

**Article 27 — EU representative:** If the developer is not established in the EU but processes data of EU residents, they must designate an EU representative. This is relevant if the app is published on Google Play for EU users.

#### Current app status

- No retention/deletion policy implemented.
- No erasure feature (UI or data layer).
- Terms dialog exists but content compliance with Article 13 is unverified.
- Auto Backup gap on API 31+ devices sends financial data to Google servers.
- Production logs contain SMS body content (`SmsProcessingService`).
- No encryption at rest for the Room database.

#### Recommendations

| Priority | Action |
|---|---|
| BLOCKING | Fix `data_extraction_rules.xml` to exclude `bank_sms_tracker.db` from cloud backup (API 31+). |
| BLOCKING | Add a "Delete all my data" feature that purges the payments, incomes, and configuration tables. |
| HIGH | Draft and display a GDPR-compliant privacy notice (all Article 13 elements) before first data collection. The current terms dialog must be verified or replaced. |
| HIGH | Implement a configurable data retention period (e.g., 1 year default, user-adjustable). |
| HIGH | Guard all `SmsProcessingService` log statements that include SMS body or sender content with `BuildConfig.DEBUG`. |
| MEDIUM | Evaluate enabling Room database encryption (SQLCipher or Android Keystore AES). |
| MEDIUM | Designate an EU GDPR representative if distributing to EU users from a non-EU entity. |
| LOW | Add a data export function that covers all stored tables (full portability, Article 20). |

---

### 1.2 PSD2 (Payment Services Directive 2)

**Regulation:** Directive (EU) 2015/2366, implemented by EU member states (in UK: Payment Services Regulations 2017, now diverged post-Brexit). Governs payment services and account information services.

#### Whether SMS-parsing apps qualify as AISPs

PSD2 defines an Account Information Service Provider (AISP) as an entity that provides "an online service to provide consolidated information on one or more payment accounts held by the payment service user with either another payment service provider or with more than one payment service provider" (Annex I, §8).

The critical distinction is **how the app obtains account data**:

- **API-based aggregators** that connect to bank Open Banking APIs to pull transaction data: clearly AISPs under PSD2, require authorization from the national competent authority.
- **Screen-scraping aggregators** that log into online banking portals on behalf of users: historically treated as AISPs; PSD2 Article 31 requires banks to provide API access instead.
- **SMS-parsing apps** (this app): the app does not connect to the bank. It reads SMS messages that the bank has already sent to the user's phone. There is no direct bank connection. The app is a local data processor, not an account data accessor in the PSD2 sense.

**Assessment:** BankSMSTracker almost certainly does not qualify as an AISP under PSD2 because it does not access payment accounts — it reads SMS messages that the bank has unilaterally sent to the user. The app has no ability to initiate payments, access account credentials, or connect to banking infrastructure.

**However:** This is a grey area that has not been formally adjudicated in any EU jurisdiction as of the knowledge cutoff date. A national DPA or financial regulator could take a different view, particularly if the app is marketed in a way that emphasizes its role as an "account tracker" or "financial aggregator."

#### What to disclose in the privacy policy

Even if AISP regulation does not apply, the privacy policy should clarify:

- The app does not connect to any bank or financial institution.
- The app does not access online banking portals or APIs.
- All data originates from SMS messages delivered to the user's device.
- No financial credentials are collected or stored.

#### Recommendation

Consult a financial regulation lawyer before distributing in the EU if the app's marketing emphasizes financial account aggregation. The legal risk is low for a clearly labelled "personal SMS parser" but increases if the app's framing resembles a financial service. Include the above disclosure points in the privacy policy.

---

### 1.3 ePrivacy Directive

**Regulation:** Directive 2002/58/EC (ePrivacy Directive), as amended by Directive 2009/136/EC. Under revision (proposed ePrivacy Regulation has not yet been adopted as of the knowledge cutoff date).

#### SMS content and confidentiality of communications

Article 5 of the ePrivacy Directive requires member states to ensure "the confidentiality of communications and the related traffic data by means of a public communications network and with publicly available electronic communications services." This primarily governs **service providers** (telecoms operators, messaging platforms), not end-user applications.

The ePrivacy Directive's confidentiality obligation runs between:
- The network operator and the end user.
- Service providers who intercept or process communications in transit.

An app running on the user's own device, reading messages already delivered to that device, with the user's consent, is in a fundamentally different position. The user is one of the parties to the communication and can lawfully process their own received messages.

#### Applicability to on-device processing with no server transmission

The app does not transmit SMS content to any server. All processing is local. The ePrivacy Directive's obligations on confidentiality of communications in transit do not apply to local processing on the recipient's own device.

**Residual consideration:** The upcoming ePrivacy Regulation (if adopted) may extend obligations to software that processes communications content. Developers should monitor legislative progress.

**Current app status:** No network transmission of SMS content. Processing is on-device only.

**Recommendation:** No immediate action required under the current ePrivacy Directive. Monitor the ePrivacy Regulation progress if distributing in the EU.

---

## 2. Russian Federation

### 2.1 FZ-152 (Federal Law on Personal Data)

**Law:** Federal Law No. 152-FZ "On Personal Data," dated 27 July 2006, as amended. Enforced by Roskomnadzor. Significant fines and potential blocking of non-compliant services.

#### What data qualifies as personal data under Russian law

Under FZ-152 Article 3(1), personal data is "any information relating directly or indirectly to a specific or identifiable individual (personal data subject)." The app's data elements (payment amounts, card tails, merchant names, timestamps, account balance, sender addresses) qualify as personal data under this definition for the same reasons as under GDPR.

#### Data localization requirement

**Article 18.1** (amended by Federal Law No. 242-FZ, effective 1 September 2015, penalties strengthened by subsequent amendments): When collecting personal data of Russian citizens, the operator must ensure that recording, systematisation, accumulation, storage, clarification, and retrieval of personal data of Russian citizens is performed using databases located in Russia.

**How the app is affected:**

The on-device Room database (`bank_sms_tracker.db`) is stored on the user's own phone in Russia, which satisfies the localization requirement. Local storage on a Russian user's device in Russia is on Russian territory.

**The Auto Backup problem:** Android's Auto Backup feature, when enabled, backs up the database to Google Drive. Google's backup infrastructure routes data through Google servers globally, including servers outside Russia. If `data_extraction_rules.xml` is empty (the current state on API 31+ devices — see REM-1 in the code review), `bank_sms_tracker.db` is backed up to Google Drive, which violates Article 18.1 for Russian users.

**Fix status:** `backup_rules.xml` correctly excludes `bank_sms_tracker.db` — but this file is only consulted on devices running Android 11 (API 30) and below. On Android 12+ (API 31+), Android uses `data_extraction_rules.xml` exclusively. That file is currently empty boilerplate, meaning `bank_sms_tracker.db` is backed up to Google Drive on all modern Android devices. This is a direct violation of FZ-152 Article 18.1 for Russian users.

**SharedPreferences:** The `app_terms.xml` SharedPreferences file (excluded by `backup_rules.xml`) also needs exclusion in `data_extraction_rules.xml`. Other SharedPreferences files (e.g., `payments_filter_state`, `onboarding`) may contain personally relevant state but are not currently excluded.

#### Operator registration

FZ-152 Article 22 requires personal data operators to notify Roskomnadzor before processing personal data, with limited exceptions (e.g., processing data only for the purposes of an agreement with the data subject, processing publicly available data, processing employee data). A personal budgeting app arguably falls under the agreement-purpose exception, but this requires legal analysis.

#### Current app status

- On-device storage: compliant for Russian users.
- Auto Backup on API 31+ devices: **non-compliant** — `data_extraction_rules.xml` is empty, database backs up to Google Drive.
- `backup_rules.xml`: correctly configured but only effective on API 30 and below.
- Operator registration: unassessed.

#### Recommendations

| Priority | Action |
|---|---|
| BLOCKING | Fix `data_extraction_rules.xml` immediately. This is the same fix required for GDPR compliance — one fix resolves both jurisdictions. |
| HIGH | Consider a Russian-market build variant with `android:allowBackup="false"` as a belt-and-suspenders measure. |
| MEDIUM | Seek legal advice on Roskomnadzor notification requirements before distributing in Russia. |

---

### 2.2 FZ-149 (Federal Law on Information, Information Technologies and Information Protection)

**Law:** Federal Law No. 149-FZ, dated 27 July 2006, as amended.

#### General obligations

FZ-149 establishes general obligations for information system operators. For personal data processing systems, the more specific FZ-152 takes precedence. FZ-149 is primarily relevant for:

- Requirements to protect information systems from unauthorized access (Article 16).
- Obligations to restrict access to protected information.

#### Application to this app

The app's database is in app-private storage (not world-readable). No network API is exposed. The foreground service processes SMS only in response to incoming broadcasts. FZ-149 Article 16 security obligations are met by the Android sandbox model, provided Auto Backup is properly restricted (see §2.1).

**Recommendation:** No additional action beyond the Auto Backup fix. The Android sandbox meets FZ-149 technical protection requirements for a local app with no server component.

---

### 2.3 Russian Banking Regulations (Bank of Russia)

**Regulator:** Bank of Russia (Банк России), regulated by Federal Law No. 86-FZ "On the Central Bank."

#### CBR regulations on mobile banking security

The Bank of Russia regulates financial institutions, payment system operators, and entities providing payment services. Key regulations for mobile contexts include:

- **GOST R 57580.1-2017** (information security standard for financial organizations): applies to regulated financial entities, not to consumer applications.
- **Regulation No. 683-P / 684-P** (on information security requirements for credit organizations): applies to banks, not to users' personal applications.
- **Regulation No. 382-P** (on requirements for payment infrastructure protection): applies to payment service operators.

#### Whether SMS parsing for personal budgeting qualifies as a financial service

Under Russian financial regulation, a "payment service" requires handling, transmitting, or processing payment orders or payment instruments. This app does none of these. It reads text messages. It cannot initiate transactions, access accounts, or move money.

**Assessment:** The app does not constitute a financial service under Russian law. Bank of Russia regulations do not apply directly. The app's compliance obligation is under FZ-152 (personal data), not financial services law.

**Note:** If the developer were to add features that aggregate data from multiple sources, provide investment advice, or connect to bank APIs, re-assessment would be necessary.

**Recommendation:** No Bank of Russia registration or authorization is required for the current feature set.

---

## 3. Kazakhstan

### 3.1 Law on Personal Data and its Protection (2013, amended 2021)

**Law:** Law of the Republic of Kazakhstan No. 94-V "On Personal Data and its Protection," dated 21 May 2013, amended by Law No. 58-VII in 2021. Enforced by the Ministry of Digital Development, Innovations and Aerospace Industry.

#### Personal data localization requirements

Kazakhstan Law Article 8 requires personal data databases of Kazakhstani citizens to be located on servers in Kazakhstan. The same Auto Backup analysis from §2.1 applies: local storage is compliant; Auto Backup via Google Drive is not.

The 2021 amendments strengthened enforcement mechanisms and increased the similarity of Kazakhstan's framework to GDPR. Cross-border transfer of personal data is permitted only under conditions: the recipient country has adequate protection, or the data subject has given written consent, or there is a bilateral agreement.

Google's infrastructure does not have a blanket adequacy decision from Kazakhstan. Auto Backup to Google Drive involves cross-border transfer without explicit per-user consent to the specific transfer.

#### Consent requirements

Article 9 requires freely given, informed, and specific consent before personal data collection. The consent must be:

- In writing, or in electronic form with a digital signature or other reliable authentication method.
- For a specific and named purpose.
- Withdrawable at any time.

The current terms acceptance gate (`app_terms` SharedPreferences) may not satisfy Kazakhstani written-consent requirements if the Terms text does not specifically name personal data processing purposes.

#### Current app status

- Same Auto Backup non-compliance as Russia.
- Consent mechanism unverified against Kazakhstani requirements.

#### Recommendations

| Priority | Action |
|---|---|
| BLOCKING | Fix `data_extraction_rules.xml` (same fix as GDPR/Russia). |
| HIGH | If distributing in Kazakhstan, ensure the consent screen names specific data elements being collected and their purpose, in a form that meets Article 9 requirements. |
| MEDIUM | Consult a Kazakhstani data protection lawyer before distributing, particularly regarding the "written consent" interpretation for mobile app consent flows. |

---

## 4. Georgia / TBC Bank Context

### 4.1 Georgian Personal Data Protection Law

**Law:** Law of Georgia on Personal Data Protection, originally enacted 2011, substantially amended 2023 to align with GDPR. Enforced by the Personal Data Protection Service (PDPS).

#### Context: TBC Bank

The app's default rule set (`default_rules.json`) is specifically tuned for TBC Bank Georgia. The primary user base is Georgian users with TBC Bank SMS messages. This makes Georgian compliance particularly important.

#### GDPR alignment (2023 amendments)

The 2023 amendments brought Georgian law into close alignment with GDPR:

- **Article 5 equivalents:** Same data minimization, purpose limitation, and storage limitation principles.
- **Consent:** Must be freely given, specific, informed, and unambiguous. Withdrawable at any time without detriment.
- **Data subject rights:** Right of access, right to erasure ("right to be forgotten"), right to data portability, right to object.
- **Data Controller obligations:** Must provide transparency information (equivalent to GDPR Article 13/14).

**Key difference from GDPR:** The PDPS has enforcement authority but fines are structured differently from GDPR's percentage-of-turnover model. Administrative sanctions under the 2023 law are significant but smaller than EU GDPR maximum fines.

#### Key requirements for this app

**Consent and purpose limitation:** The app must inform Georgian users that it reads and stores financial SMS content, for what purpose (personal budgeting), for how long, and how to withdraw consent (delete data).

**Right to erasure:** PDPS enforces the right to erasure. The app has no deletion feature — this is a gap under Georgian law as well as under GDPR.

**Cross-border transfers:** If data leaves Georgia (which it does via Auto Backup to Google Drive on API 31+ devices), the transfer must be to a country with adequate protection or under approved safeguards. Google's data processing infrastructure does not have a Georgian PDPS adequacy decision.

#### Current app status

- No data deletion feature.
- Auto Backup gap sends data to Google servers.
- TBC Bank-specific default rules suggest a primary Georgian user base, making Georgian compliance the most immediately relevant non-EU jurisdiction.

#### Recommendations

| Priority | Action |
|---|---|
| BLOCKING | Fix `data_extraction_rules.xml`. Given the Georgian user focus, this is the highest immediate risk. |
| BLOCKING | Implement a data erasure feature before distributing to Georgian users. |
| HIGH | Draft a privacy notice specifically compliant with the 2023 Georgian law (PDPS guidance is available). |
| HIGH | Include consent withdrawal instructions in the Terms dialog. |
| MEDIUM | Contact the PDPS for guidance if uncertain about specific requirements. |

---

## 5. India

### 5.1 DPDP Act 2023 (Digital Personal Data Protection Act)

**Law:** Digital Personal Data Protection Act, 2023 (Act No. 22 of 2023). Assent received August 2023. Rules (implementing regulations) are pending as of the knowledge cutoff date — the Act's full operationalization depends on the Rules being notified. Enforced by the Data Protection Board of India (once constituted).

#### Status note

The DPDP Act 2023 is enacted but not yet fully operationalized. Rules have not been notified. Developers should plan for compliance now, as the transition timeline is uncertain.

#### Which categories of personal data are processed

The DPDP Act defines "personal data" as "any data about an individual who is identifiable by or in relation to such data." All data elements identified in the GDPR section (§1.1) qualify as personal data under the DPDP Act.

The Act defines "sensitive personal data" by reference to categories specified in the Rules (not yet published). Financial data is widely expected to be classified as sensitive, consistent with the prior IT Rules 2011 framework. Payment amounts, card data, and banking behavior would likely qualify.

#### Consent requirements

Section 6 of the DPDP Act: consent must be:

- **Free, specific, informed, unconditional, and unambiguous** (an affirmative action).
- Accompanied by a notice in clear and plain language.
- **Withdrawable** at any time, as easily as it was given.
- The Data Fiduciary (app developer) must provide a mechanism to withdraw consent.

**Current gap:** The app has a terms acceptance gate but no consent withdrawal mechanism. Users cannot withdraw consent via the app — there is no data deletion flow.

#### Right to erasure and data portability

Section 12 provides the data principal's right to:
- Erasure of their personal data (upon withdrawal of consent or where purpose is fulfilled).
- A summary of the personal data being processed and identities of Data Fiduciaries and Data Processors with whom the data has been shared.

Section 13 provides the right to data portability (details to be specified in the Rules).

**Current gap:** Neither right is implemented.

#### Obligations of Data Fiduciaries

Sections 8-11 impose obligations including: ensuring accuracy and completeness of data; implementing security safeguards; notifying the Board of data breaches; not retaining data beyond the purpose period.

**Retention:** The DPDP Act requires erasure when the purpose is fulfilled or consent is withdrawn. Indefinite storage without a retention policy likely violates Section 8(7).

#### Recommendations

| Priority | Action |
|---|---|
| HIGH | Implement consent withdrawal (data erasure) before full operationalization of the DPDP Act. |
| HIGH | Draft a DPDP-compliant notice with all Section 5 required elements. |
| MEDIUM | Monitor the Rules (implementing regulations) for finalized categories of sensitive data and portability requirements. |
| MEDIUM | Implement a data breach notification procedure (even as a documented internal procedure). |

---

## 6. South Korea

### 6.1 PIPA (Personal Information Protection Act)

**Law:** Personal Information Protection Act (개인정보 보호법), Act No. 11990, enacted 2011, substantially revised 2023 (effective September 2023). Enforced by the Personal Information Protection Commission (PIPC). Fines: up to 3% of total sales from the activity involving the violation.

#### Sensitive financial data classification

PIPA Article 23 designates "sensitive information" including information that could significantly infringe on the data subject's privacy. Financial behavioral data (payment history, account balance) is not explicitly listed as sensitive under Article 23 (which lists health, genetics, criminal history, biometrics, political opinion, union membership, sexual orientation, and nationality). However, financial data can still be classified as sensitive in practice by PIPC interpretation.

Regular personal data processing requires consent under Article 15 or another lawful basis. Financial payment records from bank SMS clearly constitute personal information under PIPA Article 2(1).

#### Consent and withdrawal requirements

PIPA Article 15(1): Personal information may be processed with the data subject's consent. The consent must:

- Be specific to the purpose.
- Be distinguishable for different purposes if multiple.
- Be withdrawable (Article 37: data subjects may withdraw consent at any time).
- Withdrawal must be no more difficult than the method of giving consent.

**Article 36:** Right to request correction or erasure of personal information that is inaccurate or unnecessarily retained. If the purpose is fulfilled, data must be destroyed without delay.

#### Similar gaps as India DPDP

The same structural gaps apply:

- No consent withdrawal mechanism.
- No data erasure feature.
- No retention policy.
- Indefinite storage of financial records without purpose re-assessment.

#### Cross-border transfer (Article 17(3))

If data is transferred outside South Korea, the recipient must meet adequacy standards or specific contract conditions must be satisfied. The Auto Backup gap applies here as well.

#### Recommendations

| Priority | Action |
|---|---|
| BLOCKING | Fix Auto Backup to prevent database backup to Google servers. |
| HIGH | Implement data erasure and consent withdrawal before distributing in South Korea. |
| HIGH | Draft PIPA-compliant consent notices. |
| MEDIUM | Assess whether financial SMS data constitutes sensitive information under PIPC guidance and apply higher-tier safeguards if so. |

---

## 7. PCI-DSS (Global)

### 7.1 Applicability

**Standard:** Payment Card Industry Data Security Standard (PCI-DSS), version 4.0 (released March 2022), maintained by the PCI Security Standards Council.

#### PCI-DSS scope for card data

PCI-DSS applies to entities that store, process, or transmit **cardholder data (CHD)** or **sensitive authentication data (SAD)**. The standard defines CHD as: Primary Account Number (PAN), cardholder name, expiration date, service code.

The app stores card tail digits — the last 4 digits of a card number as extracted from bank SMS messages (the `card` named capture group). This is explicitly out of scope for PCI-DSS: PCI-DSS FAQ and Requirement 3 make clear that truncated card numbers (where the middle digits are removed or not stored) are not considered PAN and are not CHD.

**However:** The combination of card tail digits + merchant name + amount + timestamp + balance creates a transaction fingerprint that could, in the hands of an attacker, assist in:

- Confirming whether a specific transaction occurred (useful for social engineering or fraud confirmation).
- Correlating partial card numbers with known transaction records.

This does not bring the app into PCI-DSS scope, but it is a data minimization consideration.

#### Full card numbers (PAN)

If any bank's SMS format includes a full card number (which some do), and the user configures a regex that captures it, the app would store a full PAN. This would trigger PCI-DSS Requirement 3. The default rules use `(?<card>\d{4})` patterns (4 digits only), so this is not currently an issue with the default configuration. User-defined custom regex rules could capture more digits.

**Recommendation:** Add a note in user documentation (when created) warning against writing regex patterns that capture more than the last 4 card digits, and consider adding a validator that prevents `card` capture groups from matching more than 6 consecutive digits (per PCI-DSS card masking convention: first 6 and last 4 may be visible).

#### Recommendations

| Priority | Action |
|---|---|
| LOW | No PCI-DSS registration or formal compliance required under current default rules. |
| MEDIUM | Add documentation and optionally a UI warning about the risk of capturing full card numbers via custom regex patterns. |
| LOW | Note in the privacy policy that card tail digits (not full card numbers) are stored. |

---

## 8. Google Play Store Policies

### 8.1 SMS and Call Log permissions policy

**Policy:** Google Play Developer Policy — Permissions (SMS and Call Log restricted permissions). Updated periodically; check the current version at play.google.com/about/developer-content-policy.

#### READ_SMS is a restricted permission

Since 2019, Google classifies `READ_SMS` and `RECEIVE_SMS` as "restricted permissions" requiring a **Declaration Form** and approval from the Google Play team. Apps must:

1. **Justify core functionality**: The permission must be required for the app's primary function. An app cannot request `READ_SMS` for a secondary or optional feature.
2. **Submit a Declaration Form**: Explaining why the permission is needed and how data is used.
3. **Not collect data for undisclosed purposes**: SMS content may not be sent to servers, shared with third parties, or used for advertising.

**Retrospective parsing vs. real-time reception:** The app uses both `RECEIVE_SMS` (real-time interception of incoming SMS) and `READ_SMS` (reading the SMS inbox for historical processing in `SmsExportActivity` and `ApplyRulesActivity`). The `READ_SMS` use case — reading historical SMS for retrospective rule application — is the riskier one from Google Play's perspective. Policies historically have been most restrictive on exactly this pattern (mass inbox reading), as it has been abused by spyware.

**Retrospective rejection risk:** As documented in LIMITATION-001 (`docs/ISSUES.md`), on Android 10+ (API 29+), `READ_SMS` requires additional Play Store justification. Google may reject or revoke app approval for apps that read the full inbox retroactively.

**Recommendation:** Consider restructuring the app so that:
- `RECEIVE_SMS` is the primary data collection pathway (real-time monitoring only).
- Historical parsing (`ApplyRulesActivity`, `SmsExportActivity`) is gated behind a clear explanation to users that inbox reading is one-time and user-initiated.
- The Declaration Form makes the user-initiated, local-processing nature explicit.

#### 8.2 Financial data policy

**Policy:** Google Play Financial Services Policy.

Apps that process financial data must:

1. Link to a privacy policy from the store listing.
2. The privacy policy must accurately describe what data is collected and how it is used.
3. Not collect financial data for purposes beyond what is necessary for the app's function.
4. Comply with applicable law in each jurisdiction the app is available in.

The app currently has no store listing privacy policy (it is not distributed on Google Play as of the knowledge cutoff date). Before Play Store distribution, a privacy policy URL must be provided.

#### Current app status

- No Google Play submission made.
- No Declaration Form submitted for `READ_SMS`/`RECEIVE_SMS`.
- No Play Store privacy policy URL.

#### Recommendations

| Priority | Action |
|---|---|
| BLOCKING | Submit a Declaration Form for `READ_SMS` and `RECEIVE_SMS` before Play Store submission. |
| BLOCKING | Create and host a privacy policy that meets Google Play's financial data policy requirements. |
| HIGH | Separately justify the historical inbox reading use case (`ApplyRulesActivity`) vs. real-time reception (`SmsProcessingService`) in the Declaration Form. |
| HIGH | Consider limiting `READ_SMS` to Debug builds and making `RECEIVE_SMS` the sole production data collection pathway. This may simplify the Declaration Form justification. |
| MEDIUM | Review the current Play Store SMS/Call Log restricted permissions policy page before submission, as the policy is periodically revised. |

---

## 9. Recommendations Summary Table

| Jurisdiction | Regulation | Current Status | Required Action | Priority |
|---|---|---|---|---|
| EU | GDPR — Auto Backup (Article 32) | NON-COMPLIANT — `data_extraction_rules.xml` empty | Fix `data_extraction_rules.xml` to exclude database | BLOCKING |
| EU | GDPR — Right to erasure (Article 17) | GAP — no erasure feature | Implement "Delete my data" functionality | BLOCKING |
| Russia | FZ-152 — Data localization (Article 18.1) | NON-COMPLIANT — database backs up to Google (API 31+) | Fix `data_extraction_rules.xml` | BLOCKING |
| Georgia | Personal Data Protection Law — Auto Backup | NON-COMPLIANT — same as Russia | Fix `data_extraction_rules.xml` | BLOCKING |
| Kazakhstan | Law on Personal Data — Localization | NON-COMPLIANT — same as Russia | Fix `data_extraction_rules.xml` | BLOCKING |
| South Korea | PIPA — Cross-border transfer (Article 17) | NON-COMPLIANT — database backs up to Google (API 31+) | Fix `data_extraction_rules.xml` | BLOCKING |
| Google Play | SMS Restricted Permissions Policy | NOT SUBMITTED — no Declaration Form | Submit Declaration Form with justification | BLOCKING |
| Google Play | Financial Data Policy | NOT SUBMITTED — no privacy policy URL | Create and host privacy policy | BLOCKING |
| EU | GDPR — Transparency (Article 13) | UNVERIFIED — Terms dialog exists but content unreviewed | Verify/replace Terms dialog with Article 13 content | HIGH |
| EU | GDPR — Security (Article 32) — production logs | NON-COMPLIANT — SMS body logged in `SmsProcessingService` | Guard logs with `BuildConfig.DEBUG` | HIGH |
| India | DPDP Act 2023 — Consent withdrawal | GAP — no withdrawal mechanism | Implement consent withdrawal + data erasure | HIGH |
| South Korea | PIPA — Right to erasure (Article 36) | GAP — no erasure feature | Implement data erasure | HIGH |
| Georgia | Georgian PDPS — Right to erasure | GAP — no erasure feature | Implement data erasure | HIGH |
| EU | GDPR — Storage limitation (Article 5) | GAP — no retention policy | Implement retention period (user-configurable) | HIGH |
| India | DPDP Act — Retention (Section 8) | GAP — no retention policy | Implement retention period | HIGH |
| EU | GDPR — Security (Article 32) — obfuscation | WEAK — `isMinifyEnabled = false` | Enable R8 code shrinking for release builds | MEDIUM |
| EU | GDPR — Data portability (Article 20) | PARTIAL — CSV/JSON export exists for payments only | Extend export to cover all tables | MEDIUM |
| Russia | FZ-152 — Operator registration | UNASSESSED | Seek legal advice on notification requirement | MEDIUM |
| Kazakhstan | Law on Personal Data — Written consent | UNVERIFIED | Verify consent screen against Article 9 requirements | MEDIUM |
| Global | PCI-DSS — Full PAN risk from custom regex | THEORETICAL — default rules are safe | Add UI warning about card digit capture limits | MEDIUM |
| EU | PSD2 — AISP classification | LIKELY NOT APPLICABLE | Clarify in privacy policy: no bank API connection | LOW |
| EU | ePrivacy Directive | COMPLIANT — no in-transit interception | Monitor ePrivacy Regulation progress | LOW |
| Russia | FZ-149 — Technical security | COMPLIANT — Android sandbox | No action required | LOW |
| Russia | Bank of Russia — Financial service classification | NOT APPLICABLE | No action required | LOW |
| Global | PCI-DSS — CHD scope | NOT IN SCOPE — tail digits only | No registration required; add documentation note | LOW |

---

## 10. Privacy Policy Requirements

The following is the minimum content required to comply with the intersection of GDPR (EU), FZ-152 (Russia), Georgian Personal Data Protection Law, and Google Play Financial Services Policy. Meeting this baseline also substantially addresses Kazakhstan, India DPDP, and South Korean PIPA requirements.

### 10.1 Every data element collected

The privacy policy must enumerate:

| Data element | Source | Stored where |
|---|---|---|
| Transaction amount | Bank SMS body, parsed by regex | On-device Room database (payments table) |
| Transaction currency | Bank SMS body, parsed by regex | On-device Room database (payments table) |
| Card tail digits (last 4) | Bank SMS body, parsed by regex | On-device Room database (payments table) |
| Merchant name | Bank SMS body, parsed by regex | On-device Room database (payments table) |
| Transaction timestamp | Bank SMS body, parsed by regex | On-device Room database (payments table) |
| Account balance at transaction time | Bank SMS body, parsed by regex | On-device Room database (payments table) |
| Sender address (bank short code) | SMS metadata | On-device Room database (payments table, senders table) |
| Income details (amount, source, currency) | Bank SMS body, parsed by regex | On-device Room database (incomes table) |
| Message deduplication hash | SHA-256 of `sender::body` | On-device Room database (messageHash column) |
| User-defined rules and categories | User input | On-device Room database (rules, categories tables) |
| Terms acceptance status | User action | On-device SharedPreferences (app_terms.xml) |
| App configuration state | User input | On-device SharedPreferences |

### 10.2 Purpose of processing

- **Primary purpose:** Personal financial tracking — allowing the user to monitor their own bank transaction history by parsing SMS messages sent by their bank.
- **No secondary purposes:** Data is not used for advertising, analytics, profiling for third parties, or any purpose other than personal finance tracking.

### 10.3 Storage location

All data is stored exclusively on the user's device in the app's private storage directory. **Exception (current gap):** On Android 12+ devices, if Android Auto Backup is not properly restricted, the database may be uploaded to Google Drive as part of Android's system backup. (This gap must be fixed before this statement can be made without qualification.)

After the `data_extraction_rules.xml` fix: "All data is stored exclusively on the user's device. No data is transmitted to any server operated by the app developer."

### 10.4 Retention period (gap)

The app currently has no data retention limit. Payment records accumulate indefinitely. The privacy policy must either:

(a) State the retention period honestly and commit to a deletion schedule, or
(b) State that data is retained until the user manually deletes it, and provide clear instructions on how to delete.

Option (b) requires a data deletion feature to exist. Option (a) requires implementing automated deletion.

**This is a compliance gap.** The privacy policy cannot truthfully state a retention period without the corresponding technical implementation.

### 10.5 Data controller contact

The privacy policy must identify:

- **Data controller name:** The individual or legal entity responsible for data processing (the developer or their company).
- **Contact email or address:** An address where data subjects can send requests (access, erasure, portability, objection).
- **EU GDPR representative** (if developer is outside EU and EU users are targeted): Name and contact of the EU representative.

This information does not currently exist in the app or in any document in the repository.

### 10.6 User rights

The privacy policy must describe the following rights and how to exercise each:

- **Right of access:** Users can request a copy of all data held about them. Currently addressable via the export features.
- **Right to erasure:** Users can request deletion of all their data. **Not currently implemented.**
- **Right to portability:** Users can receive data in machine-readable format. Partially implemented (CSV/JSON export).
- **Right to withdraw consent:** Users can withdraw consent to processing. **Not currently implemented** — withdrawing consent should trigger data deletion.
- **Right to object:** Users can object to processing; the app must cease processing and offer deletion.
- **Right to lodge a complaint:** Users can complain to the supervisory authority in their jurisdiction (DPA in EU, PDPS in Georgia, Roskomnadzor in Russia, etc.).

### 10.7 Consent withdrawal mechanism

The privacy policy must explain how to withdraw consent. Without a technical withdrawal mechanism in the app, the only available instruction is to uninstall the app and manually clear its data via Android settings. This is not a legally adequate withdrawal mechanism under GDPR, Georgian law, or PIPA — it is not as easy as giving consent (which is a single tap on a dialog).

**Required feature:** A "Delete all my data and withdraw consent" function accessible from Settings, which clears the database, all SharedPreferences, and confirms to the user that their data has been erased.

### 10.8 Third-party sharing policy

Currently, **no data is shared with third parties by the app itself.** The privacy policy should state:

- The app does not share personal data with any third party.
- The app does not use advertising SDKs or analytics SDKs.
- **Exception — Android Auto Backup:** On devices running Android 12 or later, Android's operating system may include app data in device backups transmitted to Google's servers. The app is configured to exclude its database from these backups. [Include this only after the `data_extraction_rules.xml` fix is applied.]
- **Bug Report feature:** The Bug Report feature, when the user selects "Include payments data," generates a JSON attachment of payment records that the user may share via any app they choose (email, messaging, etc.). The developer receives this data only if the user explicitly sends it to a developer-controlled address. The privacy policy should disclose this.

---

## Appendix: Technical Items Directly Affecting Compliance

The following code-level issues from the post-fix code review (`docs/reports/code_review_post_fix_2026-03-17.md`) have direct legal/compliance implications. They are listed here for traceability.

| Code Review Finding | Legal Implication | Jurisdiction |
|---|---|---|
| REM-1 / NEW-2: `data_extraction_rules.xml` empty | Database backed up to Google Drive on API 31+. Violates data localization (Russia, Kazakhstan) and GDPR Article 32 security. | EU, Russia, Georgia, Kazakhstan, South Korea |
| NEW-1: `SmsProcessingService` logs SMS body in production | SMS content visible in logcat on any device with `READ_LOGS` permission or ADB access. Violates GDPR Article 32 security obligation. | EU, all jurisdictions |
| REM-2: `isMinifyEnabled = false` | APK not obfuscated; reverse engineering of regex patterns and sender data is trivial. Weakens GDPR Article 32 "appropriate technical measures." | EU, all jurisdictions |
| No data deletion feature | Violates right to erasure (GDPR Article 17, Georgian law, PIPA Article 36, DPDP Act Section 12). | EU, Georgia, South Korea, India |
| No retention policy | Violates storage limitation principle (GDPR Article 5, DPDP Act Section 8, PIPA). | EU, India, South Korea |
| Terms dialog — content unverified | May not satisfy Article 13 transparency requirements. | EU, Georgia |
| No data export covering all tables | Portability right (GDPR Article 20) only partially satisfied. | EU |

---

*Document prepared: 2026-03-17. Review against current regulatory texts before each distribution decision. Regulations change; this document reflects the state of law as of early 2026.*
