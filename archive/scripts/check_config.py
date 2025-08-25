#!/usr/bin/env python3
import json

with open("/home/ubuntu/IdeaProjects/Realm/config/agentclients_fields.json", "r") as f:
    config = json.load(f)

# Check if includedFields items match fields items
included = {f["fieldPath"]: f for f in config.get("includedFields", [])}
all_fields = {f["fieldPath"]: f for f in config.get("fields", [])}

print("Fields in includedFields that are marked include=true in fields section:")
count = 0
for path in included:
    if path in all_fields and all_fields[path].get("include", False):
        count += 1

print(f"{count} out of {len(included)} includedFields are marked include=true in fields section")

# Check how many fields are marked include=true
include_true_count = sum(1 for f in config["fields"] if f.get("include", False))
print(f"\nTotal fields marked include=true in fields section: {include_true_count}")
print(f"Total in includedFields section: {len(included)}")

if include_true_count != len(included):
    print("\n⚠️  MISMATCH! The includedFields section is out of sync!")
    print("You need to edit ONLY the 'fields' section, not 'includedFields'")
else:
    print("\n✓ includedFields section matches fields with include=true")