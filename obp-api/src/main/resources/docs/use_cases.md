# Open Bank Project - Use Cases

This document provides detailed examples of real-world use cases implemented using the Open Bank Project API.

**Version:** 1.0.0
**Last Updated:** January 2025
**License:** Copyright TESOBE GmbH 2025 - AGPL V3

---

## Table of Contents

1. [Variable Recurring Payments (VRP)](#1-variable-recurring-payments-vrp)

---

## 1. Variable Recurring Payments (VRP)

**Overview:** VRPs enable authorized applications to make multiple payments to a beneficiary over time with varying amounts, subject to pre-defined limits.

Variable Recurring Payments are ideal for use cases such as:
- Subscription services with variable billing amounts
- Utility payments (electricity, water, gas)
- Loan repayments with varying installments
- Recurring vendor payments
- Automated savings transfers

### Key Concepts

- **Consent-Based Authorization**: Account holders grant permission once for multiple future payments
- **Counterparty Limits**: Constraints on payment amounts and frequencies
- **Custom Views**: Automatically generated views control access to the payment account
- **Beneficiary (Counterparty)**: The recipient of the variable recurring payments

### VRP Components

1. **Consent Request** - Initial request to set up VRP
2. **Custom View** - Auto-generated view with specific permissions (e.g., `_vrp-9d429899-24f5-42c8`)
3. **Counterparty** - The beneficiary/recipient of payments
4. **Counterparty Limits** - Rules constraining payment amounts and frequencies
5. **Consent** - Final authorization allowing the application to initiate payments

### VRP Workflow

```
┌─────────────────────────────────────────────────────────────────┐
│  1. Application creates VRP Consent Request                     │
│     POST /consumer/vrp-consent-requests                         │
│     (Specifies: from_account, to_account, limits)               │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. OBP automatically creates:                                  │
│     - Custom View (e.g., _vrp-xxx)                              │
│     - Counterparty (beneficiary)                                │
│     - Counterparty Limits                                       │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. Account Holder finalizes consent                            │
│     POST /consumer/consent-requests/CONSENT_REQUEST_ID/         │
│          IMPLICIT|EMAIL|SMS/consents                            │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. Application uses consent to create Transaction Requests     │
│     POST /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/            │
│          transaction-request-types/COUNTERPARTY/                │
│          transaction-requests                                   │
│     (Multiple payments within limits)                           │
└─────────────────────────────────────────────────────────────────┘
```

### Creating a VRP Consent Request

```bash
POST /obp/v5.1.0/consumer/vrp-consent-requests
Authorization: Bearer CLIENT_ACCESS_TOKEN
Content-Type: application/json

{
  "from_account": {
    "bank_routing": {
      "scheme": "OBP",
      "address": "gh.29.uk"
    },
    "account_routing": {
      "scheme": "AccountNumber",
      "address": "123456789"
    },
    "branch_routing": {
      "scheme": "BranchNumber",
      "address": "001"
    }
  },
  "to_account": {
    "counterparty_name": "Utility Company Ltd",
    "bank_routing": {
      "scheme": "OBP",
      "address": "gh.29.uk"
    },
    "account_routing": {
      "scheme": "IBAN",
      "address": "GB29NWBK60161331926819"
    },
    "branch_routing": {
      "scheme": "BranchNumber",
      "address": "002"
    },
    "limit": {
      "currency": "EUR",
      "max_single_amount": "500",
      "max_monthly_amount": "2000",
      "max_number_of_monthly_transactions": 12,
      "max_yearly_amount": "20000",
      "max_number_of_yearly_transactions": 100,
      "max_total_amount": "50000",
      "max_number_of_transactions": 200
    }
  },
  "time_to_live": 31536000,
  "valid_from": "2024-01-01T00:00:00Z"
}
```

**Response:**

```json
{
  "consent_request_id": "cr-8d5e9f2a-1b3c-4d6e-7f8a-9b0c1d2e3f4a",
  "payload": {
    "from_account": { ... },
    "to_account": { ... }
  },
  "consumer_id": "123"
}
```

### Finalizing the Consent

After creating the VRP consent request, the account holder must finalize it:

```bash
# Using IMPLICIT SCA (no challenge required)
POST /obp/v5.1.0/consumer/consent-requests/CONSENT_REQUEST_ID/IMPLICIT/consents
Authorization: Bearer USER_ACCESS_TOKEN

# Using EMAIL SCA (challenge sent via email)
POST /obp/v5.1.0/consumer/consent-requests/CONSENT_REQUEST_ID/EMAIL/consents
Authorization: Bearer USER_ACCESS_TOKEN

# Using SMS SCA (challenge sent via SMS)
POST /obp/v5.1.0/consumer/consent-requests/CONSENT_REQUEST_ID/SMS/consents
Authorization: Bearer USER_ACCESS_TOKEN
```

### Counterparty Limits Explained

Counterparty Limits control how much and how often payments can be made:

| Limit Type | Description | Example |
|------------|-------------|---------|
| `max_single_amount` | Maximum amount for a single payment | €500 per transaction |
| `max_monthly_amount` | Total amount allowed per month | €2,000 per month |
| `max_number_of_monthly_transactions` | Maximum transactions per month | 12 transactions/month |
| `max_yearly_amount` | Total amount allowed per year | €20,000 per year |
| `max_number_of_yearly_transactions` | Maximum transactions per year | 100 transactions/year |
| `max_total_amount` | Total amount across all transactions | €50,000 lifetime |
| `max_number_of_transactions` | Total number of all transactions | 200 lifetime |

### Making Payments with VRP Consent

Once the consent is active, the application can create transaction requests:

```bash
POST /obp/v4.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/_vrp-xxx/
     transaction-request-types/COUNTERPARTY/transaction-requests
Authorization: Bearer USER_ACCESS_TOKEN
Content-Type: application/json

{
  "to": {
    "counterparty_id": "counterparty-uuid"
  },
  "value": {
    "currency": "EUR",
    "amount": "125.50"
  },
  "description": "Monthly utility bill - January 2024"
}
```

### Limit Enforcement

OBP automatically enforces all limits. If a transaction would exceed any limit, it is rejected:

```json
{
  "error": "OBP-40037: Counterparty Limit Exceeded. Monthly limit of EUR 2000 would be exceeded."
}
```

### Manual VRP Setup (Alternative Approach)

If you prefer to set up VRP manually instead of using the automated endpoint:

```bash
# 1. Create a custom view
POST /obp/v5.1.0/banks/BANK_ID/accounts/ACCOUNT_ID/views
{
  "name": "_vrp-utility-payments",
  "description": "VRP view for utility payments",
  "is_public": false,
  "allowed_permissions": [
    "can_add_transaction_request_to_beneficiary",
    "can_get_counterparty",
    "can_see_transaction_requests"
  ]
}

# 2. Create a counterparty on that view
POST /obp/v4.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/_vrp-utility-payments/counterparties
{
  "name": "Utility Company Ltd",
  "other_account_routing_scheme": "IBAN",
  "other_account_routing_address": "GB29NWBK60161331926819",
  "other_bank_routing_scheme": "BIC",
  "other_bank_routing_address": "NWBKGB2L"
}

# 3. Add limits to the counterparty
POST /obp/v5.1.0/banks/BANK_ID/accounts/ACCOUNT_ID/_vrp-utility-payments/
     counterparties/COUNTERPARTY_ID/limits
{
  "currency": "EUR",
  "max_single_amount": "500",
  "max_monthly_amount": "2000",
  "max_number_of_monthly_transactions": 12
}

# 4. Create a consent for the view
POST /obp/v5.1.0/my/consents/IMPLICIT
{
  "everything": false,
  "account_access": [{
    "account_id": "ACCOUNT_ID",
    "view_id": "_vrp-utility-payments"
  }],
  "time_to_live": 31536000
}
```

### Monitoring VRP Usage

View current limits and usage:

```bash
# Get counterparty limits
GET /obp/v5.1.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/
    counterparties/COUNTERPARTY_ID/limits

# Response shows current usage
{
  "counterparty_limit_id": "limit-uuid",
  "currency": "EUR",
  "max_single_amount": "500",
  "max_monthly_amount": "2000",
  "current_monthly_amount": "875.50",
  "max_number_of_monthly_transactions": 12,
  "current_number_of_monthly_transactions": 3,
  ...
}
```

### Revoking VRP Consent

Account holders can revoke consent at any time:

```bash
DELETE /obp/v5.1.0/banks/BANK_ID/consents/CONSENT_ID
Authorization: Bearer USER_ACCESS_TOKEN
```

### Security Considerations

- VRP consents use JWT tokens with embedded view permissions
- Limits are enforced at the API level before forwarding to the core banking system
- All VRP transactions are logged and auditable
- Account holders can modify or delete counterparty limits at any time
- Custom views created for VRP have minimal permissions (only what's needed for payments)

### VRP vs Traditional Payment Initiation

| Feature | VRP | Traditional PIS |
|---------|-----|-----------------|
| **Number of Payments** | Multiple (within limits) | Single payment per consent |
| **Amount Flexibility** | Variable amounts | Fixed amount |
| **Consent Duration** | Long-lived (months/years) | Short-lived (typically 90 days) |
| **Use Case** | Recurring variable payments | One-time payments |
| **Setup Complexity** | Higher (limits, counterparties) | Lower (simple payment details) |
| **Beneficiary** | Single fixed counterparty | Any beneficiary |

### Configuration

```properties
# Maximum time-to-live for VRP consents (seconds)
consents.max_time_to_live=31536000

# Skip SCA for trusted applications (optional)
skip_consent_sca_for_consumer_id_pairs=[{
  "grantor_consumer_id": "user-app-id",
  "grantee_consumer_id": "vrp-app-id"
}]
```

### Related API Endpoints

- `POST /obp/v5.1.0/consumer/vrp-consent-requests` - Create VRP consent request
- `POST /obp/v5.1.0/consumer/consent-requests/{CONSENT_REQUEST_ID}/{SCA_METHOD}/consents` - Finalize consent
- `GET /obp/v5.1.0/consumer/consent-requests/{CONSENT_REQUEST_ID}` - Get consent request details
- `POST /obp/v4.0.0/banks/{BANK_ID}/accounts/{ACCOUNT_ID}/{VIEW_ID}/transaction-request-types/COUNTERPARTY/transaction-requests` - Create payment
- `GET /obp/v5.1.0/banks/{BANK_ID}/accounts/{ACCOUNT_ID}/{VIEW_ID}/counterparties/{COUNTERPARTY_ID}/limits` - Get limits
- `PUT /obp/v5.1.0/banks/{BANK_ID}/accounts/{ACCOUNT_ID}/{VIEW_ID}/counterparties/{COUNTERPARTY_ID}/limits/{LIMIT_ID}` - Update limits
- `DELETE /obp/v5.1.0/banks/{BANK_ID}/consents/{CONSENT_ID}` - Revoke consent

---

## Additional Use Cases

This document will be expanded with additional use cases including:
- Account Aggregation
- Payment Initiation Services (PIS)
- Account Information Services (AIS)
- Confirmation of Funds (CoF)
- Dynamic Consent Management
- Multi-Bank Operations

---

**For More Information:**

- Main Documentation: [introductory_system_documentation.md](introductory_system_documentation.md)
- API Reference: https://apiexplorer.openbankproject.com
- Source Code: https://github.com/OpenBankProject/OBP-API
- Community: https://openbankproject.com