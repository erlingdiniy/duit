name: Feature Request
description: Suggest an improvement or new capability for duit.
title: "[Feature]: "
labels: ["enhancement"]
body:
  - type: textarea
    id: problem
    attributes:
      label: Problem or use case
      description: What are you trying to do that duit. doesn't currently handle well?
    validations:
      required: true
  - type: textarea
    id: proposal
    attributes:
      label: Proposed solution
      description: How should this work from the user's perspective?
    validations:
      required: true
  - type: textarea
    id: privacy_notes
    attributes:
      label: Local-first & privacy notes (optional)
      description: Confirm how this fits duit.'s offline-first, no-account model.
