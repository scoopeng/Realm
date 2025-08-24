# Data Validation Checklist - Post Compact

## Export File Validation

### Basic Metrics
- [ ] Row count: Should be 573,874 rows (including header)
- [ ] Column count: Should be 67 columns
- [ ] File size: Should be ~500-520 MB
- [ ] Processing time: Should be under 60 seconds

### Column Presence Check
Run this Python script to verify all expected columns:
```python
import csv

export_file = "output/agentclients_full_[TIMESTAMP].csv"
expected_cols = 67

with open(export_file, 'r', encoding='utf-8') as f:
    reader = csv.reader(f)
    header = next(reader)
    print(f"Column count: {len(header)}")
    print(f"Expected: {expected_cols}")
    assert len(header) == expected_cols, f"Column mismatch!"
```

## Field Coverage Validation

### Address Fields (12 fields)
Expected coverage percentages from test run:
- [ ] Client Address State: ~25.0% coverage (143,297 rows)
- [ ] Client Address City: ~24.7% coverage (141,675 rows)
- [ ] Client Address Street Address: ~16.3% coverage (93,290 rows)
- [ ] Client Address Display Address: ~14.9% coverage (85,734 rows)
- [ ] Client Address Housenumber: ~14.9% coverage (85,482 rows)
- [ ] Client Address Streetname: ~14.8% coverage (84,805 rows)
- [ ] Client Address Streetname Suffix: ~13.8% coverage (79,015 rows)
- [ ] Client Address Streetname Prefix: ~3.3% coverage (18,844 rows)
- [ ] Client Address Country: ~1.8% coverage (10,163 rows)
- [ ] Client Address Streetname Direction: ~1.1% coverage (6,143 rows)
- [ ] Client Address Postal Code: ~0.9% coverage (4,989 rows)
- [ ] Client Address Unit: ~0.2% coverage (1,388 rows)

### Owner Field Expansion
- [ ] Realm Data Owner Agent: Should have ObjectId values (24 char hex strings)
- [ ] Realm Data Owner Agent Full Name: ~99.3% coverage (569,591 rows)
- [ ] Realm Data Owner Team: Should have ObjectId values  
- [ ] Realm Data Owner Team Name: ~10.5% coverage (60,069 rows)

### High Coverage Fields (Should be >90%)
- [ ] Client: 100% (573,874 rows)
- [ ] Full Name: 100% (573,874 rows)
- [ ] Client Name First Name: ~99.9%
- [ ] Client Name Full Name: ~99.9%
- [ ] Realm Data Owner Agent: ~99.8%

## Data Quality Checks

### Sample Data Verification
Check first 10 rows for:
- [ ] ObjectIds are 24-character hex strings where expected
- [ ] Names are human-readable text
- [ ] Addresses have proper formatting
- [ ] No excessive escaping or encoding issues
- [ ] Date fields are properly formatted

### Relationship Integrity
- [ ] Client field contains valid ObjectIds
- [ ] Owner Agent/Team fields contain ObjectIds (not names)
- [ ] Expanded fields match their base ObjectId fields

### Array Field Handling
- [ ] Agents field: Comma-separated list of names
- [ ] Realm Data Lifestyles: Comma-separated values
- [ ] Realm Data Tags: Properly formatted lists

## Performance Validation

### Export Speed
- [ ] Should process at 8,000-12,000 docs/sec
- [ ] No hanging or timeouts
- [ ] Memory usage stays under 2GB

### Cache Verification
Check logs for:
- [ ] "Caching collection people (622006 documents)"
- [ ] "Cached 5 collections for relationships"
- [ ] No "too large, using lazy loading" for people collection

## Python Validation Script

Save and run this comprehensive validation:

```python
import csv
import json
from collections import Counter

def validate_export(filename):
    print("=== EXPORT VALIDATION ===\n")
    
    with open(filename, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        
        # Track statistics
        row_count = 0
        field_coverage = Counter()
        objectid_fields = {}
        
        for row in reader:
            row_count += 1
            
            # Count non-empty fields
            for field, value in row.items():
                if value and value.strip():
                    field_coverage[field] += 1
                    
                    # Check ObjectId format for specific fields
                    if field in ["Client", "Realm Data Owner Agent", "Realm Data Owner Team"]:
                        if field not in objectid_fields:
                            objectid_fields[field] = {"valid": 0, "invalid": 0}
                        
                        # Check if it looks like an ObjectId
                        if len(value) == 24 and all(c in '0123456789abcdef' for c in value):
                            objectid_fields[field]["valid"] += 1
                        else:
                            objectid_fields[field]["invalid"] += 1
        
        print(f"Total rows: {row_count:,}")
        print(f"Total fields: {len(field_coverage)}")
        
        # Check critical fields
        print("\n=== CRITICAL FIELD VALIDATION ===")
        critical_fields = [
            ("Client", 573874, 100.0),
            ("Client Address City", 141675, 24.7),
            ("Realm Data Owner Agent Full Name", 569591, 99.3),
            ("Realm Data Owner Team Name", 60069, 10.5)
        ]
        
        for field, expected_count, expected_pct in critical_fields:
            actual_count = field_coverage.get(field, 0)
            actual_pct = (actual_count / row_count) * 100
            status = "✅" if abs(actual_pct - expected_pct) < 2 else "⚠️"
            print(f"{status} {field}: {actual_count:,} ({actual_pct:.1f}%) - Expected: {expected_count:,} ({expected_pct:.1f}%)")
        
        # Check ObjectId fields
        print("\n=== OBJECTID FIELD VALIDATION ===")
        for field, counts in objectid_fields.items():
            total = counts["valid"] + counts["invalid"]
            valid_pct = (counts["valid"] / total * 100) if total > 0 else 0
            status = "✅" if valid_pct > 95 else "❌"
            print(f"{status} {field}: {counts['valid']:,} valid ObjectIds ({valid_pct:.1f}%)")
            if counts["invalid"] > 0:
                print(f"    ⚠️  {counts['invalid']:,} invalid values")
        
        # Summary
        print("\n=== VALIDATION SUMMARY ===")
        if row_count == 573874:
            print("✅ Row count matches expected")
        else:
            print(f"❌ Row count mismatch: {row_count:,} vs 573,874 expected")
        
        address_fields = sum(1 for f in field_coverage if "Address" in f)
        owner_fields = sum(1 for f in field_coverage if "Owner" in f)
        
        print(f"✅ Address fields with data: {address_fields}")
        print(f"✅ Owner fields with data: {owner_fields}")
        
        return row_count == 573874

# Run validation
if __name__ == "__main__":
    import sys
    filename = sys.argv[1] if len(sys.argv) > 1 else "output/agentclients_full_20250824_185345.csv"
    validate_export(filename)
```

## Post-Validation Actions

If any checks fail:
1. Check `/tmp/discovery.log` for discovery phase issues
2. Check export logs for expansion errors
3. Verify cache manager is working (people collection fully cached)
4. Ensure ObjectIds aren't being pre-resolved for fields with expansions
5. Verify nested field extraction is working for `realmData.*` fields

## Success Criteria

The export is considered successful when:
- ✅ All 573,874 rows are exported
- ✅ All 67 columns are present
- ✅ Address fields have expected coverage (±2%)
- ✅ Owner fields are properly expanded (>95% coverage for agents)
- ✅ No fields are completely empty
- ✅ ObjectId fields contain valid 24-char hex strings
- ✅ Export completes in under 60 seconds