# `:foundation:preferences` and `:platform:secure-storage` design

- **Status:** Accepted 2026-09-04
- **Owner:** mayankmkh@gmail.com
- **Roles:** `foundation_runtime` for `:foundation:preferences`, `platform` for
  `:platform:secure-storage`
- **DataStore:** `androidx.datastore` 1.3.0-alpha10, kept on the alpha line because the web
  implementation only exists there. Where the DataStore prose docs and the DataStore source
  disagree, this document follows the source and says so.

This is the single source for how small persisted state is stored: key-value preferences, one
serialisable document per file, and secrets. The master document
([`helix-kmp-source-of-truth.md`](helix-kmp-source-of-truth.md) §17.4, §18.10 and §18.11) owns
the ownership split between Foundation, Platform and Capabilities; this document owns the
mechanics inside the two modules.

## 1. Requirements

- Preferences on Android, iOS, desktop JVM and wasmJs, observable as `Flow`.
- Typed keys. A caller that stores a `Boolean` reads a `Boolean` back or nothing; it never parses.
- One structured settings object per file when a Capability's state is a value, not a bag of keys.
- Secrets, today the session bearer token, never sit in plain preferences or in `localStorage`.
- A corrupt file costs the user that file's contents, never the app.
- Files live where each platform's conventions say application data lives.
- `:app:web` may be mounted inside a host page, so nothing may assume it owns `localStorage`.
- No product vocabulary. Key names, file names and document types belong to Capability
  implementations.

## 2. Ownership

| Concern | Owner |
| --- | --- |
| Opening stores, serialisation, corruption recovery, file naming | `:foundation:preferences` |
| Secrets on each platform | `:platform:secure-storage` |
| Application identifier, platform contexts | `:app:*` supplies a `PreferencesContext` and a `SecureStorageContext` |
| Which files exist, which keys they hold, what the values mean | Capability implementations |
| Session credential lifecycle | `:capability:identity-impl` over `SecretStore` |

Features never open a store. Capability implementations own one store per concern and expose typed
product state. Nothing outside `:foundation:preferences` imports `androidx.datastore`, and nothing
outside `:platform:secure-storage` imports Tink, Keychain or Keystore types.

`:platform:secure-storage` is one cohesive module with `expect`/`actual`, the form §7.17 allows,
rather than the `-api`/`-impl` pair §18.11 names as the default home. There is exactly one
implementation per platform and nothing selects between implementations at composition time, so
the physical split would buy nothing.

## 3. Public surface of `:foundation:preferences`

```kotlin
public expect class PreferencesContext

@JvmInline public value class PrefFile(public val name: String)

public class PrefKey<T> internal constructor(public val name: String, ...)
public fun booleanPrefKey(name: String): PrefKey<Boolean>
public fun intPrefKey(name: String): PrefKey<Int>
public fun longPrefKey(name: String): PrefKey<Long>
public fun floatPrefKey(name: String): PrefKey<Float>
public fun doublePrefKey(name: String): PrefKey<Double>
public fun stringPrefKey(name: String): PrefKey<String>
public fun stringSetPrefKey(name: String): PrefKey<Set<String>>

public interface PreferenceStore {
    public suspend fun <T> get(key: PrefKey<T>): T?
    public fun <T> observe(key: PrefKey<T>): Flow<T?>
    public suspend fun <T> set(key: PrefKey<T>, value: T)
    public suspend fun remove(key: PrefKey<*>)
    public suspend fun contains(key: PrefKey<*>): Boolean
    public suspend fun edit(block: PreferenceEditor.() -> Unit)
    public suspend fun clear()
}

public interface PreferenceEditor {
    public fun <T> set(key: PrefKey<T>, value: T)
    public fun remove(key: PrefKey<*>)
    public fun clear()
}

public interface DocumentStore<T> {
    public val data: Flow<T>
    public suspend fun update(transform: (T) -> T): T
}

public fun openPreferenceStore(context: PreferencesContext, file: PrefFile): PreferenceStore
public fun <T> openDocumentStore(
    context: PreferencesContext,
    file: PrefFile,
    serializer: KSerializer<T>,
    defaultValue: T,
): DocumentStore<T>

public fun inMemoryPreferenceStore(): PreferenceStore
public fun <T> inMemoryDocumentStore(defaultValue: T): DocumentStore<T>
```

`PrefKey<T>` wraps the matching `Preferences.Key<T>` and is only constructible through the seven
factories, so the set of storable types is exactly the set DataStore supports. `byteArrayPrefKey`
is deliberately absent: bytes in a preferences file are either a secret, which belongs in
`SecretStore`, or a document, which belongs in `DocumentStore`.

`edit` exists so that a Capability can change several keys atomically; a sign-out that clears three
keys must not be observable half-done. `observe` applies `distinctUntilChanged`, so a write to an
unrelated key in the same file does not wake observers of this one.

`PreferencesContext` has a different constructor per target. Android takes an `android.content.Context`.
iOS takes nothing; the sandbox already scopes the app. Desktop JVM and wasmJs take an
`applicationId: String`, because on those two targets nothing else scopes the app: the desktop
directory is named after it and every `localStorage` key is prefixed with it.

The desktop directory comes from `applicationDataDirectory(applicationId: String): File` in
`:foundation:runtime`'s JVM source set, so that this module and `:platform:secure-storage` resolve
the same directory without depending on each other (§4).

## 4. Storage per platform

| Target | Preferences and documents | Secrets |
| --- | --- | --- |
| Android | `filesDir/datastore/<name>.preferences_pb`, `<name>.json` | `noBackupFilesDir/datastore/<name>.secrets`, encrypted with `datastore-tink` under an Android Keystore master key |
| iOS | `Library/Application Support/datastore/<name>.…` | Keychain generic-password items, service = `<applicationId>.<name>`, account = key |
| Desktop JVM | macOS `~/Library/Application Support/<applicationId>/`, Windows `%APPDATA%\<applicationId>\`, Linux `${XDG_CONFIG_HOME:-~/.config}/<applicationId>/` | `<same directory>/<name>.secrets`, encrypted with `datastore-tink`; one application keyset in macOS Keychain, a Windows DPAPI-protected file or Linux libsecret, with an owner-only cleartext file fallback |
| wasmJs | `localStorage["<applicationId>.<name>.preferences_pb"]` and `localStorage["<applicationId>.<name>.json"]` via `WebLocalStorage` | Memory only |

Android uses the `datastore/` subdirectory that AndroidX's own `dataStoreFile()` uses, so a
future move to the umbrella artifacts finds the files where it expects them.

iOS moves from `Documents` to `Application Support`. Apple reserves `Documents` for user-visible
files and `Library/Preferences` for `NSUserDefaults` plists; application data the user never opens
belongs in `Application Support`, created with `create = true` because it does not exist on a
fresh install.

Desktop moves from `XDG_DATA_HOME` to `XDG_CONFIG_HOME` on Linux: preferences are configuration,
not data. Windows keeps roaming `%APPDATA%`; preferences are exactly the state that should follow
a user between machines. The directory name comes from the app, not from a constant in this
module, so a fork of the template gets its own directory the moment it changes one value in
`:app:shared`.

The `applicationId` prefix on web exists for the host-page requirement: two applications, or the
app and its host, sharing an origin must not read each other's `credentials` entry.

Every parent directory is created before the file is first touched, inside the path producer that
DataStore calls on its own scope, so opening a store during Koin graph creation does no filesystem
work. `OkioStorage` creates the file's own parent, but the app-level directory on desktop and the
`datastore/` directory on Android and iOS are the module's responsibility.

## 5. Corruption

Every store is opened with `ReplaceFileCorruptionHandler` that yields the empty preferences or the
document's `defaultValue`. Without it DataStore rethrows `CorruptionException` from every read and
write for the rest of the process, and a preferences file that was truncated by a crash mid-rename
would brick the app until reinstall. Losing the file is the cheaper outcome for anything this
module stores.

The handler runs once per corruption and the store carries on. There is no hook for callers to be
told: the module has no logger, and a Capability that must know can compare the observed value
with its own expectations. A production app will want a counter; that hook is deferred until the
template has a telemetry seam to attach it to (§12.1).

## 6. One store per file

DataStore allows one active instance per file per process and throws `IllegalStateException` from
the first read otherwise. `openPreferenceStore` and `openDocumentStore` register the file name in a
process-wide set and fail at open with a message that names the file, so two Capabilities that both
picked `PrefFile("settings")` fail at Koin resolution with a readable cause instead of on the first
read with DataStore's path-only message. Stores are process-lifetime singles in Koin, so the set is
never cleared.

The convention that avoids the collision in the first place: a Capability names its files after
itself, `PrefFile("identity.credentials")` rather than `PrefFile("credentials")`. The registry
enforces uniqueness; the convention makes uniqueness the default. The registry itself is
`OpenNameRegistry` in `:foundation:runtime`, a compare-and-set name set that both store modules
instantiate once; the modules differ only in the noun the failure message uses.

`openSecretStore` keeps its own instance of the same registry over store names. On Android and desktop the reason is
the same DataStore rule. On iOS two instances of one Keychain service would each hold a snapshot
that the other's writes never reach, so `observe` on one would miss `set` on the other.

## 7. Documents and serialisation

`DocumentStore<T>` is a typed DataStore over an `OkioSerializer<T>` that encodes with
kotlinx.serialization JSON. The `Json` instance is the module's own and is not configurable:

```kotlin
Json {
    ignoreUnknownKeys = true   // a downgrade reads a file written by a newer build
    coerceInputValues = true   // an unknown enum constant falls back to the field default
    encodeDefaults = false     // adding a field with a default costs nothing on disk
}
```

This differs deliberately from the strict `Json` `:foundation:network` uses. A wire contract is
owned by a server and should fail loudly when it drifts. A file on the user's device was written
by an older or newer build of this same app and must be read leniently or it is lost.

Why JSON rather than protobuf:

- Proto DataStore with generated `protobuf-lite` classes is the Android documentation's default,
  but `protobuf-lite` has no Kotlin/Native or wasmJs runtime, so it cannot back a common
  `DocumentStore`. Wire has a KMP runtime but adds a code generator and a schema language for
  files that hold a handful of fields.
- kotlinx.serialization's `ProtoBuf` format is multiplatform but still `@ExperimentalSerializationApi`,
  and it makes field numbering a correctness concern: without `@ProtoNumber` on every property,
  reordering a class silently corrupts existing files. JSON keys survive reordering.
- Preferences DataStore already stores its map as a protobuf message on JVM and Android
  (`.preferences_pb`) and as JSON on web; the module does not choose that format and does not
  expose it.
- Runtime cost is not a factor. A document is read once per process and cached by DataStore in
  memory; every later `data` emission is the in-memory value. Encoding runs on writes only.

The `KSerializer<T>` parameter is format-agnostic, so swapping JSON for CBOR or `ProtoBuf` later is
a change inside `openDocumentStore` and a one-time data migration, not an API change. Document
files use the `.json` suffix so the on-disk format is visible without opening the file.

DataStore alpha10 ships its own `WebSerializer<T>` for kotlinx JSON on web only. The module does not
use it: it hard-codes `Json.Default`, which has none of the leniency above, and it exists only in
the web source set.

## 8. Secrets

### 8.1 What DataStore offers

DataStore has no encryption in its core. Since 1.3.0-alpha07 there is
`androidx.datastore:datastore-tink`, one class, `AeadSerializer<T>`, which wraps a java.io
`Serializer<T>` and runs the bytes through a Tink `Aead` with the file name as associated data. A
decryption failure surfaces as `CorruptionException`, so the corruption handler from §5 turns a lost
key into an empty store rather than a crash. It is published for JVM and Android only; the
`commonMain` artifact is metadata and the class itself imports `java.io` and `java.security`.

`androidx.security:security-crypto`, the `EncryptedSharedPreferences` and `EncryptedFile` library
that used the same Tink pattern, is deprecated since 1.1.0-beta01 in favour of direct Keystore use.
`datastore-tink` is that pattern's successor for DataStore and is what this design uses on Android.

### 8.2 Public surface of `:platform:secure-storage`

```kotlin
public expect class SecureStorageContext

public interface SecretStore {
    public suspend fun get(key: String): String?
    public fun observe(key: String): Flow<String?>
    public suspend fun set(key: String, value: String)
    public suspend fun remove(key: String)
    public suspend fun clear()
}

public fun openSecretStore(context: SecureStorageContext, name: String): SecretStore
public fun inMemorySecretStore(): SecretStore
```

Values are strings. Tokens, refresh tokens and keys are strings; anything structured is a
Capability's document encoded to a string before it is handed here. `observe` exists because the
Identity implementation exposes the signed-in state as a `Flow`.

### 8.3 Per platform

**Android.** A typed DataStore of `Map<String, String>` in `noBackupFilesDir/datastore/<name>.secrets`.
The serializer is `AeadSerializer` around a kotlinx JSON `Serializer<Map<String, String>>`, with
`name` as associated data. The `Aead` comes from `tink-android`'s `AndroidKeysetManager`: an
`AES256_GCM` keyset stored in a `SharedPreferences` file and wrapped by a master key in the Android
Keystore under `android-keystore://<applicationId>.secure-storage`. The secrets file lives under
`noBackupFilesDir` because a Keystore key does not follow the user to a new device; a restored
ciphertext would only ever decrypt to a `CorruptionException`, so it is better not to restore it
at all. The app shell should exclude the keyset preferences file from backup for the same reason;
the module documents the file name in its KDoc. The shared assembly takes the serializer as a
producer and builds it on the first read or write, which DataStore runs on its own IO scope, so the
Keystore lookup and the keyset preferences read never happen on the thread that resolves the Koin
graph. `AeadConfig.register()` is called from that producer too; it is idempotent.

**iOS.** Keychain generic-password items with `kSecAttrService = "<applicationId>.<name>"` and
`kSecAttrAccount = key`, accessibility `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. The
Keychain is the platform's secret store; encrypting a file and then needing somewhere to keep the
key would reinvent it. `ThisDeviceOnly` matches the Android choice: secrets do not migrate through
backup. The Keychain has no change notifications, so `observe` is fed by the store's own writes
through an in-memory snapshot, the same `MemorySecretStore` that backs `inMemorySecretStore()`,
loaded on first access; the module is the only writer of its service. `clear()` skips that load,
because it does not need the current contents. Writes reach the Keychain before the snapshot, so
the snapshot never reports a value the device rejected. The blocking `SecItem*` calls run on
`Dispatchers.IO`, and a failing status becomes `SecretStoreException` with the `OSStatus` in the
message.

`SecretStoreException` is the failure contract on every platform, not only iOS: the shared
Android and desktop assembly wraps DataStore's `IOException` in it, with the original as the cause.
A consumer can therefore treat "secret storage unavailable" as "no value" by catching one type.
The Identity implementation does not catch it yet.

**Desktop JVM.** A typed DataStore of `Map<String, String>` in the application directory, encrypted
by the same `AeadSerializer` and `AES256_GCM` Tink keyset model as Android. One cleartext JSON keyset
is generated per application and held through a JNA-backed `KeysetVault`. The store name remains
the associated data, so moving ciphertext from one named store to another is treated as corruption.
The serializer producer performs vault access, keyset generation and `AeadConfig.register()` on
DataStore's IO scope at first use. Resolving the Koin graph does not touch a native store or file.

On macOS the vault is a Security.framework generic password with service
`<applicationId>.secure-storage` and account `keyset`. On Windows the keyset is protected for the
current user with DPAPI and `CRYPTPROTECT_UI_FORBIDDEN`, then the protected blob is written to
`<applicationDataDirectory>/secure-storage.keyset`. On Linux it is a libsecret password in the
default collection with attributes `application=<applicationId>` and `purpose=secure-storage`.
An unavailable native library or Linux Secret Service selects a cleartext
`<applicationDataDirectory>/secure-storage.keyset` fallback, created `0600` inside the existing
`0700` directory on POSIX, and prints one warning line with the reason. Once a native store has
answered, an error for its item becomes `SecretStoreException` and never silently falls back.

The OS store holds the Tink keyset itself rather than a key-encryption key. The single-key JSON is
only a few hundred bytes and fits every backend. Adding a key-encryption key would still require the
same OS custody while adding a second encrypted keyset file, another format and another failure
boundary without improving protection.

**wasmJs.** Memory only. A bearer token in `localStorage` is readable by any script on the origin,
including the host page the app is embedded in. The secret lives for the page's lifetime and the
user signs in again on reload. A web product that wants persistent sessions uses `httpOnly`
cookies so that the token never reaches the page at all, at which point `SecretStore` holds
nothing on web and the Identity implementation's web actual asks the server instead.

The Android and desktop actuals share the `Map<String, String>` serializer and the DataStore
assembly through a `jvmAndAndroidMain` source set; only the choice of wrapping the serializer in
`AeadSerializer` and the directory differ. `:foundation:preferences` uses the same source set for
the same reason: its two DataStore factories are shared and each platform contributes only
`dataStoreDirectory()`. Both source sets are declared through the default hierarchy template,
matching Android by platform type because `withAndroidTarget()` only knows the old Android target,
and not with `dependsOn`, which would switch the template off for iOS and web.

## 9. Identity

`CredentialStore` in `:capability:identity-impl` moves from `PreferenceStore` to `SecretStore`,
opened as `openSecretStore(context, "identity.credentials")`. Its tests use `inMemorySecretStore()`.
The Identity Koin module takes a `SecureStorageContext` from the app the same way it took a
`PreferencesContext`. The old plain `credentials.preferences_pb` file is not migrated: the sample
app has no users, and a token migration would be a Capability concern in a product, written as a
one-shot read from the old store followed by a delete.

The ownership table in [`network.md`](network.md) §2 changes its "Credential persistence" row to
`:platform:secure-storage`.

## 10. Composition

`:app:shared` supplies both contexts through the same `expect fun Scope.create…Context()` pattern
used for connectivity and the database. It also owns a single `ApplicationId` constant next to
`apiBaseUrl` in `config/Environment.kt`; the desktop and web `PreferencesContext` and every
`SecureStorageContext` take it from there. The Android application id and the iOS bundle
identifier stay what the platform build files say; `ApplicationId` here is the stable name the app
uses for its own storage and must never change once a build has shipped.

## 11. Testing

- `:foundation:preferences` common tests over `inMemoryPreferenceStore()` and
  `inMemoryDocumentStore()`: typed round trips for each key type, `edit` atomicity, `observe`
  quiet on unrelated keys, `clear`, document `update` returning the new value.
- `:foundation:preferences` JVM test that opens a real file store in a temporary directory, writes
  a value, overwrites the file with garbage, reopens it and asserts the value is gone and the store
  is writable again. A second JVM test opens the same logical file twice and asserts the failure
  names the file.
- `:platform:secure-storage` JVM tests: the `Map<String, String>` serializer round trip and the
  same store wrapped in `AeadSerializer` with plain Tink. A tampered ciphertext reads as an empty
  store. An in-memory keyset vault proves first use generates and stores one keyset, a new file with
  the same vault decrypts copied ciphertext, and a different store name rejects that ciphertext as
  corruption. The file fallback round trips and is `0600` on POSIX. A macOS-only smoke test writes,
  reads and deletes one unique generic password, skipping when the JVM cannot access the login
  Keychain. Two more tests pin the open-time guarantees: the encrypting serializer is built on the
  first read rather than when the store opens, and opening the same name twice fails with a message
  that names it.
- `:capability:identity-impl` tests move to `inMemorySecretStore()` unchanged in intent.
- Android Keystore and iOS Keychain actuals compile in `check` and are exercised by running the
  apps. Windows DPAPI and Linux libsecret compile on the JVM but need their target operating systems
  for native execution. The project does not run instrumented tests in the routine loop.

## 12. Decisions and rejected alternatives

- **Typed keys over a string-only store.** The old surface stored only strings and every Boolean
  or Int would have been a parse at the call site. DataStore's key types are the storable set.
- **Two modules rather than one.** A secret store that is a Keychain on one platform and an
  encrypted file on another is a platform service, not a preferences mechanism, and the master
  document places it under Platform. Keeping it out of Foundation also keeps Tink off the
  classpath of every module that only wants a Boolean.
- **No `Json` parameter on `openDocumentStore`.** One leniency rule for all persisted documents;
  a caller that wants different rules is asking for a different file format.
- **No caller-visible corruption callback.** See §5.
- **Registry over silent instance sharing.** Returning the existing store for a duplicate file
  name would merge two Capabilities' key namespaces without either noticing.
- **Stay on the alpha line.** Stable 1.2.1 has wasmJs artifacts but they throw; `WebLocalStorage`
  and `datastore-tink` exist only in 1.3.0. Revisit when 1.3.0 is stable.
- **No encryption of preferences.** Preferences hold no secrets by construction; encrypting them
  would spend Keystore operations on every read for nothing.
- **Open-name registries use compare-and-set.** Graph creation is single-threaded today, but the
  duplicate-open guarantee should not depend on that; the lock-free set costs nothing. One
  `OpenNameRegistry` class in `:foundation:runtime` serves both store modules.

### 12.1 Deferred improvements

Reviewed on 2026-09-04 and judged worth doing only when a consumer asks. Each is a small, local
change behind the existing surfaces.

- **Migrations.** `openPreferenceStore` and `openDocumentStore` do not expose DataStore's
  `migrations` parameter. A fork moving an existing Android app off `SharedPreferences` adds an
  optional parameter; the template has no legacy data to migrate.
- **Corruption visibility.** The reset in §5 is silent. When the template gains a telemetry seam,
  the open functions take an optional callback or report through it.
- **Web secrets across a reload.** Memory-only sign-out on refresh is deliberate (§8.3).
  `sessionStorage` would survive a refresh without pretending to be secure; whether that is wanted
  depends on the host page the app embeds in.

Judged not worth doing: an instrumented test of the real Android Keystore path (Robolectric cannot
emulate it and the JVM Tink test already covers the serializer), Tink key rotation (no caller), and
typed keys for `SecretStore` (secrets are strings by nature).

## 13. References

- DataStore overview and the one-instance rule: https://developer.android.com/topic/libraries/architecture/datastore
- DataStore release notes, `datastore-tink` in 1.3.0-alpha07, `WebLocalStorage` in 1.3.0-alpha08: https://developer.android.com/jetpack/androidx/releases/datastore
- `security-crypto` deprecation, 1.1.0-beta01: https://developer.android.com/jetpack/androidx/releases/security
- `AeadSerializer` source, `datastore-tink` 1.3.0-alpha10 sources jar, `commonMain/androidx/datastore/tink/AeadSerializer.kt`
- `OkioStorage`, `WebLocalStorage`, `WebSerializer` sources, `datastore-core-okio` 1.3.0-alpha10
- Apple File System Programming Guide, where files go in the app sandbox
- XDG Base Directory Specification
