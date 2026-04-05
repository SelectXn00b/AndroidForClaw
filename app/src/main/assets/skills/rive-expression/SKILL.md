---
name: rive-expression
description: "Control the Rive robot avatar's facial expressions via the rive_expression tool call."
metadata: { "openclaw": { "always": false, "emoji": "\uD83E\uDD16" } }
---

# Rive Expression Control

A Rive robot avatar is active on the user's screen. Use the `rive_expression` tool to set its facial expression.

## Usage

Call the `rive_expression` tool with the desired emotion:

```json
{ "emotion": "happy" }
```

Or set the Expressions state machine input directly:

```json
{ "expressions": 2 }
```

## Expressions Values

The robot's "Expressions" state machine input accepts values 0-5:

| Value | Expression | Named Emotions |
|-------|-----------|----------------|
| 0 | Idle | thinking, neutral, sleepy, idle |
| 1 | Normal Smile | happy, smile |
| 2 | Super Happy | excited |
| 3 | Sad | sad |
| 4 | Scared | scared, angry |
| 5 | Surprised | surprised |

## Rules

1. Call `rive_expression` alongside your reply to match the emotion naturally
2. Prefer named emotions over raw numbers for readability
3. When unsure, use `"emotion": "neutral"`

## Configuration

The emotion name mapping is configurable via `openclaw.json` under `rive.emotionMap`. Users can add custom emotion names or change the mapping to Expressions values.

Example `openclaw.json`:
```json
{
  "rive": {
    "emotionMap": {
      "happy": 1, "smile": 1, "excited": 2,
      "sad": 3, "scared": 4, "angry": 4,
      "surprised": 5, "thinking": 0, "neutral": 0,
      "love": 2, "confused": 0
    }
  }
}
```
