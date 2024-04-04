# OSMZ project web app


# notest


2. enable external storage maniuplation

push data to external storage

```
 ~/Android/Sdk/platform-tools/adb push external_data /storage/emulated/0/web

```

https://developer.android.com/training/data-storage/manage-all-files

```
 ~/Android/Sdk/platform-tools/adb shell appops set --uid com.vsb.kru13.osmzhttpserver  MANAGE_EXTERNAL_STORAGE allow
```
