# Extensions and trust

An extension is a small APK with no screens of its own. RouterHub finds it among the installed apps, loads
its code and lets it talk to your router. Because that code runs inside RouterHub and receives your router's
password, RouterHub checks who signed each extension before loading it.

| Shown as | Meaning | Loaded |
| --- | --- | --- |
| **Official** | Signed with the key of the [extensions repository](https://github.com/atasanbratan/routerhub-extensions), built from reviewed source by its Publish workflow. | Right away |
| **Development build** | Signed with the same debug key as a debug build of the app, such as extensions you built yourself. | Right away, debug builds only |
| **Untrusted** | Anyone else. | Only after you tap **Trust**, and again after every update |

The official signing certificate has this SHA-256 fingerprint:

`7F:3B:12:B9:54:80:3F:03:AB:99:96:56:3F:83:4C:7E:D2:37:AD:55:EB:E6:31:A2:CC:4C:26:EE:FB:EF:0E:39`

## API versions

Each extension declares the version of the RouterHub extension API it needs, shown as **API N** in the
[extension list](/extensions/). If your app is older than that, **Browse → Extensions** shows
"Built for router-api vN; this app supports …" instead of loading it; [update the app](/download) and it loads.
