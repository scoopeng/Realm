#!/usr/bin/env python3
"""
Analyze agentclients_summary.json to identify fields that should be disabled.
Generates a comprehensive CSV report with actionable recommendations.
"""

import json
import csv
from typing import Dict, List, Tuple
import sys
from pathlib import Path

def load_summary(file_path: str) -> Dict:
    """Load the summary JSON file."""
    with open(file_path, 'r') as f:
        return json.load(f)

def categorize_field(field: Dict) -> Tuple[str, str]:
    """
    Categorize a field and provide recommendation.
    Returns (recommendation, reason)
    """
    name = field['fieldName']
    null_pct = field['nullPercentage']
    unique_values = field['uniqueValues']
    category = field['category']
    sample_values = field.get('sampleValues', [])
    
    # Rule 1: Completely empty fields (100% null)
    if null_pct >= 99.99:
        return ("DISABLE", "Completely empty (100% null)")
    
    # Rule 2: Single value fields (provide no information)
    if category == 'SINGLE_VALUE' or unique_values == 1:
        if sample_values:
            return ("DISABLE", f"Single value only: '{sample_values[0]}'")
        return ("DISABLE", "Single value - no variation")
    
    # Rule 3: Technical IDs that shouldn't be in export
    if name in ['Client', 'Realm Data Owner Agent', 'Realm Data Owner Team', 
                'Realm Data Owner Team Leader', 'Realm Data Owner Agent Person',
                'Realm Data Owner Agent Subscription']:
        return ("DISABLE", "Technical ID field - not business data")
    
    # Rule 4: Extremely sparse fields (>95% null) with low unique values
    if null_pct > 95:
        if unique_values < 10:
            return ("DISABLE", f"Extremely sparse ({null_pct:.1f}% null) with only {unique_values} unique values")
        elif null_pct > 98:
            # Special cases for potentially valuable sparse fields
            if 'Address' in name or 'Demographics' in name or 'Name' in name:
                if null_pct > 99:
                    return ("DISABLE", f"Address/demographic field too sparse ({null_pct:.1f}% null)")
                else:
                    return ("KEEP", f"Address/demographic data - sparse but potentially valuable ({null_pct:.1f}% null)")
            else:
                return ("DISABLE", f"Too sparse to be useful ({null_pct:.1f}% null)")
    
    # Rule 5: Sparse demographic fields (>90% null) - evaluate individually
    if 'personexternaldata' in name.lower() or any(x in name for x in ['Income', 'Education', 'Age', 'Gender', 'Household', 'Net Worth']):
        if null_pct > 90:
            return ("DISABLE", f"Demographic field too sparse ({null_pct:.1f}% null)")
        elif null_pct > 80:
            return ("CONSIDER", f"Demographic field is sparse ({null_pct:.1f}% null) - evaluate business value")
    
    # Rule 6: Low-value interest fields
    if 'Interest' in name and null_pct > 85:
        return ("DISABLE", f"Interest field too sparse ({null_pct:.1f}% null)")
    
    # Rule 7: Duplicate or redundant fields
    if name == 'Full Name' and null_pct < 1:  # We already have Client Name Full Name
        return ("CONSIDER", "Possible duplicate of 'Client Name Full Name'")
    
    # Rule 8: Keep meaningful fields with reasonable data
    if null_pct < 50:
        return ("KEEP", f"Good coverage ({100-null_pct:.1f}% populated)")
    elif null_pct < 75:
        return ("KEEP", f"Moderate coverage ({100-null_pct:.1f}% populated)")
    elif null_pct < 85:
        return ("CONSIDER", f"Limited coverage ({100-null_pct:.1f}% populated) - evaluate business need")
    
    # Default for anything not caught
    return ("CONSIDER", f"Evaluate based on business requirements ({null_pct:.1f}% null)")

def analyze_fields(summary: Dict) -> List[Dict]:
    """Analyze all fields and generate recommendations."""
    results = []
    
    for field in summary['fieldSummaries']:
        recommendation, reason = categorize_field(field)
        
        # Format sample values
        sample_str = ""
        if field.get('sampleValues'):
            samples = field['sampleValues'][:3]  # First 3 samples
            sample_str = ", ".join([f"'{s}'" for s in samples])
            if len(field['sampleValues']) > 3:
                sample_str += "..."
        
        results.append({
            'Field Name': field['fieldName'],
            'Null %': f"{field['nullPercentage']:.2f}",
            'Non-Null Count': field['nonNullCount'],
            'Unique Values': field['uniqueValues'],
            'Category': field['category'],
            'Data Type': field['dataType'],
            'Sample Values': sample_str,
            'Recommendation': recommendation,
            'Reason': reason
        })
    
    # Sort by recommendation priority (DISABLE first, then CONSIDER, then KEEP)
    priority = {'DISABLE': 0, 'CONSIDER': 1, 'KEEP': 2}
    results.sort(key=lambda x: (priority.get(x['Recommendation'], 3), float(x['Null %'])), reverse=False)
    
    return results

def generate_summary_stats(results: List[Dict]) -> Dict:
    """Generate summary statistics."""
    stats = {
        'total_fields': len(results),
        'disable_count': sum(1 for r in results if r['Recommendation'] == 'DISABLE'),
        'consider_count': sum(1 for r in results if r['Recommendation'] == 'CONSIDER'),
        'keep_count': sum(1 for r in results if r['Recommendation'] == 'KEEP')
    }
    
    # Group disable reasons
    disable_reasons = {}
    for r in results:
        if r['Recommendation'] == 'DISABLE':
            reason_type = r['Reason'].split('(')[0].strip()
            disable_reasons[reason_type] = disable_reasons.get(reason_type, 0) + 1
    
    stats['disable_reasons'] = disable_reasons
    return stats

def write_csv_report(results: List[Dict], output_path: str):
    """Write results to CSV file."""
    if not results:
        print("No results to write")
        return
    
    with open(output_path, 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=results[0].keys())
        writer.writeheader()
        writer.writerows(results)

def print_summary(stats: Dict, results: List[Dict]):
    """Print summary to console."""
    print("\n" + "="*80)
    print("FIELD QUALITY ANALYSIS SUMMARY")
    print("="*80)
    
    print(f"\nTotal fields analyzed: {stats['total_fields']}")
    print(f"  - DISABLE: {stats['disable_count']} fields")
    print(f"  - CONSIDER: {stats['consider_count']} fields")
    print(f"  - KEEP: {stats['keep_count']} fields")
    
    if stats['disable_reasons']:
        print("\nReasons for DISABLE recommendations:")
        for reason, count in sorted(stats['disable_reasons'].items(), key=lambda x: x[1], reverse=True):
            print(f"  - {reason}: {count} fields")
    
    print("\n" + "="*80)
    print("TOP FIELDS TO DISABLE (Highest Priority)")
    print("="*80)
    
    disable_fields = [r for r in results if r['Recommendation'] == 'DISABLE'][:10]
    for field in disable_fields:
        print(f"\n{field['Field Name']}")
        print(f"  Null: {field['Null %']}% | Unique: {field['Unique Values']} | Reason: {field['Reason']}")
        if field['Sample Values']:
            print(f"  Samples: {field['Sample Values']}")
    
    print("\n" + "="*80)
    print("FIELDS TO CONSIDER (Need Business Input)")
    print("="*80)
    
    consider_fields = [r for r in results if r['Recommendation'] == 'CONSIDER']
    for field in consider_fields[:5]:
        print(f"\n{field['Field Name']}")
        print(f"  Null: {field['Null %']}% | Unique: {field['Unique Values']} | Reason: {field['Reason']}")
    
    # Special section for demographic fields
    print("\n" + "="*80)
    print("DEMOGRAPHIC FIELDS ANALYSIS")
    print("="*80)
    
    demographic_fields = [r for r in results if any(x in r['Field Name'] for x in 
                          ['Income', 'Education', 'Age', 'Gender', 'Net Worth', 'Household', 
                           'Marital', 'Children', 'Adults', 'Home', 'Dwelling'])]
    
    demo_stats = {
        'disable': sum(1 for f in demographic_fields if f['Recommendation'] == 'DISABLE'),
        'consider': sum(1 for f in demographic_fields if f['Recommendation'] == 'CONSIDER'),
        'keep': sum(1 for f in demographic_fields if f['Recommendation'] == 'KEEP')
    }
    
    print(f"\nTotal demographic fields: {len(demographic_fields)}")
    print(f"  - Recommended to DISABLE: {demo_stats['disable']}")
    print(f"  - Need consideration: {demo_stats['consider']}")
    print(f"  - Recommended to KEEP: {demo_stats['keep']}")
    
    print("\nDemographic fields with best coverage:")
    demo_keep = sorted([f for f in demographic_fields if f['Recommendation'] == 'KEEP'], 
                      key=lambda x: float(x['Null %']))
    for field in demo_keep[:5]:
        print(f"  - {field['Field Name']}: {100-float(field['Null %']):.1f}% coverage")

def main():
    """Main execution function."""
    # File paths
    input_file = '/home/ubuntu/IdeaProjects/Realm/output/agentclients_summary.json'
    output_csv = '/home/ubuntu/IdeaProjects/Realm/output/field_quality_recommendations.csv'
    
    # Load and analyze
    print(f"Loading summary from: {input_file}")
    summary = load_summary(input_file)
    
    print("Analyzing fields...")
    results = analyze_fields(summary)
    
    # Generate statistics
    stats = generate_summary_stats(results)
    
    # Write CSV report
    print(f"Writing CSV report to: {output_csv}")
    write_csv_report(results, output_csv)
    
    # Print summary
    print_summary(stats, results)
    
    print(f"\n✅ Analysis complete! CSV report saved to: {output_csv}")
    print(f"   Total recommendations: {stats['disable_count']} fields to disable")
    
    # Create a focused disable list
    disable_list = [r['Field Name'] for r in results if r['Recommendation'] == 'DISABLE']
    disable_file = '/home/ubuntu/IdeaProjects/Realm/output/fields_to_disable.txt'
    with open(disable_file, 'w') as f:
        f.write("# Fields recommended for disabling\n")
        f.write("# Copy these field names to set 'include: false' in config\n\n")
        for field in disable_list:
            f.write(f"{field}\n")
    print(f"   Disable list saved to: {disable_file}")

if __name__ == "__main__":
    main()