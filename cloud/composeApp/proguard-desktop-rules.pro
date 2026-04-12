-ignorewarnings

# Desktop release: keep SQLite JDBC driver for explicit Class.forName + DriverManager
-keep class org.sqlite.JDBC { *; }
-keep class org.sqlite.** { *; }

