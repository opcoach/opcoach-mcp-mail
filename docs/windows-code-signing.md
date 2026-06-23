# Windows Code Signing

Official Windows releases must be Authenticode-signed before the ZIP archive and its `.sha256` checksum are created.

## Required Certificate

Use an OV or EV code-signing certificate exported as a `.pfx` file that includes the private key. EV certificates generally build Microsoft SmartScreen reputation faster, but the release workflow works with any valid Authenticode certificate accepted by `signtool.exe`.

Keep the `.pfx` file and its password outside the repository.

## GitHub Secrets

Configure these repository secrets before creating a release:

```text
WINDOWS_CODESIGN_PFX_BASE64
WINDOWS_CODESIGN_PASSWORD
```

Create the base64 value from macOS or Linux:

```bash
base64 -i opcoach-codesign.pfx | pbcopy
```

Paste the clipboard content into `WINDOWS_CODESIGN_PFX_BASE64`. Store the certificate password in `WINDOWS_CODESIGN_PASSWORD`.

## Release Behavior

The release workflow runs on `windows-latest`, locates Microsoft `signtool.exe`, signs `OPCoach MCP Mail.exe`, then packages the signed executable with the bundled Java runtime.

If either signing secret is missing or the signature command fails, the workflow fails and no official Windows ZIP is published.

Local developer builds created with `bin/build-release` are unsigned by default. They are useful for testing packaging, but they should not be redistributed to training users.
