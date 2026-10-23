# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in $ANDROID_HOME/tools/proguard/proguard-android-optimize.txt
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── R8 實測踩到的坑（2026-10-23，minified debug 在 realme 上一啟動就崩）──
# Room 產生的資料庫實作是執行期用 Class.forName("<資料庫類名>_Impl") 載入的，
# R8 看不到這條引用，會把類別刪掉或改名。少了這條，AndroidX WorkManager
# （由廣告 SDK 帶進來）在啟動時就拋：
#   Failed to create an instance of androidx.work.impl.WorkDatabase
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
