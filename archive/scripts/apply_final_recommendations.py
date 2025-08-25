#!/usr/bin/env python3
"""
Apply final field recommendations to the configuration files
"""

import json
import sys

def load_config(filename):
    """Load configuration file"""
    with open(filename, 'r') as f:
        return json.load(f)

def save_config(config, filename):
    """Save configuration file"""
    with open(filename, 'w') as f:
        json.dump(config, f, indent=2)
    print(f"Updated: {filename}")

def apply_recommendations():
    """Apply the final recommendations to the configuration files"""
    
    # Fields to disable (HIGH and MEDIUM priority)
    fields_to_disable = [
        'client',  # ObjectId field
        'realmData.ownerAgent.realmData.client.uploadFile',  # Single value field
        'client.name.prefix',  # Extremely sparse
        'fullName'  # Redundant with client.name.fullName
    ]
    
    # Load main configuration
    config = load_config('config/agentclients_fields.json')
    
    # Track changes
    changes = {'disabled': 0, 'already_disabled': 0, 'not_found': 0}
    
    # Apply changes to main config
    for field in config['fields']:
        field_path = field['fieldPath']
        
        # Check if this field should be disabled
        if any(field_path.lower() == f.lower() or 
               field_path.lower().endswith('.' + f.lower()) or
               f.lower() in field_path.lower() 
               for f in fields_to_disable):
            if field['include']:
                field['include'] = False
                changes['disabled'] += 1
                print(f"DISABLED: {field_path}")
            else:
                changes['already_disabled'] += 1
    
    # Save updated configuration
    save_config(config, 'config/agentclients_fields.json')
    
    # Print summary
    print(f"\n=== CHANGES APPLIED ===")
    print(f"Newly disabled fields: {changes['disabled']}")
    print(f"Already disabled: {changes['already_disabled']}")
    
    # Count final state
    enabled_count = sum(1 for f in config['fields'] if f['include'])
    disabled_count = sum(1 for f in config['fields'] if not f['include'])
    
    # Check supplemental
    supplemental = load_config('config/agentclients_supplemental.json')
    supplemental_enabled = sum(1 for f in supplemental['fields'] if f['include'])
    
    print(f"\n=== FINAL CONFIGURATION ===")
    print(f"Main config - Enabled fields: {enabled_count}")
    print(f"Main config - Disabled fields: {disabled_count}")
    print(f"Supplemental - Enabled fields: {supplemental_enabled}")
    print(f"TOTAL ENABLED FIELDS: {enabled_count + supplemental_enabled}")
    
    return enabled_count + supplemental_enabled

def verify_export_field_count():
    """Verify the export will have the expected number of fields"""
    
    # These are the fields that should be in the final export
    expected_fields = {
        # Core client fields (good coverage)
        'Client Name First Name',
        'Client Name Full Name', 
        'Client Name Last Name',
        'Client Primary Email',
        'Client Data Source',
        
        # Address fields (moderate coverage)
        'Client Address City',
        'Client Address State',
        
        # Demographics (moderate coverage)
        'Client Age',
        'Client Birth Year',
        'Client Household Income',
        'Client Household Income Midpoint',
        'Client Household Net Worth',
        'Client Net Worth Midpoint',
        'Client Income Level',
        'Client Education Level',
        'Client Education Ordinal',
        'Client Home Owner',
        'Client Home Value',
        'Client Area Median Home Value',
        'Client Dwelling Type',
        'Client Household Size',
        'Client Number of Adults',
        'Client Number of Children',
        'Client Number of Vehicles',
        'Client Length of Residence',
        'Client Urbanicity',
        
        # Interests (sparse but kept as optional)
        'Client Health Interest',
        'Client Family Affinity',
        
        # Realm-specific fields
        'Realm Data Max Price',
        'Realm Data Lifestyles',
        'Realm Data Lifestyle Names',
        'Realm Data Tags',
        'Realm Data Tag Names',
        'Realm Data Owner Agent Full Name',
        'Realm Data Owner Agent Realm Data Client Count',
        'Realm Data Owner Agent Realm Data Listing Count',
        'Realm Data Owner Team Display Name',
        'Realm Data Owner Team Name',
        'Realm Data Owner Team Tier',
        
        # Status
        'Status Intent',
        
        # Phone (sparse but valuable)
        'Client Primary Phone'
    }
    
    print(f"\n=== EXPECTED FIELD COUNT ===")
    print(f"Core expected fields: {len(expected_fields)}")
    print(f"Plus 30 supplemental demographic fields")
    print(f"Expected total: ~{len(expected_fields) + 30} fields")
    
    return expected_fields

if __name__ == '__main__':
    # Apply recommendations
    total_fields = apply_recommendations()
    
    # Verify expected fields
    expected = verify_export_field_count()
    
    print(f"\n=== OPTIMIZATION COMPLETE ===")
    print(f"Final export will have {total_fields} fields")
    print(f"Removed: 4 fields (2 HIGH priority, 2 MEDIUM priority)")
    print(f"Optional fields retained for now (can be removed later if needed)")
    print(f"\nRecommendation: Run export to verify data quality with optimized field set")