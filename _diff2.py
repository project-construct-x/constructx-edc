#!/usr/bin/env python3
import yaml
from copy import deepcopy

def deep_diff(stage_dict, default_dict):
    if not isinstance(stage_dict, dict) or not isinstance(default_dict, dict):
        return stage_dict if stage_dict != default_dict else None
    result = {}
    for key, stage_val in stage_dict.items():
        if key not in default_dict:
            result[key] = deepcopy(stage_val)
        else:
            default_val = default_dict[key]
            if isinstance(stage_val, dict) and isinstance(default_val, dict):
                diff = deep_diff(stage_val, default_val)
                if diff is not None and diff != {}:
                    result[key] = diff
            elif stage_val != default_val:
                result[key] = deepcopy(stage_val)
    return result

base_dir = "charts/tractusx-connector"
with open(f"{base_dir}/values.yaml", 'r') as f:
    default = yaml.safe_load(f)

for stage_name in ["values-provider.yaml", "values-consumer.yaml"]:
    path = f"{base_dir}/{stage_name}"
    with open(path, 'r') as f:
        stage = yaml.safe_load(f)
    diff = deep_diff(stage, default)
    print(f"=== {stage_name} ===")
    print(yaml.dump(diff, default_flow_style=False, sort_keys=False, allow_unicode=True, width=120))
    with open(path, 'w') as f:
        yaml.dump(diff, f, default_flow_style=False, sort_keys=False, allow_unicode=True, width=120)
