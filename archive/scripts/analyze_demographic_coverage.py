#!/usr/bin/env python3
"""
Analyze demographic field coverage in the latest export.
Focus on the 30 supplemental demographic fields from personexternaldatas.
"""

import csv
from pathlib import Path
from collections import defaultdict

def analyze_demographic_coverage():
    """Analyze coverage of demographic fields from personexternaldatas."""
    
    csv_file = Path("/home/ubuntu/IdeaProjects/Realm/output/agentclients_full_20250824_225632.csv")
    
    # The 30 supplemental demographic fields we added
    demographic_fields = [
        "Client Income Level",
        "Client Household Income",
        "Client Household Income Midpoint",
        "Client Household Net Worth",
        "Client Net Worth Midpoint",
        "Client Education Level",
        "Client Education Ordinal",
        "Client Age",
        "Client Birth Year",
        "Client Gender Male",
        "Client Marital Status",
        "Client Home Owner",
        "Client Home Value",
        "Client Area Median Home Value",
        "Client Dwelling Type",
        "Client Length of Residence",
        "Client Number of Children",
        "Client Number of Adults",
        "Client Household Size",
        "Client Number of Vehicles",
        "Client Luxury Lifestyle",
        "Client Family Affinity",
        "Client Health Interest",
        "Client Technology Interest",
        "Client Outdoors Interest",
        "Client Travel Interest",
        "Client Golf Interest",
        "Client Charity Interest",
        "Client Urbanicity",
        "Client Data Source"
    ]
    
    print("=" * 80)
    print("DEMOGRAPHIC FIELD COVERAGE ANALYSIS")
    print("Analyzing 30 supplemental fields from personexternaldatas")
    print("=" * 80)
    
    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        
        # Initialize counters
        total_rows = 0
        field_counts = defaultdict(int)
        any_demographic_count = 0
        all_demographic_count = 0
        
        # Process each row
        for row in reader:
            total_rows += 1
            
            # Track which demographic fields have data in this row
            fields_with_data = 0
            
            for field in demographic_fields:
                if field in row and row[field] and row[field].strip():
                    field_counts[field] += 1
                    fields_with_data += 1
            
            if fields_with_data > 0:
                any_demographic_count += 1
            if fields_with_data == len(demographic_fields):
                all_demographic_count += 1
            
            # Progress indicator
            if total_rows % 50000 == 0:
                print(f"  Processed {total_rows:,} rows...")
    
    print(f"\nTotal rows: {total_rows:,}")
    print(f"\n📊 OVERALL DEMOGRAPHIC COVERAGE:")
    print(f"  Rows with ANY demographic data: {any_demographic_count:,} ({any_demographic_count/total_rows*100:.1f}%)")
    print(f"  Rows with ALL demographic fields: {all_demographic_count:,} ({all_demographic_count/total_rows*100:.1f}%)")
    
    print(f"\n📈 INDIVIDUAL FIELD COVERAGE (30 supplemental fields):")
    
    # Sort fields by coverage
    field_coverage = [(field, count, count/total_rows*100) for field, count in field_counts.items()]
    field_coverage.sort(key=lambda x: x[2], reverse=True)
    
    # Group by coverage levels
    high_coverage = [f for f in field_coverage if f[2] >= 50]
    medium_coverage = [f for f in field_coverage if 20 <= f[2] < 50]
    low_coverage = [f for f in field_coverage if 0 < f[2] < 20]
    zero_coverage = [f for f in demographic_fields if f not in field_counts]
    
    if high_coverage:
        print(f"\n  ✅ HIGH COVERAGE (≥50%):")
        for field, count, pct in high_coverage:
            print(f"    {field:40} {pct:6.2f}% ({count:,} rows)")
    
    if medium_coverage:
        print(f"\n  ⚠️  MEDIUM COVERAGE (20-50%):")
        for field, count, pct in medium_coverage:
            print(f"    {field:40} {pct:6.2f}% ({count:,} rows)")
    
    if low_coverage:
        print(f"\n  ❌ LOW COVERAGE (<20%):")
        for field, count, pct in low_coverage:
            print(f"    {field:40} {pct:6.2f}% ({count:,} rows)")
    
    if zero_coverage:
        print(f"\n  ⛔ ZERO COVERAGE (0%):")
        for field in zero_coverage:
            print(f"    {field}")
    
    # Calculate average coverage
    total_coverage = sum(count for _, count, _ in field_coverage)
    avg_coverage = (total_coverage / (len(demographic_fields) * total_rows)) * 100
    
    print(f"\n📊 SUMMARY:")
    print(f"  Average field coverage: {avg_coverage:.2f}%")
    print(f"  Fields with data: {len(field_coverage)}/{len(demographic_fields)}")
    print(f"  Fields with >50% coverage: {len(high_coverage)}")
    print(f"  Fields with 20-50% coverage: {len(medium_coverage)}")
    print(f"  Fields with <20% coverage: {len(low_coverage)}")
    print(f"  Fields with 0% coverage: {len(zero_coverage)}")
    
    print("\n" + "=" * 80)
    print("COMPARISON WITH PREVIOUS RESULTS:")
    print("=" * 80)
    print("\nBEFORE MERGE FIX (Session 4):")
    print("  - Average demographic coverage: ~10%")
    print("  - Most fields had 6-10% coverage")
    print("  - Issue: Only used FIRST personexternaldata record per person")
    
    print(f"\nAFTER MERGE FIX (Current):")
    print(f"  - Average demographic coverage: {avg_coverage:.2f}%")
    if avg_coverage > 60:
        print(f"  - ✅ SUCCESS! {avg_coverage/10:.1f}x improvement!")
        print("  - Merge fix is working correctly")
    elif avg_coverage > 30:
        print(f"  - ⚠️  PARTIAL SUCCESS: {avg_coverage/10:.1f}x improvement")
        print("  - Better than before but not at expected 70%")
    else:
        print(f"  - ❌ Limited improvement: {avg_coverage/10:.1f}x")
        print("  - Need to investigate further")

if __name__ == "__main__":
    analyze_demographic_coverage()