#!/usr/bin/env python3
"""
Analyze the latest agentclients export to verify data quality improvements.
Focus on demographic field coverage after the merging fix.
"""

import pandas as pd
import json
from datetime import datetime
from pathlib import Path

def analyze_csv(filepath):
    """Analyze CSV file for data quality metrics."""
    print(f"Analyzing: {filepath}")
    print("=" * 80)
    
    # Read CSV
    df = pd.read_csv(filepath, low_memory=False)
    
    # Basic statistics
    total_rows = len(df)
    total_cols = len(df.columns)
    
    print(f"\n📊 BASIC STATISTICS:")
    print(f"  Total Rows: {total_rows:,}")
    print(f"  Total Columns: {total_cols}")
    
    # Analyze each column
    column_stats = []
    
    # Group columns by category
    demographic_fields = []
    address_fields = []
    owner_fields = []
    client_fields = []
    agent_fields = []
    realm_fields = []
    other_fields = []
    empty_fields = []
    
    for col in df.columns:
        non_null_count = df[col].notna().sum()
        coverage_pct = (non_null_count / total_rows) * 100
        unique_values = df[col].nunique()
        
        stats = {
            'column': col,
            'non_null': non_null_count,
            'coverage_pct': coverage_pct,
            'unique_values': unique_values
        }
        column_stats.append(stats)
        
        # Categorize fields
        col_lower = col.lower()
        if coverage_pct == 0:
            empty_fields.append(col)
        elif 'income' in col_lower or 'education' in col_lower or 'age' in col_lower or \
             'home' in col_lower or 'marital' in col_lower or 'gender' in col_lower or \
             'occupation' in col_lower or 'ethnicity' in col_lower or 'dwelling' in col_lower or \
             'political' in col_lower or 'interest' in col_lower or 'children' in col_lower:
            demographic_fields.append((col, coverage_pct))
        elif 'address' in col_lower or 'city' in col_lower or 'state' in col_lower or \
             'zip' in col_lower or 'street' in col_lower or 'country' in col_lower:
            address_fields.append((col, coverage_pct))
        elif 'owneragent' in col_lower or 'ownerteam' in col_lower:
            owner_fields.append((col, coverage_pct))
        elif col.startswith('client.') or col.startswith('client_'):
            client_fields.append((col, coverage_pct))
        elif col.startswith('agent.') or col.startswith('agent_'):
            agent_fields.append((col, coverage_pct))
        elif col.startswith('realmData.'):
            realm_fields.append((col, coverage_pct))
        else:
            other_fields.append((col, coverage_pct))
    
    # Print demographic fields analysis (THE KEY METRIC)
    print(f"\n🎯 DEMOGRAPHIC FIELDS (Key Focus - Should be ~70% coverage):")
    print(f"  Found {len(demographic_fields)} demographic fields")
    if demographic_fields:
        demographic_fields.sort(key=lambda x: x[1], reverse=True)
        total_demo_coverage = 0
        for field, coverage in demographic_fields[:10]:  # Show top 10
            print(f"    {field:50} {coverage:6.2f}% coverage")
            total_demo_coverage += coverage
        avg_demo_coverage = total_demo_coverage / len(demographic_fields)
        print(f"\n  📈 Average Demographic Coverage: {avg_demo_coverage:.2f}%")
        
        # Check specific key demographics
        key_demographics = ['Income_HH', 'Education_Input_Individual', 'Age_Actual', 
                           'Home_Market_Value', 'Marital_Status', 'Gender_Input_Individual']
        print(f"\n  Key Demographics Detail:")
        for key in key_demographics:
            matching = [f for f in demographic_fields if key in f[0]]
            if matching:
                for field, coverage in matching:
                    print(f"    {field:50} {coverage:6.2f}% coverage")
    
    # Print address fields analysis
    print(f"\n📍 ADDRESS FIELDS:")
    print(f"  Found {len(address_fields)} address fields")
    if address_fields:
        address_fields.sort(key=lambda x: x[1], reverse=True)
        for field, coverage in address_fields[:10]:  # Show top 10
            print(f"    {field:50} {coverage:6.2f}% coverage")
        avg_addr_coverage = sum(c for _, c in address_fields) / len(address_fields)
        print(f"  Average Address Coverage: {avg_addr_coverage:.2f}%")
    
    # Print owner fields analysis
    print(f"\n👤 OWNER FIELDS (ownerAgent/ownerTeam expansion):")
    print(f"  Found {len(owner_fields)} owner fields")
    if owner_fields:
        owner_fields.sort(key=lambda x: x[1], reverse=True)
        for field, coverage in owner_fields:
            print(f"    {field:50} {coverage:6.2f}% coverage")
    
    # Print client fields summary
    print(f"\n👥 CLIENT FIELDS:")
    print(f"  Found {len(client_fields)} client fields")
    if client_fields:
        avg_client_coverage = sum(c for _, c in client_fields) / len(client_fields)
        print(f"  Average Coverage: {avg_client_coverage:.2f}%")
        # Show top 5
        client_fields.sort(key=lambda x: x[1], reverse=True)
        for field, coverage in client_fields[:5]:
            print(f"    {field:50} {coverage:6.2f}% coverage")
    
    # Print agent fields summary
    print(f"\n🏢 AGENT FIELDS:")
    print(f"  Found {len(agent_fields)} agent fields")
    if agent_fields:
        avg_agent_coverage = sum(c for _, c in agent_fields) / len(agent_fields)
        print(f"  Average Coverage: {avg_agent_coverage:.2f}%")
        # Show top 5
        agent_fields.sort(key=lambda x: x[1], reverse=True)
        for field, coverage in agent_fields[:5]:
            print(f"    {field:50} {coverage:6.2f}% coverage")
    
    # Print empty fields
    print(f"\n⚠️  EMPTY FIELDS (0% coverage):")
    print(f"  Found {len(empty_fields)} completely empty fields")
    if empty_fields:
        for field in empty_fields[:10]:  # Show first 10
            print(f"    - {field}")
        if len(empty_fields) > 10:
            print(f"    ... and {len(empty_fields) - 10} more")
    
    # Overall summary
    fields_with_data = [s for s in column_stats if s['coverage_pct'] > 0]
    fields_50pct = [s for s in column_stats if s['coverage_pct'] >= 50]
    fields_90pct = [s for s in column_stats if s['coverage_pct'] >= 90]
    
    print(f"\n📈 OVERALL DATA QUALITY:")
    print(f"  Fields with ANY data: {len(fields_with_data)}/{total_cols} ({len(fields_with_data)/total_cols*100:.1f}%)")
    print(f"  Fields with >50% coverage: {len(fields_50pct)}/{total_cols} ({len(fields_50pct)/total_cols*100:.1f}%)")
    print(f"  Fields with >90% coverage: {len(fields_90pct)}/{total_cols} ({len(fields_90pct)/total_cols*100:.1f}%)")
    
    return {
        'total_rows': total_rows,
        'total_columns': total_cols,
        'demographic_fields_count': len(demographic_fields),
        'avg_demographic_coverage': sum(c for _, c in demographic_fields) / len(demographic_fields) if demographic_fields else 0,
        'address_fields_count': len(address_fields),
        'owner_fields_count': len(owner_fields),
        'empty_fields_count': len(empty_fields),
        'fields_with_data': len(fields_with_data)
    }

def compare_with_previous():
    """Compare with previous analysis results."""
    print("\n" + "=" * 80)
    print("📊 COMPARISON WITH PREVIOUS EXPORT (before merge fix)")
    print("=" * 80)
    
    previous_stats = {
        'demographic_coverage': 10,  # Was ~10% before fix
        'address_coverage': 35,      # Was ~30-37%
        'owner_fields': 67,          # Had 67 fields with data
        'total_fields': 103          # Had 103 total fields
    }
    
    print(f"\nPREVIOUS (Session 4 - before merge fix):")
    print(f"  - Demographic coverage: ~{previous_stats['demographic_coverage']}%")
    print(f"  - Address coverage: ~{previous_stats['address_coverage']}%")
    print(f"  - Fields with data: {previous_stats['owner_fields']}")
    print(f"  - Total fields: {previous_stats['total_fields']}")
    
    print(f"\nEXPECTED AFTER FIX:")
    print(f"  - Demographic coverage: ~70% (7x improvement)")
    print(f"  - Address coverage: ~35% (same)")
    print(f"  - Owner fields: Should have data")
    print(f"  - Total fields: 103+")

def main():
    """Main analysis function."""
    # Find the latest export file
    output_dir = Path("/home/ubuntu/IdeaProjects/Realm/output")
    csv_file = output_dir / "agentclients_full_20250824_225632.csv"
    
    if not csv_file.exists():
        print(f"ERROR: File not found: {csv_file}")
        return
    
    # Analyze the CSV
    stats = analyze_csv(csv_file)
    
    # Compare with previous
    compare_with_previous()
    
    # Final verdict
    print("\n" + "=" * 80)
    print("🎯 FINAL VERDICT:")
    print("=" * 80)
    
    if stats['avg_demographic_coverage'] > 60:
        print("✅ SUCCESS! Demographic coverage significantly improved!")
        print(f"   Achieved {stats['avg_demographic_coverage']:.1f}% average coverage")
        print("   This confirms the merge fix is working correctly.")
    elif stats['avg_demographic_coverage'] > 30:
        print("⚠️  PARTIAL SUCCESS: Demographic coverage improved but not to expected levels")
        print(f"   Achieved {stats['avg_demographic_coverage']:.1f}% average coverage")
        print("   Expected ~70% but got better than the original 10%")
    else:
        print("❌ ISSUE: Demographic coverage did not improve as expected")
        print(f"   Only {stats['avg_demographic_coverage']:.1f}% average coverage")
        print("   The merge fix may not be working correctly")
    
    # Save summary
    summary = {
        'timestamp': datetime.now().isoformat(),
        'file': str(csv_file),
        'stats': stats,
        'demographic_improvement': f"{stats['avg_demographic_coverage']:.1f}% (was ~10%)"
    }
    
    summary_file = output_dir / "latest_export_analysis.json"
    with open(summary_file, 'w') as f:
        json.dump(summary, f, indent=2)
    
    print(f"\n📄 Analysis saved to: {summary_file}")

if __name__ == "__main__":
    main()