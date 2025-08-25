#!/usr/bin/env python3
"""
Simple CSV analysis without external dependencies
Analyzes the agentclients export CSV file
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
    date_pattern = re.compile(r'^\d{4}-\d{2}-\d{2}')
    if any(date_pattern.match(str(v)) for v in non_empty[:10]):
        return "datetime"
    
    # Check for booleans
    bool_values = {'true', 'false', 'True', 'False'}
    if all(v in bool_values for v in non_empty[:10]):
        return "boolean"
    
    # Check for numbers
    try:
        for v in non_empty[:10]:
            if v:
                float(v)
        return "number"
    except ValueError:
        pass
    
    return "string"

def analyze_column(column_data):
    """Analyze a single column's data"""
    non_empty = [v for v in column_data if v]
    total = len(column_data)
    
    analysis = {
        'non_null_count': len(non_empty),
        'null_count': total - len(non_empty),
        'non_null_percentage': round((len(non_empty) / total) * 100, 2) if total > 0 else 0,
        'data_type': detect_data_type(non_empty),
    }
    
    if non_empty:
        # Count distinct values
        unique_values = list(set(non_empty))
        analysis['distinct_count'] = len(unique_values)
        
        # Sample values
        if len(unique_values) <= 20:
            analysis['sample_values'] = unique_values[:20]
        else:
            analysis['sample_values'] = unique_values[:5]
        
        # For arrays, analyze lengths
        if analysis['data_type'] == 'array/list':
            lengths = [len(v.split(',')) for v in non_empty]
            analysis['array_min_length'] = min(lengths)
            analysis['array_max_length'] = max(lengths)
            analysis['array_avg_length'] = round(sum(lengths) / len(lengths), 2)
    else:
        analysis['distinct_count'] = 0
        analysis['sample_values'] = []
    
    # Identify data quality issues
    issues = []
    if analysis['non_null_percentage'] < 1:
        issues.append(f"Very sparse ({analysis['non_null_percentage']}%)")
    if analysis['distinct_count'] == 1 and analysis['non_null_count'] > 0:
        issues.append("Only one unique value")
    if analysis['non_null_percentage'] == 0:
        issues.append("Empty column")
    
    analysis['issues'] = issues
    
    return analysis

def categorize_columns(columns):
    """Categorize columns by their source pattern"""
    categories = {
        'direct_fields': [],
        'client_expanded': [],
        'realmData_ownerAgent_expanded': [],
        'realmData_ownerTeam_expanded': [],
        'address_fields': [],
        'other_expanded': []
    }
    
    for col in columns:
        if col.startswith('client_'):
            categories['client_expanded'].append(col)
        elif col.startswith('realmData_ownerAgent_'):
            categories['realmData_ownerAgent_expanded'].append(col)
        elif col.startswith('realmData_ownerTeam_'):
            categories['realmData_ownerTeam_expanded'].append(col)
        elif 'address_' in col or 'location_' in col:
            categories['address_fields'].append(col)
        elif '_' in col and col not in ['created', 'lastContacted', 'anniversaryDate', 'birthDate']:
            categories['other_expanded'].append(col)
        else:
            categories['direct_fields'].append(col)
    
    return categories

def main():
    csv_path = '/home/ubuntu/IdeaProjects/Realm/output/agentclients_full_20250824_185345.csv'
    print(f"Analyzing: {csv_path}")
    
    # Read CSV and analyze first 1000 rows
    column_data = defaultdict(list)
    headers = []
    row_count = 0
    sample_size = 1000
    
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
    except Exception as e:
        print(f"Error reading CSV: {e}")
        return
    
    print(f"Analyzed {row_count} rows")
    
    # Analyze each column
    print("\nAnalyzing columns...")
    analyses = {}
    for i, col in enumerate(headers, 1):
        if i % 10 == 0:
            print(f"  Processed {i}/{len(headers)} columns")
        analyses[col] = analyze_column(column_data[col])
        analyses[col]['column_name'] = col
    
    # Categorize columns
    categories = categorize_columns(headers)
    
    # Generate summary
    data_type_counts = Counter(a['data_type'] for a in analyses.values())
    
    summary = {
        'file': csv_path,
        'timestamp': datetime.now().isoformat(),
        'rows_analyzed': row_count,
        'total_columns': len(headers),
        'columns_with_data': sum(1 for a in analyses.values() if a['non_null_percentage'] > 0),
        'empty_columns': sum(1 for a in analyses.values() if a['non_null_percentage'] == 0),
        'sparse_columns': sum(1 for a in analyses.values() if 0 < a['non_null_percentage'] < 10),
        'well_populated': sum(1 for a in analyses.values() if a['non_null_percentage'] > 50),
        'data_types': dict(data_type_counts),
        'category_counts': {k: len(v) for k, v in categories.items() if v}
    }
    
    # Print summary
    print("\n" + "="*80)
    print("ANALYSIS SUMMARY")
    print("="*80)
    print(f"\nRows analyzed: {summary['rows_analyzed']:,}")
    print(f"Total columns: {summary['total_columns']}")
    
    print("\n## Data Completeness:")
    print(f"  Columns with data: {summary['columns_with_data']}/{summary['total_columns']}")
    print(f"  Empty columns: {summary['empty_columns']}")
    print(f"  Sparse (<10%): {summary['sparse_columns']}")
    print(f"  Well-populated (>50%): {summary['well_populated']}")
    
    print("\n## Data Types:")
    for dtype, count in summary['data_types'].items():
        print(f"  {dtype}: {count}")
    
    print("\n## Column Categories:")
    for cat, count in summary['category_counts'].items():
        print(f"  {cat}: {count}")
    
    # Show all column headers grouped by category
    print("\n## All 67 Columns by Category:")
    print("-" * 80)
    
    for category, cols in categories.items():
        if cols:
            print(f"\n### {category.replace('_', ' ').title()} ({len(cols)} columns):")
            for col in sorted(cols):
                a = analyses[col]
                fill_rate = a['non_null_percentage']
                dtype = a['data_type']
                print(f"  - {col:<45} [{dtype:>10}] {fill_rate:>6.1f}% filled")
    
    # Top populated columns
    print("\n## Top 20 Most Populated Columns:")
    print("-" * 80)
    sorted_analyses = sorted(analyses.items(), key=lambda x: x[1]['non_null_percentage'], reverse=True)
    for i, (col, a) in enumerate(sorted_analyses[:20], 1):
        print(f"{i:2}. {col:<45} {a['non_null_percentage']:>6.1f}% ({a['non_null_count']}/{row_count})")
    
    # Empty columns
    empty_cols = [(col, a) for col, a in analyses.items() if a['non_null_percentage'] == 0]
    if empty_cols:
        print(f"\n## Empty Columns ({len(empty_cols)}):")
        print("-" * 80)
        for col, _ in empty_cols:
            print(f"  - {col}")
    
    # Save detailed results
    report = {
        'summary': summary,
        'categories': categories,
        'column_analyses': analyses
    }
    
    output_path = '/home/ubuntu/IdeaProjects/Realm/output/export_analysis.json'
    with open(output_path, 'w') as f:
        json.dump(report, f, indent=2, default=str)
    print(f"\n## Detailed analysis saved to: {output_path}")
    
    # Generate a detailed markdown report
    generate_markdown_report(report)
    
    print("\n" + "="*80)
    print("Analysis Complete!")

def generate_markdown_report(report):
    """Generate a detailed markdown report"""
    output_path = '/home/ubuntu/IdeaProjects/Realm/output/export_analysis.md'
    
    with open(output_path, 'w') as f:
        f.write("# AgentClients Export Analysis\n\n")
        f.write(f"**Generated:** {report['summary']['timestamp']}\n")
        f.write(f"**File:** `{report['summary']['file']}`\n")
        f.write(f"**Rows Analyzed:** {report['summary']['rows_analyzed']:,}\n\n")
        
        f.write("## Summary Statistics\n\n")
        s = report['summary']
        f.write(f"- Total Columns: {s['total_columns']}\n")
        f.write(f"- Columns with Data: {s['columns_with_data']}\n")
        f.write(f"- Empty Columns: {s['empty_columns']}\n")
        f.write(f"- Sparse Columns (<10%): {s['sparse_columns']}\n")
        f.write(f"- Well-Populated (>50%): {s['well_populated']}\n\n")
        
        f.write("## All 67 Columns\n\n")
        f.write("| # | Column Name | Category | Data Type | Fill Rate | Distinct Values |\n")
        f.write("|---|-------------|----------|-----------|-----------|----------------|\n")
        
        # Sort columns by fill rate
        sorted_cols = sorted(report['column_analyses'].items(), 
                           key=lambda x: x[1]['non_null_percentage'], reverse=True)
        
        for i, (col, analysis) in enumerate(sorted_cols, 1):
            # Determine category
            category = "unknown"
            for cat, cols in report['categories'].items():
                if col in cols:
                    category = cat.replace('_', ' ')
                    break
            
            f.write(f"| {i} | `{col}` | {category} | {analysis['data_type']} | "
                   f"{analysis['non_null_percentage']}% | {analysis['distinct_count']} |\n")
        
        f.write("\n## Column Groups\n\n")
        for category, cols in report['categories'].items():
            if cols:
                f.write(f"### {category.replace('_', ' ').title()}\n\n")
                f.write(f"**Count:** {len(cols)} columns\n\n")
                f.write("| Column | Fill Rate | Type | Sample Values |\n")
                f.write("|--------|-----------|------|---------------|\n")
                
                for col in sorted(cols):
                    a = report['column_analyses'][col]
                    samples = ', '.join(str(v)[:30] for v in a['sample_values'][:3])
                    if len(samples) > 50:
                        samples = samples[:50] + "..."
                    f.write(f"| `{col}` | {a['non_null_percentage']}% | "
                           f"{a['data_type']} | {samples} |\n")
                f.write("\n")
    
    print(f"Markdown report saved to: {output_path}")

if __name__ == "__main__":
    main()