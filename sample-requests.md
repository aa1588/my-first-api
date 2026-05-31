# Sample Requests for FL HO6 Deductible Change Tracking

This document contains sample Postman requests to test the FL HO6 deductible adjustment INFO message feature.

**Expected INFO Message:** `"Info: Hurricane and/or Non Hurricane deductible updated to meet Underwriting Guidelines."`

---

## /quote/add Endpoint

### Requests That WILL Return the INFO Message (5 scenarios)

These requests have `windHailExclusion: false` AND the deductible values will be adjusted by the system.

---

#### 1. Hurricane Deductible Adjusted (1000 → 2500)
**Scenario:** CovA + CovC = 400,000, Hurricane input = 1000, adjusted to 2500

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-hurricane-adjusted-001",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "123 Test Street",
        "city": "Miami",
        "state": "FL",
        "zip": "33101",
        "county": "Miami-Dade"
      },
      "occupancy": "Owner",
      "yearBuilt": 2010,
      "constructionType": "Masonry"
    },
    "coverages": {
      "covA": 200000,
      "covC": 200000
    },
    "deductibles": {
      "hurricane": 1000,
      "aop": 2500,
      "windHailExclusion": false
    },
    "insured": {
      "firstName": "John",
      "lastName": "Doe",
      "email": "john.doe@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** INFO message returned because Hurricane deductible 1000 is adjusted to 2500 (CovA+CovC=400000 triggers adjustment).

---

#### 2. Hurricane Deductible Adjusted (500 → 5000)
**Scenario:** CovA + CovC = 600,000, Hurricane input = 500, adjusted to 5000

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-hurricane-adjusted-002",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "456 Beach Ave",
        "city": "Fort Lauderdale",
        "state": "FL",
        "zip": "33301",
        "county": "Broward"
      },
      "occupancy": "Owner",
      "yearBuilt": 2015,
      "constructionType": "Masonry"
    },
    "coverages": {
      "covA": 300000,
      "covC": 300000
    },
    "deductibles": {
      "hurricane": 500,
      "aop": 5000,
      "windHailExclusion": false
    },
    "insured": {
      "firstName": "Jane",
      "lastName": "Smith",
      "email": "jane.smith@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** INFO message returned because Hurricane deductible 500 is adjusted to 5000 (CovA+CovC=600000 triggers adjustment).

---

#### 3. AOP Deductible Adjusted (2500 → 500)
**Scenario:** Hurricane = 500, AOP input = 2500, adjusted to 500

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-aop-adjusted-001",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "789 Palm Drive",
        "city": "Tampa",
        "state": "FL",
        "zip": "33602",
        "county": "Hillsborough"
      },
      "occupancy": "Owner",
      "yearBuilt": 2018,
      "constructionType": "Frame"
    },
    "coverages": {
      "covA": 20000,
      "covC": 20000
    },
    "deductibles": {
      "hurricane": 500,
      "aop": 2500,
      "windHailExclusion": false
    },
    "insured": {
      "firstName": "Bob",
      "lastName": "Johnson",
      "email": "bob.johnson@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** INFO message returned because AOP deductible 2500 is adjusted to 500 (Hurricane=500 limits AOP to 500).

---

#### 4. Both Hurricane and AOP Adjusted
**Scenario:** CovA + CovC = 600,000, Hurricane input = 1000 → 5000, AOP input = 100 → 5000

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-both-adjusted-001",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "321 Ocean Blvd",
        "city": "Jacksonville",
        "state": "FL",
        "zip": "32202",
        "county": "Duval"
      },
      "occupancy": "Owner",
      "yearBuilt": 2020,
      "constructionType": "Masonry"
    },
    "coverages": {
      "covA": 300000,
      "covC": 300000
    },
    "deductibles": {
      "hurricane": 1000,
      "aop": 100,
      "windHailExclusion": false
    },
    "insured": {
      "firstName": "Alice",
      "lastName": "Williams",
      "email": "alice.williams@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** INFO message returned (only once) because BOTH Hurricane (1000→5000) and AOP (100→5000) are adjusted.

---

#### 5. AOP Deductible Adjusted (5000 → 1000)
**Scenario:** Hurricane = 1000, AOP input = 5000, adjusted to 1000

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-aop-adjusted-002",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "555 Bay Street",
        "city": "Orlando",
        "state": "FL",
        "zip": "32801",
        "county": "Orange"
      },
      "occupancy": "Owner",
      "yearBuilt": 2012,
      "constructionType": "Masonry"
    },
    "coverages": {
      "covA": 20000,
      "covC": 20000
    },
    "deductibles": {
      "hurricane": 1000,
      "aop": 5000,
      "windHailExclusion": false
    },
    "insured": {
      "firstName": "Charlie",
      "lastName": "Brown",
      "email": "charlie.brown@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** INFO message returned because AOP deductible 5000 is adjusted to 1000 (Hurricane=1000 limits AOP to ≤1000).

---

### Requests That Will NOT Return the INFO Message (5 scenarios)

---

#### 6. Wind Hail Exclusion = true (No message even if adjusted)
**Scenario:** windHailExclusion=true, deductibles may be adjusted but message suppressed

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-windhail-exclusion-001",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "999 Inland Road",
        "city": "Gainesville",
        "state": "FL",
        "zip": "32601",
        "county": "Alachua"
      },
      "occupancy": "Owner",
      "yearBuilt": 2008,
      "constructionType": "Masonry"
    },
    "coverages": {
      "covA": 200000,
      "covC": 200000
    },
    "deductibles": {
      "hurricane": 200,
      "aop": 2500,
      "windHailExclusion": true
    },
    "insured": {
      "firstName": "David",
      "lastName": "Miller",
      "email": "david.miller@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** NO INFO message because `windHailExclusion: true` (message is suppressed per requirements).

---

#### 7. No Adjustment Needed - Hurricane Matches Output
**Scenario:** CovA + CovC = 40,000, Hurricane input = 500, output = 500 (no change)

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-no-change-001",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "111 Small Condo Lane",
        "city": "Pensacola",
        "state": "FL",
        "zip": "32501",
        "county": "Escambia"
      },
      "occupancy": "Owner",
      "yearBuilt": 2005,
      "constructionType": "Frame"
    },
    "coverages": {
      "covA": 20000,
      "covC": 20000
    },
    "deductibles": {
      "hurricane": 500,
      "aop": 500,
      "windHailExclusion": false
    },
    "insured": {
      "firstName": "Eve",
      "lastName": "Davis",
      "email": "eve.davis@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** NO INFO message because Hurricane input (500) equals output (500), and AOP input (500) equals output (500).

---

#### 8. No Adjustment Needed - Both Match Output
**Scenario:** CovA + CovC = 300,000, Hurricane = 2500, AOP = 2500 (both unchanged)

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-no-change-002",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "222 Stable Court",
        "city": "Tallahassee",
        "state": "FL",
        "zip": "32301",
        "county": "Leon"
      },
      "occupancy": "Owner",
      "yearBuilt": 2016,
      "constructionType": "Masonry"
    },
    "coverages": {
      "covA": 150000,
      "covC": 150000
    },
    "deductibles": {
      "hurricane": 2500,
      "aop": 2500,
      "windHailExclusion": false
    },
    "insured": {
      "firstName": "Frank",
      "lastName": "Wilson",
      "email": "frank.wilson@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** NO INFO message because both Hurricane (2500) and AOP (2500) inputs equal their outputs.

---

#### 9. Deductibles Not Provided (null) - Defaults Applied
**Scenario:** Hurricane and AOP not provided, system uses defaults (no "change" from user input)

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-null-deductibles-001",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "333 Default Drive",
        "city": "St. Petersburg",
        "state": "FL",
        "zip": "33701",
        "county": "Pinellas"
      },
      "occupancy": "Owner",
      "yearBuilt": 2019,
      "constructionType": "Masonry"
    },
    "coverages": {
      "covA": 100000,
      "covC": 100000
    },
    "deductibles": {
      "windHailExclusion": false
    },
    "insured": {
      "firstName": "Grace",
      "lastName": "Lee",
      "email": "grace.lee@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** NO INFO message because no user-provided deductible values were changed (null inputs are not tracked as "changed").

---

#### 10. No Adjustment - High Coverage with Matching Deductibles
**Scenario:** CovA + CovC = 600,000, Hurricane = 5000, AOP = 5000 (both match expected output)

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-no-change-003",
  "quoteRequest": {
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "effectiveDate": "2025-01-15",
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "444 Luxury Tower",
        "city": "Boca Raton",
        "state": "FL",
        "zip": "33432",
        "county": "Palm Beach"
      },
      "occupancy": "Owner",
      "yearBuilt": 2022,
      "constructionType": "Masonry"
    },
    "coverages": {
      "covA": 300000,
      "covC": 300000
    },
    "deductibles": {
      "hurricane": 5000,
      "aop": 5000,
      "windHailExclusion": false
    },
    "insured": {
      "firstName": "Henry",
      "lastName": "Taylor",
      "email": "henry.taylor@test.com"
    },
    "discounts": {
      "protectiveDevice": false
    },
    "optionalCoverages": {
      "mandatoryMediationArbitration": true
    }
  }
}
```

**Expected Result:** NO INFO message because Hurricane (5000) and AOP (5000) inputs equal their outputs for this coverage level.

---

---

## /bind Endpoint (or equivalent application update endpoint)

The bind endpoint typically uses an existing transaction number. Below are similar scenarios for binding.

### Requests That WILL Return the INFO Message (5 scenarios)

---

#### 11. Bind - Hurricane Adjusted (1000 → 2500)

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-hurricane-adjusted-001",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 200000,
      "covC": 200000
    },
    "deductibles": {
      "hurricane": 1000,
      "aop": 2500,
      "windHailExclusion": false
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "123 Bind Street",
        "city": "Miami",
        "state": "FL",
        "zip": "33101",
        "county": "Miami-Dade"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** INFO message returned because Hurricane 1000 adjusted to 2500.

---

#### 12. Bind - Hurricane Adjusted (2500 → 5000)

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-hurricane-adjusted-002",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 300000,
      "covC": 300000
    },
    "deductibles": {
      "hurricane": 2500,
      "aop": 5000,
      "windHailExclusion": false
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "456 Bind Avenue",
        "city": "Fort Lauderdale",
        "state": "FL",
        "zip": "33301",
        "county": "Broward"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** INFO message returned because Hurricane 2500 adjusted to 5000 (CovA+CovC=600000).

---

#### 13. Bind - AOP Adjusted (1000 → 500)

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-aop-adjusted-001",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 20000,
      "covC": 20000
    },
    "deductibles": {
      "hurricane": 500,
      "aop": 1000,
      "windHailExclusion": false
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "789 Bind Plaza",
        "city": "Tampa",
        "state": "FL",
        "zip": "33602",
        "county": "Hillsborough"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** INFO message returned because AOP 1000 adjusted to 500 (Hurricane=500 limits AOP).

---

#### 14. Bind - Both Deductibles Adjusted

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-both-adjusted-001",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 300000,
      "covC": 300000
    },
    "deductibles": {
      "hurricane": 500,
      "aop": 100,
      "windHailExclusion": false
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "321 Bind Court",
        "city": "Jacksonville",
        "state": "FL",
        "zip": "32202",
        "county": "Duval"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** INFO message returned (once) because both Hurricane (500→5000) and AOP (100→5000) are adjusted.

---

#### 15. Bind - AOP Adjusted (2500 → 1000)

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-aop-adjusted-002",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 20000,
      "covC": 20000
    },
    "deductibles": {
      "hurricane": 1000,
      "aop": 2500,
      "windHailExclusion": false
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "555 Bind Lane",
        "city": "Orlando",
        "state": "FL",
        "zip": "32801",
        "county": "Orange"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** INFO message returned because AOP 2500 adjusted to 1000.

---

### Requests That Will NOT Return the INFO Message (5 scenarios)

---

#### 16. Bind - Wind Hail Exclusion = true

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-windhail-001",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 200000,
      "covC": 200000
    },
    "deductibles": {
      "hurricane": 200,
      "aop": 2500,
      "windHailExclusion": true
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "999 Bind Road",
        "city": "Gainesville",
        "state": "FL",
        "zip": "32601",
        "county": "Alachua"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** NO INFO message because `windHailExclusion: true`.

---

#### 17. Bind - No Adjustment Needed

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-no-change-001",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 20000,
      "covC": 20000
    },
    "deductibles": {
      "hurricane": 500,
      "aop": 500,
      "windHailExclusion": false
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "111 Bind Way",
        "city": "Pensacola",
        "state": "FL",
        "zip": "32501",
        "county": "Escambia"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** NO INFO message because deductibles match expected output.

---

#### 18. Bind - Both Deductibles Match Output

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-no-change-002",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 150000,
      "covC": 150000
    },
    "deductibles": {
      "hurricane": 2500,
      "aop": 2500,
      "windHailExclusion": false
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "222 Bind Circle",
        "city": "Tallahassee",
        "state": "FL",
        "zip": "32301",
        "county": "Leon"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** NO INFO message because both deductibles match expected output.

---

#### 19. Bind - Null Deductibles (Defaults Applied)

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-null-deductibles-001",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 100000,
      "covC": 100000
    },
    "deductibles": {
      "windHailExclusion": false
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "333 Bind Boulevard",
        "city": "St. Petersburg",
        "state": "FL",
        "zip": "33701",
        "county": "Pinellas"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** NO INFO message because null inputs are not tracked as "changed".

---

#### 20. Bind - High Coverage with Matching Deductibles

```json
{
  "customerId": "YOUR_CUSTOMER_ID",
  "requestId": "test-bind-no-change-003",
  "quoteRequest": {
    "existingTransactionNumber": "YOUR_QUOTE_NUMBER",
    "productType": "HO6",
    "producer": {
      "producerCode": "YOUR_PRODUCER_CODE"
    },
    "workflowFlags": {
      "bindApplication": true
    },
    "coverages": {
      "covA": 300000,
      "covC": 300000
    },
    "deductibles": {
      "hurricane": 5000,
      "aop": 5000,
      "windHailExclusion": false
    },
    "insuredProperty": {
      "dwellingType": "Condominium",
      "location": {
        "address1": "444 Bind Tower",
        "city": "Boca Raton",
        "state": "FL",
        "zip": "33432",
        "county": "Palm Beach"
      },
      "occupancy": "Owner"
    }
  }
}
```

**Expected Result:** NO INFO message because both Hurricane (5000) and AOP (5000) match expected output for this coverage level.

---

## Summary Table

| # | Endpoint | Scenario | Hurricane Input | AOP Input | CovA+CovC | WindHailExclusion | Expected INFO Message |
|---|----------|----------|-----------------|-----------|-----------|-------------------|----------------------|
| 1 | /quote/add | Hurricane adjusted | 1000 | 2500 | 400,000 | false | YES |
| 2 | /quote/add | Hurricane adjusted | 500 | 5000 | 600,000 | false | YES |
| 3 | /quote/add | AOP adjusted | 500 | 2500 | 40,000 | false | YES |
| 4 | /quote/add | Both adjusted | 1000 | 100 | 600,000 | false | YES |
| 5 | /quote/add | AOP adjusted | 1000 | 5000 | 40,000 | false | YES |
| 6 | /quote/add | WindHail exclusion | 200 | 2500 | 400,000 | **true** | NO |
| 7 | /quote/add | No change | 500 | 500 | 40,000 | false | NO |
| 8 | /quote/add | No change | 2500 | 2500 | 300,000 | false | NO |
| 9 | /quote/add | Null inputs | null | null | 200,000 | false | NO |
| 10 | /quote/add | No change | 5000 | 5000 | 600,000 | false | NO |
| 11 | /bind | Hurricane adjusted | 1000 | 2500 | 400,000 | false | YES |
| 12 | /bind | Hurricane adjusted | 2500 | 5000 | 600,000 | false | YES |
| 13 | /bind | AOP adjusted | 500 | 1000 | 40,000 | false | YES |
| 14 | /bind | Both adjusted | 500 | 100 | 600,000 | false | YES |
| 15 | /bind | AOP adjusted | 1000 | 2500 | 40,000 | false | YES |
| 16 | /bind | WindHail exclusion | 200 | 2500 | 400,000 | **true** | NO |
| 17 | /bind | No change | 500 | 500 | 40,000 | false | NO |
| 18 | /bind | No change | 2500 | 2500 | 300,000 | false | NO |
| 19 | /bind | Null inputs | null | null | 200,000 | false | NO |
| 20 | /bind | No change | 5000 | 5000 | 600,000 | false | NO |

---

## Notes

1. Replace `YOUR_CUSTOMER_ID`, `YOUR_PRODUCER_CODE`, and `YOUR_QUOTE_NUMBER` with actual values from your environment.

2. The expected INFO message in the response will look like:
```json
{
  "dtoValidationError": [
    {
      "name": "INFO",
      "msg": "Info: Hurricane and/or Non Hurricane deductible updated to meet Underwriting Guidelines."
    }
  ]
}
```

3. For bind requests, you need to first create a quote using `/quote/add` and then use the returned quote number as `existingTransactionNumber`.

4. The INFO message appears alongside a valid premium and does not prevent policy issuance.


---

