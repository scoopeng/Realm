#!/usr/bin/env python3
"""
Show all discovered fields from the audit file, including excluded ones
"""

import re

def parse_audit_file(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()
    
    fields = []
    for line in lines:
        # Parse lines like: "fieldname [type] (EXCLUDED) - X distinct values"
        # or: "fieldname [type] - X distinct values"
        match = re.match(r'^(\s*)(.+?)\s+\[(.+?)\]\s*(?:\(EXCLUDED\))?\s*-\s*(\d+)\s+distinct', line)
        if match:
            indent = len(match.group(1))
            field = match.group(2)
            dtype = match.group(3)
            distinct = int(match.group(4))
            excluded = '(EXCLUDED)' in line
            
            fields.append({
                'field': field,
                'type': dtype,
                'distinct': distinct,
                'excluded': excluded,
                'indent': indent
            })
    
    return fields

# Analyze the fields
fields = parse_audit_file('/home/ubuntu/IdeaProjects/Realm/config/agentclients_expansion_audit.txt')

# Show valuable excluded fields
valuable_excluded = [f for f in fields if f['excluded'] and f['distinct'] >= 10]
valuable_excluded.sort(key=lambda x: x['distinct'], reverse=True)

print("=== VALUABLE EXCLUDED FIELDS (10+ distinct values) ===\n")
for f in valuable_excluded[:50]:
    print(f"  {f['field']}: {f['distinct']} distinct values ({f['type']})")

# Show statistics
included = [f for f in fields if not f['excluded']]
excluded = [f for f in fields if f['excluded']]

print(f"\n=== STATISTICS ===")
print(f"Total fields discovered: {len(fields)}")
print(f"Included: {len(included)}")
print(f"Excluded: {len(excluded)}")
print(f"Valuable excluded (10+ distinct): {len(valuable_excluded)}")

# Group by exclusion reason
zero_distinct = [f for f in excluded if f['distinct'] == 0]
one_distinct = [f for f in excluded if f['distinct'] == 1]
sparse = [f for f in excluded if 2 <= f['distinct'] < 10]
valuable = [f for f in excluded if f['distinct'] >= 10]

print(f"\n=== EXCLUSION BREAKDOWN ===")
print(f"0 distinct values (empty): {len(zero_distinct)}")
print(f"1 distinct value (constant): {len(one_distinct)}")
print(f"2-9 distinct values (sparse): {len(sparse)}")
print(f"10+ distinct values (valuable): {len(valuable)}")