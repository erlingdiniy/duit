name: Bug Report
description: Report a crash, calculation error, or UI issue in duit.
title: "[Bug]: "
labels: ["bug"]
body:
  - type: markdown
    attributes:
      value: |
        > **Privacy reminder:** Never include real financial balances, bank/account details, PINs, backup passwords, or `.duitbackup` files in a public issue. Use synthetic sample amounts or blur sensitive values in screenshots.
  - type: input
    id: app_version
    attributes:
      label: App Version & Package
      description: Found under Settings -> About.
      placeholder: "e.g. 1.2.0-beta.1 (com.diniy.duit.beta)"
    validations:
      required: true
  - type: input
    id: device_os
    attributes:
      label: Device & Android Version
      placeholder: "e.g. Xiaomi 17T, Android 15 (SDK 35)"
    validations:
      required: true
  - type: dropdown
    id: theme
    attributes:
      label: Active Theme & Mode
      options:
        - Candy Bubble 2.0 · Light
        - Candy Bubble 2.0 · Dark
        - Pure Liquid Glass · Light (LOW / MID / HIGH)
        - Pure Liquid Glass · Dark (LOW / MID / HIGH)
        - Not sure
    validations:
      required: false
  - type: textarea
    id: summary
    attributes:
      label: What happened?
      description: Clear description of the bug.
    validations:
      required: true
  - type: textarea
    id: steps
    attributes:
      label: Steps to reproduce
      placeholder: |
        1. Open ...
        2. Tap ...
        3. Observe ...
    validations:
      required: true
  - type: textarea
    id: expected
    attributes:
      label: Expected vs. actual behavior
    validations:
      required: true
  - type: textarea
    id: logs
    attributes:
      label: Redacted logs or sample-data screenshots (optional)
      description: Strip any personal information before attaching.
