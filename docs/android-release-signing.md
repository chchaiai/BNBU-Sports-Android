# Android release signing

Every release build is required to use an external signing key. The project never
stores a keystore or its passwords in Git.

## Create the upload key

Create the key once on a protected machine, then store the `.jks` file and its
passwords in the organisation's restricted secret vault. Use an upload key when
Google Play App Signing is enabled; Google protects the app-signing key and this
key is used only to upload new releases.

```powershell
New-Item -ItemType Directory -Force release
keytool -genkeypair -v -keystore release/bnbu-upload.jks -alias bnbu-upload -keyalg RSA -keysize 4096 -validity 10000
```

Use strong, unique passwords when prompted. Back up the `.jks`, alias, and both
passwords in access-controlled storage. Do not regenerate an upload key for an
existing Play app without first completing Google's upload-key reset process.

## Configure a protected local machine

Copy `keystore.properties.example` to `keystore.properties`, fill in all four
values, and keep both files outside source control. The configured store path can
be relative to the repository root or absolute.

## Configure CI

CI must provide all of the following protected environment variables:

- `BNBU_RELEASE_STORE_FILE` — path to the keystore restored by the CI job
- `BNBU_RELEASE_STORE_PASSWORD`
- `BNBU_RELEASE_KEY_ALIAS`
- `BNBU_RELEASE_KEY_PASSWORD`

Restore the binary keystore from the CI secret manager into a temporary job file,
set `BNBU_RELEASE_STORE_FILE` to that file, and delete it when the job finishes.
For example, a GitHub Actions secret may store the Base64-encoded `.jks` file:

```yaml
- name: Restore upload keystore
  shell: bash
  env:
    BNBU_UPLOAD_KEYSTORE_BASE64: ${{ secrets.BNBU_UPLOAD_KEYSTORE_BASE64 }}
  run: echo "$BNBU_UPLOAD_KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/bnbu-upload.jks"

- name: Build signed release
  env:
    BNBU_RELEASE_STORE_FILE: ${{ runner.temp }}/bnbu-upload.jks
    BNBU_RELEASE_STORE_PASSWORD: ${{ secrets.BNBU_RELEASE_STORE_PASSWORD }}
    BNBU_RELEASE_KEY_ALIAS: ${{ secrets.BNBU_RELEASE_KEY_ALIAS }}
    BNBU_RELEASE_KEY_PASSWORD: ${{ secrets.BNBU_RELEASE_KEY_PASSWORD }}
    BNBU_API_BASE_URL: https://api.example.edu.cn/api
  run: ./gradlew :app:bundleRelease
```

The `preReleaseBuild` task now fails before packaging if any signing value or the
keystore file is absent. A Play upload should use `:app:bundleRelease` to produce
an Android App Bundle.
