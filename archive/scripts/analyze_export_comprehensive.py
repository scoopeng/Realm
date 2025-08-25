#!/usr/bin/env python3
"""
Comprehensive analysis of agentclients export CSV
Properly categorizes columns and provides detailed statistics
"""

import csv
import json
from datetime import datetime
from collections import Counter, defaultdict
import re

def detect_data_type(values):
    """Detect the most likely data type from a list of values"""
    non_empty = [v for v in values if v]
    
    if not non_empty:
        return "empty"
    
    # Check for arrays (comma-separated)
    if any(',' in str(v) for v in non_empty[:10]):
        return "array/list"
    
    # Check for dates
    date_patterns = [
        re.compile(r'^\d{4}-\d{2}-\d{2}'),  # ISO date
        re.compile(r'^\w{3} \w{3} \d{2} \d{4}'),  # Date string
    ]
    if any(pattern.match(str(v)) for pattern in date_patterns for v in non_empty[:10]):
        return "datetime"
    
    # Check for booleans
    bool_values = {'true', 'false', 'True', 'False'}
    if all(v in bool_values for v in non_empty[:10] if v):
        return "boolean"
    
    # Check for numbers
    try:
        for v in non_empty[:10]:
            if v:
                float(v)
        return "number"
    except ValueError:
        pass
    
    # Check for ObjectIds (24 char hex)
    if all(len(str(v)) == 24 and all(c in '0123456789abcdef' for c in str(v)) 
           for v in non_empty[:5] if v):
        return "objectId"
    
    return "string"

def categorize_column(col_name):
    """Categorize a column based on its name pattern"""
    col_lower = col_name.lower()
    col_clean = col_name.replace(' ', '_').lower()
    
    # Check for client expansion
    if col_clean.startswith('client_'):
        # Further categorize client fields
        if 'address' in col_clean:
            return 'client_address'
        elif 'name' in col_clean or 'demographics' in col_clean:
            return 'client_profile'
        elif 'email' in col_clean or 'phone' in col_clean:
            return 'client_contact'
        else:
            return 'client_other'
    
    # Check for realm data expansion
    elif col_clean.startswith('realm_data_'):
        if 'owner_agent' in col_clean:
            if 'client_upload' in col_clean:
                return 'owner_agent_upload'
            else:
                return 'owner_agent'
        elif 'owner_team' in col_clean:
            return 'owner_team'
        elif 'lifestyle' in col_clean or 'tag' in col_clean:
            return 'realm_data_tags'
        else:
            return 'realm_data_other'
    
    # Direct fields from agentclients
    elif col_name in ['Agents', 'Client', 'Archive Date', 'Updated At', 
                      'Full Name', 'Last Name First', 'Status Intent']:
        return 'direct_agentclients'
    
    else:
        return 'unknown'

def analyze_column(column_data):
    """Analyze a single column's data"""
    non_empty = [v for v in column_data if v]
    total = len(column_data)
    
    data_type = detect_data_type(non_empty)
    
    analysis = {
        'non_null_count': len(non_empty),
        'null_count': total - len(non_empty),
        'non_null_percentage': round((len(non_empty) / total) * 100, 2) if total > 0 else 0,
        'data_type': data_type,
    }
    
    if non_empty:
        # Count distinct values
        unique_values = list(set(non_empty))
        analysis['distinct_count'] = len(unique_values)
        analysis['distinct_percentage'] = round((len(unique_values) / len(non_empty)) * 100, 2)
        
        # Sample values
        if len(unique_values) <= 10:
            analysis['sample_values'] = unique_values[:10]
        else:
            analysis['sample_values'] = unique_values[:5]
        
        # For arrays, analyze lengths
        if data_type == 'array/list':
            lengths = [len(v.split(',')) for v in non_empty]
            analysis['array_min_length'] = min(lengths)
            analysis['array_max_length'] = max(lengths)
            analysis['array_avg_length'] = round(sum(lengths) / len(lengths), 2)
        
        # For numbers, add statistics
        if data_type == 'number':
            try:
                nums = [float(v) for v in non_empty]
                analysis['min'] = min(nums)
                analysis['max'] = max(nums)
                analysis['mean'] = round(sum(nums) / len(nums), 2)
            except:
                pass
    else:
        analysis['distinct_count'] = 0
        analysis['distinct_percentage'] = 0
        analysis['sample_values'] = []
    
    # Identify data quality issues
    issues = []
    if analysis['non_null_percentage'] < 1:
        issues.append(f"Very sparse ({analysis['non_null_percentage']}%)")
    if analysis['distinct_count'] == 1 and analysis['non_null_count'] > 0:
        issues.append("Only one unique value")
    if analysis['non_null_percentage'] == 0:
        issues.append("Empty column")
    if data_type == 'objectId' and 'client' not in column_data[0].lower():
        issues.append("Unexpanded ObjectId reference")
    
    analysis['issues'] = issues
    
    return analysis

def main():
    csv_path = '/home/ubuntu/IdeaProjects/Realm/output/agentclients_full_20250824_185345.csv'
    print(f"Analyzing: {csv_path}")
    print("="*80)
    
    # Read CSV and analyze
    column_data = defaultdict(list)
    headers = []
    row_count = 0
    sample_size = 5000  # Analyze more rows for better statistics
    
    try:
        with open(csv_path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            headers = reader.fieldnames
            print(f"Found {len(headers)} columns")
            
            for row in reader:
                if row_count >= sample_size:
                    break
                for col in headers:
                    column_data[col].append(row.get(col, ''))
                row_count += 1
                
                if row_count % 1000 == 0:
                    print(f"  Read {row_count} rows...")
    except Exception as e:
        print(f"Error reading CSV: {e}")
        return
    
    print(f"Analyzed {row_count} rows\n")
    
    # Analyze each column
    print("Analyzing columns...")
    analyses = {}
    categories = defaultdict(list)
    
    for i, col in enumerate(headers, 1):
        if i % 10 == 0:
            print(f"  Processed {i}/{len(headers)} columns")
        
        analysis = analyze_column(column_data[col])
        analysis['column_name'] = col
        analysis['category'] = categorize_column(col)
        analyses[col] = analysis
        categories[analysis['category']].append(col)
    
    print("\n" + "="*80)
    print("COMPREHENSIVE ANALYSIS RESULTS")
    print("="*80)
    
    # Summary statistics
    data_type_counts = Counter(a['data_type'] for a in analyses.values())
    category_counts = {k: len(v) for k, v in categories.items()}
    
    print(f"\n## Summary Statistics")
    print(f"  Total Rows Analyzed: {row_count:,}")
    print(f"  Total Columns: {len(headers)}")
    print(f"  Columns with Data: {sum(1 for a in analyses.values() if a['non_null_percentage'] > 0)}")
    print(f"  Empty Columns: {sum(1 for a in analyses.values() if a['non_null_percentage'] == 0)}")
    print(f"  Sparse (<10%): {sum(1 for a in analyses.values() if 0 < a['non_null_percentage'] < 10)}")
    print(f"  Well-Populated (>50%): {sum(1 for a in analyses.values() if a['non_null_percentage'] > 50)}")
    
    print(f"\n## Data Types Distribution:")
    for dtype, count in sorted(data_type_counts.items()):
        pct = (count / len(headers)) * 100
        print(f"  {dtype:<12}: {count:2} columns ({pct:5.1f}%)")
    
    print(f"\n## Column Categories (Properly Grouped):")
    category_order = [
        'direct_agentclients',
        'client_address', 
        'client_profile',
        'client_contact',
        'client_other',
        'owner_agent',
        'owner_agent_upload',
        'owner_team',
        'realm_data_tags',
        'realm_data_other',
        'unknown'
    ]
    
    for cat in category_order:
        if cat in categories and categories[cat]:
            print(f"\n### {cat.replace('_', ' ').title()} ({len(categories[cat])} columns)")
            
            # Sort columns in this category by fill rate
            cat_cols = sorted(categories[cat], 
                            key=lambda x: analyses[x]['non_null_percentage'], 
                            reverse=True)
            
            for col in cat_cols[:10]:  # Show top 10
                a = analyses[col]
                print(f"    {col:<50} {a['data_type']:>10} {a['non_null_percentage']:>6.1f}% filled")
            
            if len(cat_cols) > 10:
                print(f"    ... and {len(cat_cols)-10} more")
    
    # Data quality analysis
    print(f"\n## Data Quality Analysis:")
    
    # Find unexpanded ObjectIds
    unexpanded = [col for col, a in analyses.items() 
                  if a['data_type'] == 'objectId' and 'client' not in col.lower()]
    if unexpanded:
        print(f"\n  Unexpanded ObjectId References ({len(unexpanded)}):")
        for col in unexpanded:
            print(f"    - {col}")
    
    # Columns with single values
    single_value = [(col, a['sample_values'][0] if a['sample_values'] else '') 
                    for col, a in analyses.items() 
                    if a['distinct_count'] == 1 and a['non_null_count'] > 0]
    if single_value:
        print(f"\n  Columns with Only One Value ({len(single_value)}):")
        for col, val in single_value[:5]:
            val_str = str(val)[:50] + "..." if len(str(val)) > 50 else str(val)
            print(f"    - {col}: '{val_str}'")
    
    # Empty columns
    empty_cols = [col for col, a in analyses.items() if a['non_null_percentage'] == 0]
    if empty_cols:
        print(f"\n  Completely Empty Columns ({len(empty_cols)}):")
        for col in empty_cols:
            print(f"    - {col}")
    
    # Export detailed report
    print("\n" + "="*80)
    print("DETAILED COLUMN LISTING (All 67 Columns)")
    print("="*80)
    
    # List all columns with their full details
    sorted_cols = sorted(analyses.items(), key=lambda x: (-x[1]['non_null_percentage'], x[0]))
    
    print(f"\n{'#':<3} {'Column Name':<50} {'Category':<20} {'Type':<10} {'Fill%':<7} {'Distinct':<8}")
    print("-"*100)
    
    for i, (col, a) in enumerate(sorted_cols, 1):
        print(f"{i:<3} {col:<50} {a['category']:<20} {a['data_type']:<10} "
              f"{a['non_null_percentage']:>6.1f}% {a['distinct_count']:>7}")
    
    # Save comprehensive report
    report = {
        'analysis_timestamp': datetime.now().isoformat(),
        'file_analyzed': csv_path,
        'rows_analyzed': row_count,
        'summary': {
            'total_columns': len(headers),
            'columns_with_data': sum(1 for a in analyses.values() if a['non_null_percentage'] > 0),
            'empty_columns': len(empty_cols),
            'data_types': dict(data_type_counts),
            'categories': category_counts
        },
        'column_headers': headers,
        'categories': dict(categories),
        'analyses': analyses
    }
    
    json_path = '/home/ubuntu/IdeaProjects/Realm/output/export_analysis_comprehensive.json'
    with open(json_path, 'w') as f:
        json.dump(report, f, indent=2, default=str)
    
    print(f"\n## Comprehensive analysis saved to: {json_path}")
    
    # Generate final summary
    print("\n" + "="*80)
    print("KEY FINDINGS")
    print("="*80)
    
    print("\n1. COLUMN EXPANSION SUCCESS:")
    print(f"   - Client fields expanded: {len(categories.get('client_address', [])) + len(categories.get('client_profile', [])) + len(categories.get('client_contact', []))} columns")
    print(f"   - Owner Agent expanded: {len(categories.get('owner_agent', [])) + len(categories.get('owner_agent_upload', []))} columns")
    print(f"   - Owner Team expanded: {len(categories.get('owner_team', []))} columns")
    
    print("\n2. DATA COMPLETENESS:")
    well_filled = [col for col, a in analyses.items() if a['non_null_percentage'] > 90]
    print(f"   - Excellent (>90% filled): {len(well_filled)} columns")
    print(f"   - Good (50-90% filled): {sum(1 for a in analyses.values() if 50 <= a['non_null_percentage'] <= 90)} columns")
    print(f"   - Poor (<50% filled): {sum(1 for a in analyses.values() if 0 < a['non_null_percentage'] < 50)} columns")
    print(f"   - Empty: {len(empty_cols)} columns")
    
    print("\n3. KEY EXPANDED FIELDS WITH GOOD DATA:")
    key_fields = [
        ('Client Address Fields', [col for col in categories.get('client_address', []) 
                                   if analyses[col]['non_null_percentage'] > 20]),
        ('Owner Agent Fields', [col for col in categories.get('owner_agent', [])
                               if analyses[col]['non_null_percentage'] > 90]),
        ('Owner Team Fields', [col for col in categories.get('owner_team', [])
                              if analyses[col]['non_null_percentage'] > 90])
    ]
    
    for field_type, fields in key_fields:
        if fields:
            print(f"\n   {field_type}:")
            for field in fields[:5]:
                print(f"     - {field}: {analyses[field]['non_null_percentage']}% filled")
    
    print("\n" + "="*80)
    print("Analysis Complete!")

if __name__ == "__main__":
    main()