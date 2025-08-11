# Quality Assessment Plan for MongoDB Export Production Run

## Overview
This document provides a comprehensive quality assessment plan for validating the production export results. Execute these steps after running the full production export to ensure data quality, completeness, and accuracy.

## Prerequisites
- Complete production export has been run for target collection(s)
- Output CSV files are available in `./output/` directory
- Configuration files are available in `./config/` directory
- Python 3.x is installed for validation scripts

## Phase 1: File and Structure Validation

### 1.1 Verify Output Files Exist
```bash
# Check that all expected files were generated
ls -lah ./output/listings_full_*.csv
ls -lah ./output/listings_summary.json
ls -lah ./config/listings_fields.json
ls -lah ./config/listings_expansion_audit.txt

# Record file sizes and timestamps
echo "=== File Information ===" > qa_report.txt
ls -lah ./output/listings_full_*.csv >> qa_report.txt
```

### 1.2 Validate CSV Structure
```python
# Save as: validate_csv_structure.py
import csv
import json
import sys

def validate_csv_structure(csv_file, config_file):
    # Load configuration
    with open(config_file, 'r') as f:
        config = json.load(f)
    
    # Get expected fields
    expected_fields = [
        field['businessName'] 
        for field in config['includedFields'] 
        if field.get('include', False)
    ]
    
    # Check CSV headers
    with open(csv_file, 'r') as f:
        reader = csv.DictReader(f)
        actual_headers = reader.fieldnames
        
        # Validate header count
        print(f"Expected fields: {len(expected_fields)}")
        print(f"Actual headers: {len(actual_headers)}")
        
        # Check for missing/extra headers
        missing = set(expected_fields) - set(actual_headers)
        extra = set(actual_headers) - set(expected_fields)
        
        if missing:
            print(f"WARNING: Missing headers: {missing}")
        if extra:
            print(f"WARNING: Extra headers: {extra}")
        
        return len(missing) == 0 and len(extra) == 0

# Run: python3 validate_csv_structure.py output/listings_full_*.csv config/listings_fields.json
```

## Phase 2: Data Quality Checks

### 2.1 Field Coverage Analysis
```python
# Save as: field_coverage_analysis.py
import csv
import json
from collections import defaultdict

def analyze_field_coverage(csv_file, sample_size=None):
    field_stats = defaultdict(lambda: {'total': 0, 'non_empty': 0, 'unique_values': set()})
    row_count = 0
    
    with open(csv_file, 'r') as f:
        reader = csv.DictReader(f)
        
        for row in reader:
            row_count += 1
            if sample_size and row_count > sample_size:
                break
                
            for field, value in row.items():
                field_stats[field]['total'] += 1
                if value and value.strip():
                    field_stats[field]['non_empty'] += 1
                    if len(field_stats[field]['unique_values']) < 100:
                        field_stats[field]['unique_values'].add(value)
    
    # Generate report
    print(f"\n=== FIELD COVERAGE REPORT ===")
    print(f"Total rows analyzed: {row_count}")
    print("\nField Coverage Statistics:")
    print("-" * 80)
    
    # Sort by coverage percentage
    sorted_fields = sorted(
        field_stats.items(), 
        key=lambda x: x[1]['non_empty'] / x[1]['total'] if x[1]['total'] > 0 else 0,
        reverse=True
    )
    
    for field, stats in sorted_fields:
        coverage = (stats['non_empty'] / stats['total'] * 100) if stats['total'] > 0 else 0
        unique_count = len(stats['unique_values'])
        print(f"{field:40} {coverage:6.2f}% ({stats['non_empty']:6}/{stats['total']}) [{unique_count} unique]")
    
    # Identify potential issues
    print("\n=== POTENTIAL ISSUES ===")
    sparse_fields = [f for f, s in field_stats.items() 
                     if s['total'] > 0 and (s['non_empty'] / s['total']) < 0.1]
    if sparse_fields:
        print(f"Sparse fields (<10% coverage) found: {sparse_fields}")
        print("ACTION: Verify these should be included based on business requirements")
    
    empty_fields = [f for f, s in field_stats.items() if s['non_empty'] == 0]
    if empty_fields:
        print(f"Completely empty fields found: {empty_fields}")
        print("ACTION: These fields should be investigated and possibly excluded")
    
    return field_stats

# Run: python3 field_coverage_analysis.py output/listings_full_*.csv
```

### 2.2 Expanded Field Validation
```python
# Save as: validate_expanded_fields.py
import csv
import re

def validate_expanded_fields(csv_file, sample_size=1000):
    expanded_field_patterns = {
        'Property City': r'^[A-Za-z\s\-\.]+$',
        'Property Full Address': r'.+,.+,\d{5}',
        'Property Zipcode': r'^\d{5}(-\d{4})?$',
        'Listing Brokerage Agents': r'.+',
    }
    
    validation_results = defaultdict(lambda: {'valid': 0, 'invalid': 0, 'empty': 0, 'samples': []})
    
    with open(csv_file, 'r') as f:
        reader = csv.DictReader(f)
        
        for i, row in enumerate(reader):
            if i >= sample_size:
                break
                
            for field, pattern in expanded_field_patterns.items():
                if field in row:
                    value = row[field]
                    
                    if not value or not value.strip():
                        validation_results[field]['empty'] += 1
                    elif re.match(pattern, value):
                        validation_results[field]['valid'] += 1
                        if len(validation_results[field]['samples']) < 5:
                            validation_results[field]['samples'].append(value)
                    else:
                        validation_results[field]['invalid'] += 1
                        print(f"Invalid {field}: {value}")
    
    # Report results
    print("\n=== EXPANDED FIELD VALIDATION ===")
    for field, results in validation_results.items():
        total = results['valid'] + results['invalid'] + results['empty']
        print(f"\n{field}:")
        print(f"  Valid: {results['valid']} ({results['valid']/total*100:.1f}%)")
        print(f"  Invalid: {results['invalid']} ({results['invalid']/total*100:.1f}%)")
        print(f"  Empty: {results['empty']} ({results['empty']/total*100:.1f}%)")
        print(f"  Samples: {results['samples'][:3]}")

# Run: python3 validate_expanded_fields.py output/listings_full_*.csv
```

## Phase 3: Data Integrity Checks

### 3.1 Cross-Reference Validation
```python
# Save as: cross_reference_validation.py
import csv
from collections import defaultdict

def validate_cross_references(csv_file, sample_size=5000):
    """
    Validate that related fields are consistent
    """
    issues = []
    
    with open(csv_file, 'r') as f:
        reader = csv.DictReader(f)
        
        for i, row in enumerate(reader):
            if i >= sample_size:
                break
            
            # Check: If Property City exists, Property Zipcode should too
            if row.get('Property City') and not row.get('Property Zipcode'):
                issues.append(f"Row {i+2}: Has Property City but missing Property Zipcode")
            
            # Check: If price exists, it should be numeric
            if 'Price Amount' in row and row['Price Amount']:
                try:
                    float(row['Price Amount'])
                except ValueError:
                    issues.append(f"Row {i+2}: Invalid price format: {row['Price Amount']}")
            
            # Check: Bedrooms and Bathrooms should be numeric if present
            for field in ['Bedrooms', 'Bathrooms']:
                if field in row and row[field]:
                    try:
                        float(row[field])
                    except ValueError:
                        issues.append(f"Row {i+2}: Invalid {field} format: {row[field]}")
    
    # Report
    print("\n=== CROSS-REFERENCE VALIDATION ===")
    if issues:
        print(f"Found {len(issues)} issues:")
        for issue in issues[:20]:  # Show first 20 issues
            print(f"  - {issue}")
    else:
        print("✓ No cross-reference issues found")
    
    return issues

# Run: python3 cross_reference_validation.py output/listings_full_*.csv
```

### 3.2 Duplicate Detection
```python
# Save as: duplicate_detection.py
import csv
from collections import defaultdict

def detect_duplicates(csv_file):
    """
    Detect potential duplicate records based on key fields
    """
    seen_records = defaultdict(list)
    
    with open(csv_file, 'r') as f:
        reader = csv.DictReader(f)
        
        for i, row in enumerate(reader):
            # Create a key from identifying fields
            key_fields = ['MLS Number', 'Full Address', 'Property Full Address']
            key_values = []
            
            for field in key_fields:
                if field in row and row[field]:
                    key_values.append(row[field])
            
            if key_values:
                key = '|'.join(key_values)
                seen_records[key].append(i + 2)  # Row number (accounting for header)
    
    # Find duplicates
    duplicates = {k: v for k, v in seen_records.items() if len(v) > 1}
    
    print("\n=== DUPLICATE DETECTION ===")
    if duplicates:
        print(f"Found {len(duplicates)} potential duplicate groups:")
        for key, rows in list(duplicates.items())[:10]:
            print(f"  Key: {key[:100]}...")
            print(f"  Rows: {rows}")
    else:
        print("✓ No duplicates detected")
    
    return duplicates

# Run: python3 duplicate_detection.py output/listings_full_*.csv
```

## Phase 4: Performance and Statistics Validation

### 4.1 Summary Statistics Validation
```python
# Save as: validate_summary_stats.py
import json
import csv

def validate_summary_statistics(csv_file, summary_file):
    """
    Compare actual CSV statistics with the generated summary
    """
    # Load summary
    with open(summary_file, 'r') as f:
        summary = json.load(f)
    
    # Calculate actual stats from CSV
    actual_stats = {}
    row_count = 0
    
    with open(csv_file, 'r') as f:
        reader = csv.DictReader(f)
        field_values = defaultdict(list)
        
        for row in reader:
            row_count += 1
            for field, value in row.items():
                if value and value.strip():
                    field_values[field].append(value)
    
    # Compare counts
    print("\n=== SUMMARY STATISTICS VALIDATION ===")
    print(f"Summary reports: {summary.get('totalDocuments', 'N/A')} documents")
    print(f"Actual CSV has: {row_count} rows")
    
    if abs(summary.get('totalDocuments', 0) - row_count) > 1:
        print("WARNING: Document count mismatch!")
    
    # Validate processing time is reasonable
    processing_time = summary.get('processingTimeMs', 0)
    if processing_time > 0:
        docs_per_sec = row_count / (processing_time / 1000)
        print(f"Processing speed: {docs_per_sec:.0f} docs/sec")
        if docs_per_sec < 1000:
            print("WARNING: Processing speed seems slow")
    
    return row_count == summary.get('totalDocuments', 0)

# Run: python3 validate_summary_stats.py output/listings_full_*.csv output/listings_summary.json
```

## Phase 5: Business Logic Validation

### 5.1 Business Rules Validation
```python
# Save as: validate_business_rules.py
import csv

def validate_business_rules(csv_file, sample_size=10000):
    """
    Validate specific business rules
    """
    violations = []
    
    with open(csv_file, 'r') as f:
        reader = csv.DictReader(f)
        
        for i, row in enumerate(reader):
            if i >= sample_size:
                break
            
            # Rule 1: Year Built should be between 1800 and 2025
            if 'Year Built' in row and row['Year Built']:
                try:
                    year = int(row['Year Built'])
                    if year < 1800 or year > 2025:
                        violations.append(f"Row {i+2}: Invalid Year Built: {year}")
                except ValueError:
                    pass
            
            # Rule 2: Bedrooms should be reasonable (0-20)
            if 'Bedrooms' in row and row['Bedrooms']:
                try:
                    bedrooms = int(float(row['Bedrooms']))
                    if bedrooms < 0 or bedrooms > 20:
                        violations.append(f"Row {i+2}: Unusual Bedrooms count: {bedrooms}")
                except ValueError:
                    pass
            
            # Rule 3: Status should be from known list
            if 'Status' in row and row['Status']:
                valid_statuses = ['Active', 'Pending', 'Sold', 'Closed', 'Withdrawn', 'Expired']
                if row['Status'] not in valid_statuses:
                    violations.append(f"Row {i+2}: Unknown status: {row['Status']}")
    
    print("\n=== BUSINESS RULES VALIDATION ===")
    if violations:
        print(f"Found {len(violations)} violations:")
        for v in violations[:20]:
            print(f"  - {v}")
    else:
        print("✓ All business rules passed")
    
    return violations

# Run: python3 validate_business_rules.py output/listings_full_*.csv
```

## Phase 6: Final Report Generation

### 6.1 Generate Comprehensive QA Report
```bash
# Create comprehensive QA report
echo "=== MONGODB EXPORT QA REPORT ===" > qa_final_report.txt
echo "Date: $(date)" >> qa_final_report.txt
echo "" >> qa_final_report.txt

# File information
echo "=== FILE INFORMATION ===" >> qa_final_report.txt
ls -lah ./output/listings_full_*.csv >> qa_final_report.txt
echo "" >> qa_final_report.txt

# Row counts
echo "=== ROW COUNTS ===" >> qa_final_report.txt
wc -l ./output/listings_full_*.csv >> qa_final_report.txt
echo "" >> qa_final_report.txt

# Field counts
echo "=== FIELD COUNTS ===" >> qa_final_report.txt
head -1 ./output/listings_full_*.csv | tr ',' '\n' | wc -l >> qa_final_report.txt
echo "" >> qa_final_report.txt

# Run all Python validations
python3 validate_csv_structure.py output/listings_full_*.csv config/listings_fields.json >> qa_final_report.txt
python3 field_coverage_analysis.py output/listings_full_*.csv >> qa_final_report.txt
python3 validate_expanded_fields.py output/listings_full_*.csv >> qa_final_report.txt
python3 cross_reference_validation.py output/listings_full_*.csv >> qa_final_report.txt
python3 duplicate_detection.py output/listings_full_*.csv >> qa_final_report.txt
python3 validate_summary_stats.py output/listings_full_*.csv output/listings_summary.json >> qa_final_report.txt
python3 validate_business_rules.py output/listings_full_*.csv >> qa_final_report.txt

echo "QA Report saved to: qa_final_report.txt"
```

## Quality Metrics and Pass Criteria

### Must Pass (Critical):
- [ ] CSV file exists and is readable
- [ ] Header count matches configuration
- [ ] No completely empty columns (0% coverage)
- [ ] Row count matches summary report (±1)
- [ ] No malformed CSV structure

### Should Pass (Important):
- [ ] Sparse field coverage > 10% (as designed)
- [ ] Expanded fields contain valid data (>80% valid)
- [ ] No duplicate records on key fields
- [ ] Processing speed > 1000 docs/sec
- [ ] Business rules validation > 95% pass rate

### Nice to Have:
- [ ] All numeric fields properly formatted
- [ ] Date fields in consistent format
- [ ] No truncated values
- [ ] Consistent array field formatting

## Execution Instructions

1. Save all Python scripts in the project directory
2. Run the production export first
3. Execute each validation phase in order
4. Review the final QA report
5. Address any critical issues before using the data

## Troubleshooting Guide

### Issue: Empty columns found
**Action**: Check if sparse threshold needs adjustment or if data is missing

### Issue: Slow processing speed
**Action**: Review heap settings, check for memory issues

### Issue: Invalid expanded field values
**Action**: Verify RelationExpander mappings and collection references

### Issue: Duplicate records
**Action**: Check MongoDB source for duplicates or review deduplication logic

### Issue: Business rule violations
**Action**: Review with business team, may need to adjust validation rules

## Notes
- Run QA on a sample first (10K rows) for quick validation
- Full QA on complete export may take 10-15 minutes
- Save QA reports for audit trail
- Re-run after any configuration changes