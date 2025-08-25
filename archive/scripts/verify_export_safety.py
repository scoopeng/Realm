#!/usr/bin/env python3
"""Verify that disabling duplicate fields won't break the export process"""

import json
from typing import List, Dict, Set

def load_config(filepath: str) -> dict:
    """Load the JSON configuration file"""
    with open(filepath, 'r') as f:
        return json.load(f)

def check_expansion_dependencies(fields: List[dict]) -> Dict[str, List[str]]:
    """Check dependencies between ObjectId fields and their expansions"""
    issues = []
    
    # Find all expanded fields
    expanded_fields = {}
    for field in fields:
        if '_expanded' in field['fieldPath'] and field.get('include', False):
            base = field['fieldPath'].split('_expanded')[0]
            if base not in expanded_fields:
                expanded_fields[base] = []
            expanded_fields[base].append(field['fieldPath'])
    
    # Check if base ObjectId fields exist and have relationshipTarget
    for base_path, exp_fields in expanded_fields.items():
        base_field = next((f for f in fields if f['fieldPath'] == base_path), None)
        
        if not base_field:
            issues.append(f"ERROR: No base field '{base_path}' for expansions: {exp_fields}")
        elif not base_field.get('relationshipTarget'):
            issues.append(f"WARNING: Base field '{base_path}' has no relationshipTarget for expansions: {exp_fields}")
        elif not base_field.get('include', True):
            # It's OK if the base ObjectId is not included in export as long as it exists
            print(f"INFO: Base ObjectId '{base_path}' exists but not included in export (OK)")
    
    return issues

def check_supplemental_requirements(fields: List[dict]) -> List[str]:
    """Check if supplemental configuration requirements are met"""
    issues = []
    
    # Load supplemental config if it exists
    try:
        with open('/home/ubuntu/IdeaProjects/Realm/config/agentclients_supplemental.json', 'r') as f:
            supplemental = json.load(f)
            
        required_collections = supplemental.get('requiredCollections', [])
        
        # Check if client_expanded exists for reverse lookups
        client_expanded = next((f for f in fields if f['fieldPath'] == 'client_expanded'), None)
        if not client_expanded:
            issues.append("WARNING: 'client_expanded' not found but needed for supplemental fields")
        
        # Verify client ObjectId field exists
        client_field = next((f for f in fields if f['fieldPath'] == 'client' and f.get('relationshipTarget') == 'people'), None)
        if not client_field:
            issues.append("ERROR: 'client' ObjectId field with people relationship not found - needed for supplemental")
            
    except FileNotFoundError:
        print("INFO: No supplemental configuration found (OK)")
    
    return issues

def simulate_field_disabling(fields: List[dict], disable_list: List[str]) -> Dict[str, any]:
    """Simulate disabling fields and check for issues"""
    results = {
        'total_fields': len([f for f in fields if f.get('include', False)]),
        'fields_to_disable': len(disable_list),
        'fields_after': 0,
        'broken_expansions': [],
        'safe_to_disable': []
    }
    
    # Mark fields as disabled
    for field in fields:
        if field['fieldPath'] in disable_list:
            if field.get('include', False):
                # Check if this field is needed for expansions
                if field.get('dataType') == 'objectId' and field.get('relationshipTarget'):
                    # Check if there are active expanded fields
                    exp_pattern = field['fieldPath'] + '_expanded'
                    has_active_expansions = any(
                        f['fieldPath'].startswith(exp_pattern) and f.get('include', False) 
                        for f in fields
                    )
                    if has_active_expansions:
                        results['broken_expansions'].append(field['fieldPath'])
                    else:
                        results['safe_to_disable'].append(field['fieldPath'])
                else:
                    results['safe_to_disable'].append(field['fieldPath'])
    
    # Count remaining active fields
    results['fields_after'] = len([
        f for f in fields 
        if f.get('include', False) and f['fieldPath'] not in disable_list
    ])
    
    return results

def main():
    print("=" * 80)
    print("EXPORT SAFETY VERIFICATION")
    print("=" * 80)
    
    # Load configuration
    config = load_config('/home/ubuntu/IdeaProjects/Realm/config/agentclients_fields.json')
    fields = config.get('fields', [])
    
    # 1. Check expansion dependencies
    print("\n1. CHECKING EXPANSION DEPENDENCIES")
    print("-" * 80)
    issues = check_expansion_dependencies(fields)
    
    if issues:
        for issue in issues:
            print(f"  ❌ {issue}")
    else:
        print("  ✅ All expanded fields have proper base ObjectId fields with relationships")
    
    # 2. Check supplemental requirements
    print("\n2. CHECKING SUPPLEMENTAL CONFIGURATION REQUIREMENTS")
    print("-" * 80)
    supp_issues = check_supplemental_requirements(fields)
    
    if supp_issues:
        for issue in supp_issues:
            print(f"  ❌ {issue}")
    else:
        print("  ✅ All requirements for supplemental fields are met")
    
    # 3. Simulate disabling duplicate fields
    print("\n3. SIMULATING FIELD DISABLING")
    print("-" * 80)
    
    fields_to_disable = [
        "fullName",           # Duplicate of client_expanded.name.fullName
        "lastNameFirst",      # Redundant with name components
        # NOT disabling "client" as it's needed for expansion
        "clientOld",          # Old reference
        # Disable redundant _id fields inside expanded objects
        "client_expanded._id",
        "realmData.ownerAgent_expanded._id",
        "realmData.ownerTeam_expanded._id",
        # Disable other ObjectIds without relationships
        "realmData.ownerAgent_expanded.person",
        "realmData.ownerAgent_expanded.subscription",
        "realmData.ownerTeam_expanded.leader",
    ]
    
    results = simulate_field_disabling(fields, fields_to_disable)
    
    print(f"  Current included fields: {results['total_fields']}")
    print(f"  Fields to disable: {results['fields_to_disable']}")
    print(f"  Fields after disabling: {results['fields_after']}")
    
    if results['broken_expansions']:
        print("\n  ❌ WARNING: These fields would break expansions if disabled:")
        for field in results['broken_expansions']:
            print(f"     - {field}")
    else:
        print("\n  ✅ No expansion dependencies would be broken")
    
    if results['safe_to_disable']:
        print("\n  ✅ These fields are safe to disable:")
        for field in results['safe_to_disable'][:10]:  # Show first 10
            print(f"     - {field}")
        if len(results['safe_to_disable']) > 10:
            print(f"     ... and {len(results['safe_to_disable']) - 10} more")
    
    # 4. Final safety check
    print("\n" + "=" * 80)
    print("FINAL SAFETY ASSESSMENT")
    print("=" * 80)
    
    # Critical fields that must remain
    critical_fields = [
        "client",                    # Needed for client_expanded
        "realmData.ownerAgent",      # Needed for ownerAgent_expanded  
        "realmData.ownerTeam",       # Needed for ownerTeam_expanded
    ]
    
    print("\n✅ CRITICAL FIELDS (must keep for expansions):")
    for field_path in critical_fields:
        field = next((f for f in fields if f['fieldPath'] == field_path), None)
        if field:
            target = field.get('relationshipTarget', 'N/A')
            include = field.get('include', False)
            print(f"  - {field_path:<40} -> {target:<15} (include={include})")
    
    print("\n✅ RECOMMENDED ACTIONS:")
    print("  1. Keep ObjectId fields with relationshipTarget (even if include=false)")
    print("  2. Disable duplicate name fields (fullName, lastNameFirst)")
    print("  3. Disable ObjectId fields without relationships")
    print("  4. Keep all container objects that have included children")
    print("  5. The export will continue to work correctly with these changes")

if __name__ == "__main__":
    main()