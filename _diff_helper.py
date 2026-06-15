#!/usr/bin/env python3
"""Deep-compare stage-specific values files against values.yaml and keep only differences."""

import yaml
import sys
from copy import deepcopy

def load_yaml(path):
    with open(path, 'r') as f:
        return yaml.safe_load(f)

def deep_diff(stage_dict, default_dict):
    """
    Recursively remove keys from stage_dict that have the same value as in default_dict.
    Returns the stripped dict (only contains keys that differ from default).
    """
    if not isinstance(stage_dict, dict) or not isinstance(default_dict, dict):
        return stage_dict

    result = {}
    for key, stage_val in stage_dict.items():
        if key not in default_dict:
            # Key only exists in stage - keep it
            result[key] = deepcopy(stage_val)
        else:
            default_val = default_dict[key]
            if isinstance(stage_val, dict) and isinstance(default_val, dict):
                # Recurse into nested dicts
                diff = deep_diff(stage_val, default_val)
                if diff:  # Only keep if there are differences
                    result[key] = diff
            elif stage_val != default_val:
                # Value differs - keep it
                result[key] = deepcopy(stage_val)
            # else: same value - omit
    return result

def main():
    base_dir = "charts/tractusx-connector"
    default_path = f"{base_dir}/values.yaml"
    stage_files = [
        f"{base_dir}/values-provider.yaml",
        f"{base_dir}/values-consumer.yaml",
    ]

    default = load_yaml(default_path)

    for stage_path in stage_files:
        stage = load_yaml(stage_path)
        diff = deep_diff(stage, default)

        print(f"\n=== Differences for {stage_path} ===")
        print(yaml.dump(diff, default_flow_style=False, sort_keys=False, allow_unicode=True, width=120))

        # Write back to the same file
        with open(stage_path, 'w') as f:
            yaml.dump(diff, f, default_flow_style=False, sort_keys=False, allow_unicode=True, width=120)
        print(f"Rewrote {stage_path} with diff-only content")

if __name__ == "__main__":
    main()
