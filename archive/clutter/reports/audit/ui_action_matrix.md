# UI Action Matrix

| Screen | User Action | Entry Point | Reachable | Handler Status | Runtime Result |
|---|---|---|---|---|---|
| Chat | Open settings | Drawer > Settings | Yes | Implemented | Navigates to settings |
| Chat | Open profile/footer action | Drawer > User footer row | Yes | Implemented | Routes to settings/account entry point |
| Chat | Switch to WRITE mode | Floating tabs > WRITE | Yes | Implemented | Mode set + feedback snackbar + write-prefixed prompt flow |
| Chat | Switch to CALL mode | Floating tabs > CALL | Yes | Implemented | Mode set + feedback snackbar + call-prefixed prompt flow |
| Chat | Switch to IMAGE mode | Floating tabs > IMAGE | Yes | Implemented | Navigates to image-generation workflow |
| Provider selection | Open provider key portal | "Get {Provider} Key" link | Yes | Implemented | Opens provider key URL |
| Provider config | Save API key | Save button | Yes | Implemented | Saves key through repository without storing plaintext in ViewModel state |
| Navigation deep-link | `shadowai://provider/{providerId}` | External/internal deep link | Yes | Implemented | Invalid values fallback safely to `OPENAI` |

Notes:
- Reachability evaluated via static navigation/composable path review plus handler verification.
- Full emulator accessibility runtime checks still pending due current compile blockers.
