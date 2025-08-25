#!/usr/bin/env python3
"""
Final incremental field recommendations based on export summary
"""

import json
import csv
import sys

def load_summary(filename):
    """Load the export summary JSON"""
    with open(filename, 'r') as f:
        return json.load(f)

def analyze_fields(summary):
    """Analyze fields and create recommendations"""
    recommendations = []
    
    for field in summary['fieldSummaries']:
        field_name = field['fieldName']
        null_percentage = field['nullPercentage']
        unique_values = field['uniqueValues']
        category = field['category']
        sample_values = field.get('sampleValues', [])
        
        # Initialize recommendation
        rec = {
            'Field Path': field_name,
            'Business Name': field_name,
            'Current Null %': f"{null_percentage:.2f}%",
            'Unique Values': unique_values,
            'Category': category,
            'Final Recommendation': 'KEEP',
            'Priority': 'LOW',
            'Reason': ''
        }
        
        # HIGH PRIORITY DISABLES
        
        # 1. SINGLE_VALUE fields - absolutely useless
        if category == 'SINGLE_VALUE':
            rec['Final Recommendation'] = 'DISABLE'
            rec['Priority'] = 'HIGH'
            rec['Reason'] = f'Single value field - only has value: {sample_values[0] if sample_values else "unknown"}'
        
        # 2. ObjectId fields that slipped through
        elif 'Client' == field_name and unique_values > 500000:
            rec['Final Recommendation'] = 'DISABLE'
            rec['Priority'] = 'HIGH'
            rec['Reason'] = 'ObjectId field with 563K unique values - technical field'
        
        # MEDIUM PRIORITY DISABLES
        
        # 3. Extremely sparse non-demographic fields (>98% null)
        elif null_percentage > 98 and 'Interest' not in field_name and 'Income' not in field_name:
            if field_name == 'Client Name Prefix':
                rec['Final Recommendation'] = 'DISABLE'
                rec['Priority'] = 'MEDIUM'
                rec['Reason'] = f'Extremely sparse ({null_percentage:.1f}% null) and low quality data'
            elif field_name == 'Client Address Postal Code':
                rec['Final Recommendation'] = 'OPTIONAL'
                rec['Priority'] = 'MEDIUM'
                rec['Reason'] = f'Very sparse ({null_percentage:.1f}% null) but could be useful for geo analysis'
        
        # 4. Low variation fields with high nulls
        elif unique_values <= 3 and null_percentage > 70:
            if field_name in ['Client Charity Interest', 'Client Gender Male', 'Client Marital Status', 'Client Travel Interest']:
                rec['Final Recommendation'] = 'OPTIONAL'
                rec['Priority'] = 'LOW'
                rec['Reason'] = f'Binary field with {null_percentage:.1f}% null - limited value but harmless'
            elif field_name == 'Client Luxury Lifestyle':
                rec['Final Recommendation'] = 'OPTIONAL'
                rec['Priority'] = 'LOW'
                rec['Reason'] = f'Binary luxury indicator - {null_percentage:.1f}% null but could be valuable'
        
        # 5. Interest fields that are very sparse (>90% null)
        elif 'Interest' in field_name and null_percentage > 90:
            rec['Final Recommendation'] = 'OPTIONAL'
            rec['Priority'] = 'LOW'
            rec['Reason'] = f'Interest field with {null_percentage:.1f}% null - very sparse but harmless'
        
        # 6. Redundant fields
        elif field_name == 'Full Name' and 'Client Name Full Name' in [f['fieldName'] for f in summary['fieldSummaries']]:
            rec['Final Recommendation'] = 'DISABLE'
            rec['Priority'] = 'MEDIUM'
            rec['Reason'] = 'Redundant - duplicates "Client Name Full Name" field'
        
        # KEEP recommendations with notes
        elif category == 'MEANINGFUL':
            if null_percentage > 85:
                rec['Reason'] = f'Sparse ({null_percentage:.1f}% null) but meaningful when present'
            elif null_percentage > 70:
                rec['Reason'] = f'Moderate coverage ({100-null_percentage:.1f}%) with good variety'
            elif null_percentage > 50:
                rec['Reason'] = f'Fair coverage ({100-null_percentage:.1f}%) with useful data'
            else:
                rec['Reason'] = f'Good coverage ({100-null_percentage:.1f}%) - valuable field'
        
        recommendations.append(rec)
    
    # Sort by priority and recommendation
    priority_order = {'HIGH': 0, 'MEDIUM': 1, 'LOW': 2}
    rec_order = {'DISABLE': 0, 'OPTIONAL': 1, 'KEEP': 2}
    
    recommendations.sort(key=lambda x: (
        rec_order[x['Final Recommendation']], 
        priority_order[x['Priority']], 
        x['Field Path']
    ))
    
    return recommendations

def create_summary_stats(recommendations):
    """Create summary statistics"""
    stats = {
        'Total Fields': len(recommendations),
        'DISABLE (High Priority)': sum(1 for r in recommendations if r['Final Recommendation'] == 'DISABLE' and r['Priority'] == 'HIGH'),
        'DISABLE (Medium Priority)': sum(1 for r in recommendations if r['Final Recommendation'] == 'DISABLE' and r['Priority'] == 'MEDIUM'),
        'OPTIONAL': sum(1 for r in recommendations if r['Final Recommendation'] == 'OPTIONAL'),
        'KEEP': sum(1 for r in recommendations if r['Final Recommendation'] == 'KEEP'),
    }
    
    print("\n=== FINAL RECOMMENDATION SUMMARY ===")
    print(f"Total Fields Analyzed: {stats['Total Fields']}")
    print(f"Fields to DISABLE (High Priority): {stats['DISABLE (High Priority)']}")
    print(f"Fields to DISABLE (Medium Priority): {stats['DISABLE (Medium Priority)']}")
    print(f"Fields marked OPTIONAL: {stats['OPTIONAL']}")
    print(f"Fields to KEEP: {stats['KEEP']}")
    print(f"\nOptimized Field Count: {stats['KEEP'] + stats['OPTIONAL']}")
    print(f"Maximum Reduction: {stats['DISABLE (High Priority)'] + stats['DISABLE (Medium Priority)'] + stats['OPTIONAL']} fields")
    print(f"Minimum Reduction: {stats['DISABLE (High Priority)'] + stats['DISABLE (Medium Priority)']} fields")
    
    return stats

def write_csv(recommendations, filename):
    """Write recommendations to CSV"""
    with open(filename, 'w', newline='') as f:
        fieldnames = ['Field Path', 'Business Name', 'Current Null %', 'Unique Values', 
                     'Category', 'Final Recommendation', 'Priority', 'Reason']
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(recommendations)
    print(f"\nRecommendations written to: {filename}")

def main():
    # Load summary
    summary = load_summary('output/agentclients_summary.json')
    
    # Analyze fields
    recommendations = analyze_fields(summary)
    
    # Print high priority disables
    print("\n=== HIGH PRIORITY FIELDS TO DISABLE ===")
    high_priority = [r for r in recommendations if r['Final Recommendation'] == 'DISABLE' and r['Priority'] == 'HIGH']
    for rec in high_priority:
        print(f"- {rec['Field Path']}: {rec['Reason']}")
    
    # Print medium priority disables
    print("\n=== MEDIUM PRIORITY FIELDS TO DISABLE ===")
    medium_priority = [r for r in recommendations if r['Final Recommendation'] == 'DISABLE' and r['Priority'] == 'MEDIUM']
    for rec in medium_priority:
        print(f"- {rec['Field Path']}: {rec['Reason']}")
    
    # Print optional fields
    print("\n=== OPTIONAL FIELDS (Consider Disabling) ===")
    optional = [r for r in recommendations if r['Final Recommendation'] == 'OPTIONAL']
    for rec in optional:
        print(f"- {rec['Field Path']}: {rec['Reason']}")
    
    # Create summary stats
    stats = create_summary_stats(recommendations)
    
    # Write to CSV
    write_csv(recommendations, 'field_final_recommendations.csv')
    
    # Print action items
    print("\n=== ACTION ITEMS ===")
    print("1. IMMEDIATE: Disable these HIGH priority fields:")
    for rec in high_priority:
        print(f"   - {rec['Field Path']}")
    
    print("\n2. RECOMMENDED: Disable these MEDIUM priority fields:")
    for rec in medium_priority:
        print(f"   - {rec['Field Path']}")
    
    print("\n3. OPTIONAL: Review these fields for potential removal:")
    optional_sorted = sorted(optional, key=lambda x: float(x['Current Null %'].rstrip('%')), reverse=True)
    for rec in optional_sorted[:5]:  # Top 5 most sparse optional fields
        print(f"   - {rec['Field Path']} ({rec['Current Null %']} null)")

if __name__ == '__main__':
    main()