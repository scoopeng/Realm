#!/usr/bin/env python3
"""
Analyze the latest agentclients export to verify data quality improvements.
Focus on demographic field coverage after the merging fix.
"""

import csv
import json
from datetime import datetime
from pathlib import Path
from collections import defaultdict

def analyze_csv(filepath):
    """Analyze CSV file for data quality metrics."""
    print(f"Analyzing: {filepath}")
    print("=" * 80)
    
    with open(filepath, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        
        # Initialize counters
        total_rows = 0
        field_stats = defaultdict(lambda: {'non_null': 0, 'unique_values': set()})
        
        # Process each row
        for row in reader:
            total_rows += 1
            for field, value in row.items():
                if value and value.strip():  # Non-empty value
                    field_stats[field]['non_null'] += 1
                    # Only track unique values for first 1000 rows to save memory
                    if total_rows <= 1000:
                        field_stats[field]['unique_values'].add(value)
            
            # Progress indicator
            if total_rows % 50000 == 0:
                print(f"  Processed {total_rows:,} rows...")
    
    print(f"\n📊 BASIC STATISTICS:")
    print(f"  Total Rows: {total_rows:,}")
    print(f"  Total Columns: {len(field_stats)}")
    
    # Categorize and analyze fields
    demographic_fields = []
    address_fields = []
    owner_fields = []
    client_fields = []
    agent_fields = []
    empty_fields = []
    
    for field, stats in field_stats.items():
        coverage_pct = (stats['non_null'] / total_rows) * 100 if total_rows > 0 else 0
        unique_count = len(stats['unique_values'])
        
        # Categorize fields
        field_lower = field.lower()
        if coverage_pct == 0:
            empty_fields.append(field)
        elif any(term in field_lower for term in [
            'income', 'education', 'age', 'home', 'marital', 'gender',
            'occupation', 'ethnicity', 'dwelling', 'political', 'interest', 'children'
        ]):
            demographic_fields.append((field, coverage_pct, stats['non_null']))
        elif any(term in field_lower for term in ['address', 'city', 'state', 'zip', 'street', 'country']):
            address_fields.append((field, coverage_pct, stats['non_null']))
        elif 'owneragent' in field_lower or 'ownerteam' in field_lower:
            owner_fields.append((field, coverage_pct, stats['non_null']))
        elif field.startswith('client.') or field.startswith('client_'):
            client_fields.append((field, coverage_pct, stats['non_null']))
        elif field.startswith('agent.') or field.startswith('agent_'):
            agent_fields.append((field, coverage_pct, stats['non_null']))
    
    # Print demographic fields analysis (THE KEY METRIC)
    print(f"\n🎯 DEMOGRAPHIC FIELDS (Key Focus - Should be ~70% coverage after fix):")
    print(f"  Found {len(demographic_fields)} demographic fields")
    if demographic_fields:
        demographic_fields.sort(key=lambda x: x[1], reverse=True)
        
        # Calculate average coverage
        total_coverage = sum(coverage for _, coverage, _ in demographic_fields)
        avg_demo_coverage = total_coverage / len(demographic_fields)
        
        print(f"\n  📈 AVERAGE DEMOGRAPHIC COVERAGE: {avg_demo_coverage:.2f}%")
        print(f"      (Was ~10% before merge fix, expected ~70% after)")
        
        print(f"\n  Top 15 Demographic Fields:")
        for field, coverage, count in demographic_fields[:15]:
            status = "✅" if coverage > 50 else "⚠️" if coverage > 20 else "❌"
            print(f"    {status} {field:55} {coverage:6.2f}% ({count:,} rows)")
        
        # Check specific key demographics
        print(f"\n  Key Demographics Detail:")
        key_demographics = ['Income_HH', 'Education_Input_Individual', 'Age_Actual', 
                           'Home_Market_Value', 'Marital_Status', 'Gender_Input_Individual']
        for key in key_demographics:
            matching = [(f, c, n) for f, c, n in demographic_fields if key in f]
            if matching:
                for field, coverage, count in matching:
                    status = "✅" if coverage > 50 else "⚠️" if coverage > 20 else "❌"
                    print(f"    {status} {field:55} {coverage:6.2f}% ({count:,} rows)")
    
    # Print address fields analysis
    print(f"\n📍 ADDRESS FIELDS:")
    print(f"  Found {len(address_fields)} address fields")
    if address_fields:
        address_fields.sort(key=lambda x: x[1], reverse=True)
        avg_addr_coverage = sum(c for _, c, _ in address_fields) / len(address_fields)
        print(f"  Average Coverage: {avg_addr_coverage:.2f}%")
        for field, coverage, count in address_fields[:10]:
            print(f"    {field:55} {coverage:6.2f}% ({count:,} rows)")
    
    # Print owner fields analysis
    print(f"\n👤 OWNER FIELDS (ownerAgent/ownerTeam expansion):")
    print(f"  Found {len(owner_fields)} owner fields")
    if owner_fields:
        owner_fields.sort(key=lambda x: x[1], reverse=True)
        for field, coverage, count in owner_fields:
            status = "✅" if coverage > 90 else "⚠️" if coverage > 50 else "❌"
            print(f"    {status} {field:55} {coverage:6.2f}% ({count:,} rows)")
    
    # Print summary statistics
    print(f"\n📊 FIELD COVERAGE SUMMARY:")
    print(f"  Client fields: {len(client_fields)} fields")
    print(f"  Agent fields: {len(agent_fields)} fields")
    print(f"  Empty fields: {len(empty_fields)} fields (0% coverage)")
    
    if empty_fields:
        print(f"\n  Empty fields (first 10):")
        for field in empty_fields[:10]:
            print(f"    - {field}")
    
    # Overall summary
    fields_with_data = len([f for f in field_stats.values() if f['non_null'] > 0])
    fields_50pct = len([f for f, s in field_stats.items() 
                       if (s['non_null'] / total_rows * 100) >= 50])
    fields_90pct = len([f for f, s in field_stats.items() 
                       if (s['non_null'] / total_rows * 100) >= 90])
    
    print(f"\n📈 OVERALL DATA QUALITY:")
    print(f"  Fields with ANY data: {fields_with_data}/{len(field_stats)} ({fields_with_data/len(field_stats)*100:.1f}%)")
    print(f"  Fields with >50% coverage: {fields_50pct}/{len(field_stats)}")
    print(f"  Fields with >90% coverage: {fields_90pct}/{len(field_stats)}")
    
    return {
        'total_rows': total_rows,
        'total_columns': len(field_stats),
        'demographic_fields_count': len(demographic_fields),
        'avg_demographic_coverage': sum(c for _, c, _ in demographic_fields) / len(demographic_fields) if demographic_fields else 0,
        'address_fields_count': len(address_fields),
        'owner_fields_count': len(owner_fields),
        'empty_fields_count': len(empty_fields),
        'fields_with_data': fields_with_data
    }

def compare_with_previous():
    """Compare with previous analysis results."""
    print("\n" + "=" * 80)
    print("📊 COMPARISON WITH PREVIOUS EXPORTS")
    print("=" * 80)
    
    print(f"\nBEFORE MERGE FIX (Session 4):")
    print(f"  - Demographic coverage: ~10% (6-10% per field)")
    print(f"  - Issue: Only picked FIRST personexternaldata record per person")
    print(f"  - Problem: Each person has MULTIPLE records from different sources")
    
    print(f"\nAFTER MERGE FIX (Expected):")
    print(f"  - Demographic coverage: ~70% (matching MongoDB PROD)")
    print(f"  - Solution: Merge ALL personexternaldata records per person")
    print(f"  - Keep non-null values from all sources (AA, AID, Experian, etc.)")

def main():
    """Main analysis function."""
    # Find the latest export file
    output_dir = Path("/home/ubuntu/IdeaProjects/Realm/output")
    csv_file = output_dir / "agentclients_full_20250824_225632.csv"
    
    if not csv_file.exists():
        print(f"ERROR: File not found: {csv_file}")
        # List available files
        print("\nAvailable CSV files:")
        for f in sorted(output_dir.glob("agentclients_full_*.csv")):
            print(f"  - {f.name}")
        return
    
    # Analyze the CSV
    stats = analyze_csv(csv_file)
    
    # Compare with previous
    compare_with_previous()
    
    # Final verdict
    print("\n" + "=" * 80)
    print("🎯 FINAL VERDICT:")
    print("=" * 80)
    
    demo_coverage = stats['avg_demographic_coverage']
    
    if demo_coverage > 60:
        print(f"✅ SUCCESS! Demographic coverage significantly improved!")
        print(f"   Achieved {demo_coverage:.1f}% average coverage (was ~10%)")
        print(f"   This is a {demo_coverage/10:.1f}x improvement!")
        print("   The merge fix is working correctly.")
    elif demo_coverage > 30:
        print(f"⚠️  PARTIAL SUCCESS: Demographic coverage improved but not to expected levels")
        print(f"   Achieved {demo_coverage:.1f}% average coverage (was ~10%)")
        print(f"   This is a {demo_coverage/10:.1f}x improvement")
        print("   Expected ~70% - may need further investigation")
    else:
        print(f"❌ ISSUE: Demographic coverage did not improve as expected")
        print(f"   Only {demo_coverage:.1f}% average coverage (was ~10%)")
        if demo_coverage > 10:
            print(f"   Minor improvement of {demo_coverage/10:.1f}x")
        print("   The merge fix may not be working correctly")
    
    # Save summary
    summary = {
        'timestamp': datetime.now().isoformat(),
        'file': str(csv_file),
        'stats': stats,
        'demographic_improvement': f"{demo_coverage:.1f}% (was ~10%)",
        'improvement_factor': f"{demo_coverage/10:.1f}x" if demo_coverage > 0 else "N/A"
    }
    
    summary_file = output_dir / "latest_export_analysis.json"
    with open(summary_file, 'w') as f:
        json.dump(summary, f, indent=2)
    
    print(f"\n📄 Analysis saved to: {summary_file}")

if __name__ == "__main__":
    main()