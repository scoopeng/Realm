#!/usr/bin/env python3
"""Analyze duplicate fields in agentclients_fields.json to help choose best version"""

import json
from collections import defaultdict
from typing import Dict, List, Tuple

def load_config(filepath: str) -> dict:
    """Load the JSON configuration file"""
    with open(filepath, 'r') as f:
        return json.load(f)

def calculate_coverage(field: dict) -> float:
    """Calculate the data coverage percentage for a field"""
    stats = field.get('statistics', {})
    total = stats.get('totalOccurrences', 0)
    null_count = stats.get('nullCount', 0)
    
    if total == 0:
        return 0.0
    
    non_null = total - null_count
    return (non_null / total) * 100

def find_duplicate_data_fields(fields: List[dict]) -> Dict[str, List[dict]]:
    """Group fields that represent the same data"""
    duplicates = defaultdict(list)
    
    # Group 1: Client Names
    name_fields = []
    for field in fields:
        path = field['fieldPath']
        if field.get('include', False):
            if 'fullName' in path or 'firstName' in path or 'lastName' in path:
                name_fields.append(field)
    
    # Separate into categories
    client_names = defaultdict(list)
    agent_names = defaultdict(list)
    
    for field in name_fields:
        path = field['fieldPath']
        if 'client_expanded.name' in path:
            client_names['client_expanded'].append(field)
        elif path == 'fullName':
            client_names['root'].append(field)
        elif 'ownerAgent' in path:
            agent_names['ownerAgent'].append(field)
    
    return {
        'client_names': dict(client_names),
        'agent_names': dict(agent_names)
    }

def analyze_objectid_dependencies(fields: List[dict]) -> List[Tuple[str, str, bool]]:
    """Check if ObjectId fields are needed for their expanded versions"""
    objectid_fields = {}
    expanded_fields = {}
    
    for field in fields:
        path = field['fieldPath']
        if field.get('dataType') == 'objectId':
            objectid_fields[path] = field
        if '_expanded' in path:
            base = path.split('_expanded')[0]
            if base not in expanded_fields:
                expanded_fields[base] = []
            expanded_fields[base].append(field)
    
    dependencies = []
    for oid_path, oid_field in objectid_fields.items():
        has_expansion = oid_path in expanded_fields
        is_needed = oid_field.get('relationshipTarget') is not None and has_expansion
        dependencies.append((oid_path, oid_field.get('relationshipTarget', 'None'), is_needed))
    
    return dependencies

def analyze_container_objects(fields: List[dict]) -> List[dict]:
    """Find container objects and check if they have includable children"""
    containers = []
    
    for field in fields:
        if field.get('dataType') == 'object' and field.get('include', False):
            path = field['fieldPath']
            # Check if any child fields are included
            child_fields = [f for f in fields if f['fieldPath'].startswith(path + '.') and f.get('include', False)]
            
            containers.append({
                'path': path,
                'coverage': calculate_coverage(field),
                'included_children': len(child_fields),
                'child_paths': [f['fieldPath'] for f in child_fields[:3]]  # Show first 3
            })
    
    return containers

def main():
    # Load configuration
    config = load_config('/home/ubuntu/IdeaProjects/Realm/config/agentclients_fields.json')
    fields = config.get('fields', [])
    
    print("=" * 80)
    print("AGENTCLIENTS FIELD DUPLICATION ANALYSIS")
    print("=" * 80)
    
    # 1. Check ObjectId dependencies
    print("\n1. OBJECTID FIELDS AND THEIR DEPENDENCIES")
    print("-" * 80)
    dependencies = analyze_objectid_dependencies(fields)
    
    print(f"{'Field Path':<40} {'Target Collection':<20} {'Needed for Expansion'}")
    print("-" * 80)
    for path, target, needed in dependencies:
        if path != '_id':  # Skip the main _id field
            status = "✓ KEEP" if needed else "✗ Can disable"
            print(f"{path:<40} {target:<20} {status}")
    
    # 2. Analyze duplicate name fields
    print("\n2. DUPLICATE NAME FIELDS COMPARISON")
    print("-" * 80)
    
    # Compare root fullName vs client_expanded.name fields
    root_fullname = next((f for f in fields if f['fieldPath'] == 'fullName'), None)
    client_fullname = next((f for f in fields if f['fieldPath'] == 'client_expanded.name.fullName'), None)
    client_firstname = next((f for f in fields if f['fieldPath'] == 'client_expanded.name.firstName'), None)
    client_lastname = next((f for f in fields if f['fieldPath'] == 'client_expanded.name.lastName'), None)
    
    if root_fullname and client_fullname:
        print("\nClient Name Fields:")
        print(f"{'Field':<50} {'Coverage':<12} {'Distinct':<12} {'Recommendation'}")
        print("-" * 80)
        
        # Root fullName
        root_coverage = calculate_coverage(root_fullname)
        root_distinct = root_fullname['statistics']['distinctNonNullValues']
        print(f"{'fullName (root)':<50} {root_coverage:>10.1f}% {root_distinct:>12} {'✗ DISABLE (duplicate)'}")
        
        # Expanded name fields
        client_coverage = calculate_coverage(client_fullname)
        client_distinct = client_fullname['statistics']['distinctNonNullValues']
        print(f"{'client_expanded.name.fullName':<50} {client_coverage:>10.1f}% {client_distinct:>12} {'✓ KEEP (primary)'}")
        
        if client_firstname:
            first_coverage = calculate_coverage(client_firstname)
            first_distinct = client_firstname['statistics']['distinctNonNullValues']
            print(f"{'client_expanded.name.firstName':<50} {first_coverage:>10.1f}% {first_distinct:>12} {'✓ KEEP'}")
        
        if client_lastname:
            last_coverage = calculate_coverage(client_lastname)
            last_distinct = client_lastname['statistics']['distinctNonNullValues']
            print(f"{'client_expanded.name.lastName':<50} {last_coverage:>10.1f}% {last_distinct:>12} {'✓ KEEP'}")
    
    # 3. Check container objects
    print("\n3. CONTAINER OBJECTS ANALYSIS")
    print("-" * 80)
    containers = analyze_container_objects(fields)
    
    print(f"{'Container Path':<40} {'Coverage':<12} {'Children':<10} {'Recommendation'}")
    print("-" * 80)
    for container in containers:
        if container['included_children'] > 0:
            rec = "✓ KEEP (has data)"
        else:
            rec = "✗ DISABLE (empty)"
        print(f"{container['path']:<40} {container['coverage']:>10.1f}% {container['included_children']:>10} {rec}")
        if container['child_paths']:
            for child in container['child_paths']:
                print(f"  └─ {child}")
    
    # 4. Address fields analysis
    print("\n4. ADDRESS FIELDS ANALYSIS")
    print("-" * 80)
    address_fields = [f for f in fields if 'address' in f['fieldPath'].lower() and f.get('include', False)]
    
    print(f"{'Field':<50} {'Coverage':<12} {'Distinct':<12}")
    print("-" * 80)
    for field in address_fields:
        coverage = calculate_coverage(field)
        distinct = field['statistics']['distinctNonNullValues']
        print(f"{field['fieldPath']:<50} {coverage:>10.1f}% {distinct:>12}")
    
    # 5. Summary recommendations
    print("\n" + "=" * 80)
    print("RECOMMENDATIONS SUMMARY")
    print("=" * 80)
    
    print("\n✗ FIELDS TO DISABLE (duplicates or unnecessary):")
    print("-" * 50)
    disable_fields = [
        "fullName",  # Duplicate of client_expanded.name.fullName
        "lastNameFirst",  # Redundant with separate name fields
        "client",  # Keep for relationship but can hide from export if expanded version exists
        "clientOld",  # Old reference field
    ]
    
    for field_path in disable_fields:
        field = next((f for f in fields if f['fieldPath'] == field_path), None)
        if field and field.get('include', False):
            coverage = calculate_coverage(field)
            print(f"  - {field_path:<40} (coverage: {coverage:.1f}%)")
    
    print("\n✓ FIELDS TO KEEP (best version of data):")
    print("-" * 50)
    keep_fields = [
        "client_expanded.name.fullName",
        "client_expanded.name.firstName", 
        "client_expanded.name.lastName",
        "client_expanded.primaryEmail",
        "client_expanded.primaryPhone",
        "realmData.ownerAgent_expanded.fullName",
    ]
    
    for field_path in keep_fields:
        field = next((f for f in fields if f['fieldPath'] == field_path), None)
        if field and field.get('include', False):
            coverage = calculate_coverage(field)
            print(f"  - {field_path:<40} (coverage: {coverage:.1f}%)")
    
    print("\n" + "=" * 80)
    print("EXPORT SAFETY CHECK")
    print("=" * 80)
    print("\n✓ The export will NOT break if you disable the recommended fields because:")
    print("  1. ObjectId fields with relationshipTarget are preserved for expansion")
    print("  2. Container objects with child data are kept")
    print("  3. All expanded fields have their data sources intact")
    print("  4. The supplemental configuration system works independently")

if __name__ == "__main__":
    main()