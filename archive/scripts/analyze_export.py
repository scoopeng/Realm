#!/usr/bin/env python3
"""
Comprehensive analysis of agentclients export CSV file
Analyzes data types, completeness, and quality for all 67 columns
"""

import pandas as pd
import numpy as np
import json
from datetime import datetime
from collections import Counter
import re

def detect_data_type(series):
    """Detect the most likely data type of a pandas series"""
    # Remove null values for analysis
    non_null = series.dropna()
    
    if len(non_null) == 0:
        return "empty"
    
    # Check if it's a list/array type (comma-separated)
    sample = str(non_null.iloc[0]) if len(non_null) > 0 else ""
    if ',' in sample and len(sample.split(',')) > 1:
        return "array/list"
    
    # Try to detect dates
    if non_null.dtype == 'object':
        try:
            # Check if values look like dates
            sample_str = str(non_null.iloc[0])
            if re.match(r'\d{4}-\d{2}-\d{2}', sample_str) or 'T' in sample_str:
                pd.to_datetime(non_null.head(10))
                return "datetime"
        except:
            pass
    
    # Check for boolean
    unique_vals = set(non_null.unique())
    if unique_vals <= {'true', 'false', 'True', 'False', '0', '1', 0, 1, True, False}:
        return "boolean"
    
    # Check for numeric
    if pd.api.types.is_numeric_dtype(non_null):
        if pd.api.types.is_integer_dtype(non_null):
            return "integer"
        else:
            return "float"
    
    # Try to convert to numeric
    try:
        pd.to_numeric(non_null)
        if '.' in str(non_null.iloc[0]):
            return "float"
        return "integer"
    except:
        pass
    
    # Default to string
    return "string"

def analyze_column(df, col_name, sample_size=1000):
    """Analyze a single column comprehensively"""
    series = df[col_name].head(sample_size)
    total_rows = len(series)
    
    analysis = {
        'column_name': col_name,
        'data_type': detect_data_type(series),
        'non_null_count': series.notna().sum(),
        'null_count': series.isna().sum(),
        'non_null_percentage': round((series.notna().sum() / total_rows) * 100, 2),
        'distinct_count': series.nunique(),
        'distinct_percentage': round((series.nunique() / series.notna().sum() * 100), 2) if series.notna().sum() > 0 else 0,
    }
    
    # Get sample values (up to 5 unique values)
    non_null_values = series.dropna()
    if len(non_null_values) > 0:
        unique_values = non_null_values.unique()
        if len(unique_values) <= 20:
            analysis['sample_values'] = list(unique_values[:20])
        else:
            analysis['sample_values'] = list(unique_values[:5])
        
        # For numeric columns, add statistics
        if analysis['data_type'] in ['integer', 'float']:
            try:
                numeric_series = pd.to_numeric(non_null_values)
                analysis['min'] = float(numeric_series.min())
                analysis['max'] = float(numeric_series.max())
                analysis['mean'] = round(float(numeric_series.mean()), 2)
                analysis['median'] = float(numeric_series.median())
            except:
                pass
        
        # For array/list columns, analyze the array lengths
        if analysis['data_type'] == 'array/list':
            lengths = non_null_values.apply(lambda x: len(str(x).split(',')))
            analysis['array_min_length'] = int(lengths.min())
            analysis['array_max_length'] = int(lengths.max())
            analysis['array_avg_length'] = round(float(lengths.mean()), 2)
    else:
        analysis['sample_values'] = []
    
    # Detect potential data quality issues
    issues = []
    if analysis['non_null_percentage'] < 1:
        issues.append(f"Very sparse data ({analysis['non_null_percentage']}% non-null)")
    if analysis['distinct_count'] == 1 and analysis['non_null_count'] > 0:
        issues.append("Only one unique value")
    if analysis['non_null_percentage'] == 0:
        issues.append("Completely empty column")
    
    analysis['data_quality_issues'] = issues
    
    return analysis

def categorize_columns(columns):
    """Categorize columns by their source/expansion pattern"""
    categories = {
        'direct_fields': [],
        'client_expanded': [],
        'realmData_ownerAgent_expanded': [],
        'realmData_ownerTeam_expanded': [],
        'other_expanded': [],
        'unknown': []
    }
    
    for col in columns:
        if 'client_' in col:
            categories['client_expanded'].append(col)
        elif 'realmData_ownerAgent_' in col:
            categories['realmData_ownerAgent_expanded'].append(col)
        elif 'realmData_ownerTeam_' in col:
            categories['realmData_ownerTeam_expanded'].append(col)
        elif '_' in col and any(x in col for x in ['address_', 'location_', 'office_']):
            categories['other_expanded'].append(col)
        elif col in ['agent', 'client', 'leadSource', 'created', 'lastContacted', 
                     'anniversaryDate', 'birthDate', 'status', 'pipeline', 'note',
                     'additionalEmails', 'additionalPhones', 'tags', 'customFields']:
            categories['direct_fields'].append(col)
        else:
            categories['unknown'].append(col)
    
    return categories

def main():
    # Read the CSV file
    csv_path = '/home/ubuntu/IdeaProjects/Realm/output/agentclients_full_20250824_185345.csv'
    print(f"Reading CSV file: {csv_path}")
    
    try:
        # Read with low_memory=False to ensure proper dtype inference
        df = pd.read_csv(csv_path, low_memory=False)
        print(f"Successfully loaded CSV with {len(df)} rows and {len(df.columns)} columns")
    except Exception as e:
        print(f"Error reading CSV: {e}")
        return
    
    # Extract all column headers
    columns = df.columns.tolist()
    print(f"\nFound {len(columns)} columns")
    
    # Analyze first 1000 rows for performance
    sample_df = df.head(1000)
    
    # Analyze each column
    print("\nAnalyzing columns...")
    column_analyses = []
    for i, col in enumerate(columns, 1):
        if i % 10 == 0:
            print(f"  Processed {i}/{len(columns)} columns...")
        analysis = analyze_column(sample_df, col)
        column_analyses.append(analysis)
    
    # Categorize columns
    categories = categorize_columns(columns)
    
    # Generate summary statistics
    summary = {
        'total_rows_analyzed': len(sample_df),
        'total_columns': len(columns),
        'column_categories': {k: len(v) for k, v in categories.items()},
        'data_types_distribution': Counter([a['data_type'] for a in column_analyses]),
        'columns_with_data': sum(1 for a in column_analyses if a['non_null_percentage'] > 0),
        'empty_columns': sum(1 for a in column_analyses if a['non_null_percentage'] == 0),
        'sparse_columns': sum(1 for a in column_analyses if 0 < a['non_null_percentage'] < 10),
        'well_populated_columns': sum(1 for a in column_analyses if a['non_null_percentage'] > 50),
    }
    
    # Create detailed report
    report = {
        'analysis_timestamp': datetime.now().isoformat(),
        'file_analyzed': csv_path,
        'summary': summary,
        'column_categories': categories,
        'column_analyses': column_analyses
    }
    
    # Save detailed JSON report
    json_output = '/home/ubuntu/IdeaProjects/Realm/output/export_analysis_detailed.json'
    with open(json_output, 'w') as f:
        json.dump(report, f, indent=2, default=str)
    print(f"\nDetailed analysis saved to: {json_output}")
    
    # Generate human-readable summary report
    print("\n" + "="*80)
    print("EXPORT ANALYSIS SUMMARY")
    print("="*80)
    
    print(f"\nFile: {csv_path}")
    print(f"Rows analyzed: {summary['total_rows_analyzed']:,} (sample)")
    print(f"Total columns: {summary['total_columns']}")
    
    print("\n## Column Categories:")
    for category, count in summary['column_categories'].items():
        if count > 0:
            print(f"  - {category}: {count} columns")
    
    print("\n## Data Type Distribution:")
    for dtype, count in summary['data_types_distribution'].items():
        print(f"  - {dtype}: {count} columns")
    
    print("\n## Data Completeness:")
    print(f"  - Columns with data: {summary['columns_with_data']}/{summary['total_columns']}")
    print(f"  - Empty columns: {summary['empty_columns']}")
    print(f"  - Sparse columns (<10% filled): {summary['sparse_columns']}")
    print(f"  - Well-populated columns (>50% filled): {summary['well_populated_columns']}")
    
    # Show top populated columns
    print("\n## Top 20 Most Populated Columns:")
    sorted_columns = sorted(column_analyses, key=lambda x: x['non_null_percentage'], reverse=True)
    for i, col in enumerate(sorted_columns[:20], 1):
        print(f"  {i:2}. {col['column_name']:<40} {col['non_null_percentage']:>6.1f}% ({col['non_null_count']:,}/{summary['total_rows_analyzed']:,})")
    
    # Show empty or nearly empty columns
    empty_cols = [c for c in column_analyses if c['non_null_percentage'] == 0]
    if empty_cols:
        print(f"\n## Empty Columns ({len(empty_cols)}):")
        for col in empty_cols:
            print(f"  - {col['column_name']}")
    
    # Show data quality issues
    print("\n## Columns with Data Quality Issues:")
    issues_found = False
    for col in column_analyses:
        if col['data_quality_issues']:
            issues_found = True
            print(f"  - {col['column_name']}: {', '.join(col['data_quality_issues'])}")
    if not issues_found:
        print("  No major issues detected in sampled data")
    
    # Generate markdown summary
    markdown_output = '/home/ubuntu/IdeaProjects/Realm/output/export_analysis_summary.md'
    generate_markdown_report(report, markdown_output)
    print(f"\nMarkdown summary saved to: {markdown_output}")
    
    print("\n" + "="*80)
    print("Analysis complete!")

def generate_markdown_report(report, output_path):
    """Generate a markdown summary report"""
    summary = report['summary']
    categories = report['column_categories']
    analyses = report['column_analyses']
    
    with open(output_path, 'w') as f:
        f.write("# AgentClients Export Analysis Report\n\n")
        f.write(f"**Generated:** {report['analysis_timestamp']}\n")
        f.write(f"**File:** `{report['file_analyzed']}`\n\n")
        
        f.write("## Summary Statistics\n\n")
        f.write(f"- **Rows Analyzed:** {summary['total_rows_analyzed']:,} (sample)\n")
        f.write(f"- **Total Columns:** {summary['total_columns']}\n")
        f.write(f"- **Columns with Data:** {summary['columns_with_data']}\n")
        f.write(f"- **Empty Columns:** {summary['empty_columns']}\n")
        f.write(f"- **Sparse Columns (<10%):** {summary['sparse_columns']}\n")
        f.write(f"- **Well-Populated (>50%):** {summary['well_populated_columns']}\n\n")
        
        f.write("## Column Categories\n\n")
        for category, cols in categories.items():
            if cols:
                f.write(f"### {category.replace('_', ' ').title()} ({len(cols)} columns)\n")
                for col in cols[:10]:  # Show first 10
                    col_data = next((a for a in analyses if a['column_name'] == col), None)
                    if col_data:
                        f.write(f"- `{col}` - {col_data['data_type']} ({col_data['non_null_percentage']}% filled)\n")
                if len(cols) > 10:
                    f.write(f"- ... and {len(cols)-10} more\n")
                f.write("\n")
        
        f.write("## Data Type Distribution\n\n")
        f.write("| Data Type | Count | Percentage |\n")
        f.write("|-----------|-------|------------|\n")
        for dtype, count in summary['data_types_distribution'].items():
            pct = (count / summary['total_columns']) * 100
            f.write(f"| {dtype} | {count} | {pct:.1f}% |\n")
        
        f.write("\n## Top 30 Most Populated Columns\n\n")
        f.write("| Rank | Column | Fill Rate | Non-Null | Distinct | Type |\n")
        f.write("|------|--------|-----------|----------|----------|------|\n")
        sorted_cols = sorted(analyses, key=lambda x: x['non_null_percentage'], reverse=True)
        for i, col in enumerate(sorted_cols[:30], 1):
            f.write(f"| {i} | `{col['column_name']}` | {col['non_null_percentage']}% | {col['non_null_count']:,} | {col['distinct_count']} | {col['data_type']} |\n")
        
        f.write("\n## Data Quality Issues\n\n")
        for col in analyses:
            if col['data_quality_issues']:
                f.write(f"- **{col['column_name']}**: {', '.join(col['data_quality_issues'])}\n")

if __name__ == "__main__":
    main()